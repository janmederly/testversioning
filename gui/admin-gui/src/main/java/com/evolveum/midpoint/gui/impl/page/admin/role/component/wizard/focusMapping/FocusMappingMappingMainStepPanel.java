/*
 * Copyright (C) 2023 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.page.admin.role.component.wizard.focusMapping;

import com.evolveum.midpoint.gui.impl.page.admin.assignmentholder.AssignmentHolderDetailsModel;
import com.evolveum.midpoint.gui.impl.page.admin.resource.component.wizard.schemaHandling.objectType.attributeMapping.OutboundMappingMainConfigurationStepPanel;
import com.evolveum.midpoint.web.application.PanelDisplay;
import com.evolveum.midpoint.web.application.PanelInstance;
import com.evolveum.midpoint.web.application.PanelType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AbstractRoleType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OperationTypeType;
import org.apache.wicket.model.IModel;

@PanelType(name = "arw-focusMapping-mapping-main")
@PanelInstance(identifier = "arw-focusMapping-mapping-main",
        applicableForType = AbstractRoleType.class,
        applicableForOperation = OperationTypeType.WIZARD,
        display = @PanelDisplay(label = "PageRole.wizard.step.focusMapping.mapping.main", icon = "fa fa-circle"),
        containerPath = "empty")
public class FocusMappingMappingMainStepPanel<AHD extends AssignmentHolderDetailsModel> extends OutboundMappingMainConfigurationStepPanel<AHD> {

    private static final String PANEL_TYPE = "arw-focusMapping-mapping-main";

    public FocusMappingMappingMainStepPanel(AHD model, IModel newValueModel) {
        super(model, newValueModel);
    }

    @Override
    protected String getPanelType() {
        return PANEL_TYPE;
    }
}
