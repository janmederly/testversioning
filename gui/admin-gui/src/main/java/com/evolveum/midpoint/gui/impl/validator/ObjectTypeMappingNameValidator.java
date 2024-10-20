/*
 * Copyright (C) 2010-2024 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.validator;

import com.evolveum.midpoint.prism.PrismContainerDefinition;
import com.evolveum.midpoint.prism.PrismContext;

import org.apache.commons.lang3.StringUtils;
import org.apache.wicket.model.IModel;
import org.apache.wicket.validation.IValidatable;
import org.jetbrains.annotations.Nullable;

import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismContainerValueWrapper;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismPropertyWrapper;
import com.evolveum.midpoint.gui.api.util.LocalizationUtil;
import com.evolveum.midpoint.gui.impl.util.GuiDisplayNameUtil;
import com.evolveum.midpoint.schema.processor.*;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

public class ObjectTypeMappingNameValidator extends MappingNameValidator {

    private final PageBase pageBase;

    public ObjectTypeMappingNameValidator(IModel<PrismPropertyWrapper<String>> itemModel, PageBase pageBase) {
        super(itemModel);
        this.pageBase = pageBase;
    }

    @Override
    public void validate(IValidatable<String> validatable) {
        String value = validatable.getValue();
        if (StringUtils.isEmpty(value)) {
            return;
        }

        PrismPropertyWrapper<String> item = getItemModel().getObject();
        if (item == null) {
            return;
        }

        PrismContainerValueWrapper<ResourceObjectTypeDefinitionType> objectType =
                item.getParentContainerValue(ResourceObjectTypeDefinitionType.class);

        if (objectType == null || objectType.getRealValue() == null) {
            return;
        }

        ResourceObjectTypeDefinitionType objectTypeBean = objectType.getRealValue();
        if (alreadyExistMapping(
                objectType,
                LocalizationUtil.translate(
                        "ObjectTypeMappingNameValidator.sameValue",
                        new Object[] { value, GuiDisplayNameUtil.getDisplayName(objectTypeBean) }),
                value,
                validatable)) {
            return;
        }

        ResourceSchema schema = getResourceSchema(pageBase);

        if (schema == null) {
            return;
        }

        @Nullable ResourceObjectTypeDefinition objectTypeDef = schema.getObjectTypeDefinition(ResourceObjectTypeIdentification.of(objectTypeBean));

        if (objectTypeDef == null) {
            return;
        }

        for (ShadowAssociationDefinition associationDefinition : objectTypeDef.getAssociationDefinitions()) {
            ShadowAssociationTypeDefinitionType bean = associationDefinition.getModernAssociationTypeDefinitionBean().clone();

            String associationName = GuiDisplayNameUtil.getDisplayName(bean, true);
            if (associationName == null) {
                associationName = LocalizationUtil.translate(
                        "ObjectTypeMappingNameValidator.sameValueInAssociationType.associationName",
                        new Object[] {GuiDisplayNameUtil.getDisplayName(objectTypeBean)});
            }

            String errorMessage = LocalizationUtil.translate(
                    "ObjectTypeMappingNameValidator.sameValue",
                    new Object[] {value, associationName});

            PrismContainerDefinition<ShadowAssociationTypeDefinitionType> def =
                    PrismContext.get().getSchemaRegistry().findContainerDefinitionByCompileTimeClass(ShadowAssociationTypeDefinitionType.class);
            alreadyExistMapping(bean, def, errorMessage, value, validatable, pageBase);
        }
    }
}
