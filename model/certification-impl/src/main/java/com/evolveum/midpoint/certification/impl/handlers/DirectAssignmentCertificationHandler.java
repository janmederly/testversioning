/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.certification.impl.handlers;

import com.evolveum.midpoint.certification.api.AccessCertificationApiConstants;
import com.evolveum.midpoint.schema.expression.VariablesMap;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.schema.constants.ExpressionConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ActivationUtil;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.PolicyViolationException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Component
public class DirectAssignmentCertificationHandler extends BaseCertificationHandler {

    public static final String URI = AccessCertificationApiConstants.DIRECT_ASSIGNMENT_HANDLER_URI;

    @PostConstruct
    public void init() {
        certificationManager.registerHandler(URI, this);
    }

    @Override
    public QName getDefaultObjectType() {
        return UserType.COMPLEX_TYPE;
    }

    // converts assignments to cases
    @Override
    public <F extends AssignmentHolderType> Collection<? extends AccessCertificationCaseType> createCasesForObject(
            PrismObject<F> objectPrism, AccessCertificationCampaignType campaign, Task task, OperationResult parentResult)
            throws ExpressionEvaluationException, ObjectNotFoundException, SchemaException, CommunicationException,
            ConfigurationException, SecurityViolationException {

        // TODO what if AccessCertificationObjectBasedScopeType?
        AccessCertificationAssignmentReviewScopeType assignmentScope =
                campaign.getScopeDefinition() instanceof AccessCertificationAssignmentReviewScopeType matching ? matching : null;

        F focus = objectPrism.asObjectable();

        List<AccessCertificationCaseType> caseList = new ArrayList<>();
        if (isIncludeAssignments(assignmentScope)) {
            for (AssignmentType assignment : focus.getAssignment()) {
                processAssignment(assignment, false, assignmentScope, campaign, focus, caseList, task, parentResult);
            }
        }
        if (focus instanceof AbstractRoleType && isIncludeInducements(assignmentScope)) {
            for (AssignmentType assignment : ((AbstractRoleType) focus).getInducement()) {
                processAssignment(assignment, true, assignmentScope, campaign, focus, caseList, task, parentResult);
            }
        }
        return caseList;
    }

    private void processAssignment(
            AssignmentType assignment,
            boolean isInducement,
            AccessCertificationAssignmentReviewScopeType scope,
            AccessCertificationCampaignType campaign,
            ObjectType object,
            List<AccessCertificationCaseType> caseList,
            Task task,
            OperationResult result)
            throws ExpressionEvaluationException, ObjectNotFoundException, SchemaException, CommunicationException,
            ConfigurationException, SecurityViolationException {
        AccessCertificationAssignmentCaseType assignmentCase = new AccessCertificationAssignmentCaseType();
        assignmentCase.setAssignment(assignment.clone());
        assignmentCase.setIsInducement(isInducement);
        assignmentCase.setObjectRef(ObjectTypeUtil.createObjectRef(object));
        assignmentCase.setTenantRef(assignment.getTenantRef());
        assignmentCase.setOrgRef(assignment.getOrgRef());
        assignmentCase.setActivation(assignment.getActivation());
        boolean valid;
        if (assignment.getTargetRef() != null) {
            assignmentCase.setTargetRef(assignment.getTargetRef());
            if (RoleType.COMPLEX_TYPE.equals(assignment.getTargetRef().getType())) {
                valid = isIncludeRoles(scope);
            } else if (OrgType.COMPLEX_TYPE.equals(assignment.getTargetRef().getType())) {
                valid = isIncludeOrgs(scope);
            } else if (ServiceType.COMPLEX_TYPE.equals(assignment.getTargetRef().getType())) {
                valid = isIncludeServices(scope);
            } else if (UserType.COMPLEX_TYPE.equals(assignment.getTargetRef().getType())) {
                valid = isIncludeUsers(scope);
            } else if (PolicyType.COMPLEX_TYPE.equals(assignment.getTargetRef().getType())) {
                valid = isIncludePolicies(scope);
            } else if (ArchetypeType.COMPLEX_TYPE.equals(assignment.getTargetRef().getType())) {
                // archetype assignment management is not fully supported for now, therefore ignoring
                valid = false;
            } else {
                throw new IllegalStateException("Unexpected targetRef type: " + assignment.getTargetRef().getType() + " in " + ObjectTypeUtil.toShortString(assignment));
            }
            valid = valid && relationMatches(assignment.getTargetRef().getRelation(), scope.getRelation());
        } else if (assignment.getConstruction() != null) {
            assignmentCase.setTargetRef(assignment.getConstruction().getResourceRef());
            valid = isIncludeResources(scope);
        } else {
            valid = false;      // neither role/org/service nor resource assignment; ignored for now
        }
        valid = valid && (!isEnabledItemsOnly(scope) || ActivationUtil.isAdministrativeEnabledOrNull(assignment.getActivation()));
        valid = valid && itemSelectionExpressionAccepts(assignment, isInducement, object, campaign, task, result);
        if (valid) {
            caseList.add(assignmentCase);
        }
    }

    private boolean relationMatches(QName assignmentRelation, List<QName> scopeRelations) {
        return (!scopeRelations.isEmpty() ? scopeRelations : Collections.singletonList(prismContext.getDefaultRelation()))
                .stream().anyMatch(r -> prismContext.relationMatches(r, assignmentRelation));
    }

    @SuppressWarnings("unused")
    private boolean itemSelectionExpressionAccepts(AssignmentType assignment, boolean isInducement, ObjectType object,
            AccessCertificationCampaignType campaign, Task task, OperationResult result) throws ExpressionEvaluationException,
            ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException, SecurityViolationException {
        AccessCertificationObjectBasedScopeType objectBasedScope =
                campaign.getScopeDefinition() instanceof AccessCertificationObjectBasedScopeType scope ? scope : null;
        ExpressionType selectionExpression = objectBasedScope != null ? objectBasedScope.getItemSelectionExpression() : null;
        if (selectionExpression == null) {
            return true; // no expression, no rejections
        }
        VariablesMap variables = new VariablesMap();
        variables.put(ExpressionConstants.VAR_ASSIGNMENT, assignment, AssignmentType.class);
        if (object instanceof FocusType) {
            variables.putObject(ExpressionConstants.VAR_FOCUS, (FocusType)object, FocusType.class);
        }
        if (object instanceof UserType) {
            variables.putObject(ExpressionConstants.VAR_USER, (UserType)object, UserType.class);
        }
        return expressionHelper.evaluateBooleanExpression(
                selectionExpression, variables,
                "item selection for assignment " + ObjectTypeUtil.toShortString(assignment), task, result);
    }

    private boolean isIncludeAssignments(AccessCertificationAssignmentReviewScopeType scope) {
        return scope == null || !Boolean.FALSE.equals(scope.isIncludeAssignments());
    }

    private boolean isIncludeInducements(AccessCertificationAssignmentReviewScopeType scope) {
        return scope == null || !Boolean.FALSE.equals(scope.isIncludeInducements());
    }

    private boolean isIncludeResources(AccessCertificationAssignmentReviewScopeType scope) {
        return scope == null || !Boolean.FALSE.equals(scope.isIncludeResources());
    }

    private boolean isIncludeRoles(AccessCertificationAssignmentReviewScopeType scope) {
        return scope == null || !Boolean.FALSE.equals(scope.isIncludeRoles());
    }

    private boolean isIncludeOrgs(AccessCertificationAssignmentReviewScopeType scope) {
        return scope == null || !Boolean.FALSE.equals(scope.isIncludeOrgs());
    }

    private boolean isIncludeServices(AccessCertificationAssignmentReviewScopeType scope) {
        return scope == null || !Boolean.FALSE.equals(scope.isIncludeServices());
    }

    private boolean isIncludeUsers(AccessCertificationAssignmentReviewScopeType scope) {
        return scope == null || !Boolean.FALSE.equals(scope.isIncludeUsers());
    }

    private boolean isIncludePolicies(AccessCertificationAssignmentReviewScopeType scope) {
        return scope == null || !Boolean.FALSE.equals(scope.isIncludePolicies());
    }

    private boolean isEnabledItemsOnly(AccessCertificationAssignmentReviewScopeType scope) {
        return scope == null || !Boolean.FALSE.equals(scope.isEnabledItemsOnly());
    }

    @Override
    public void doRevoke(
            AccessCertificationCaseType aCase, AccessCertificationCampaignType campaign, Task task, OperationResult result)
            throws CommunicationException, ObjectAlreadyExistsException, ExpressionEvaluationException, PolicyViolationException,
            SchemaException, SecurityViolationException, ConfigurationException, ObjectNotFoundException {
        revokeAssignmentCase(
                MiscUtil.castSafely(aCase, AccessCertificationAssignmentCaseType.class),
                campaign, result, task);
    }
}
