/*
 * Copyright (c) 2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.page.admin.reports.component;

import javax.xml.namespace.QName;

import com.evolveum.midpoint.gui.api.prism.wrapper.PrismPropertyWrapper;

import com.evolveum.midpoint.gui.api.util.LocalizationUtil;
import com.evolveum.midpoint.gui.impl.validator.ParseAxiomQueryValidator;
import com.evolveum.midpoint.web.component.prism.InputPanel;
import com.evolveum.midpoint.web.page.admin.configuration.component.EmptyOnBlurAjaxFormUpdatingBehaviour;

import org.apache.commons.lang3.StringUtils;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.FormComponent;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.IModel;

import com.evolveum.midpoint.gui.api.component.BasePanel;
import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismContainerValueWrapper;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.impl.factory.panel.searchfilter.SearchFilterTypeForQueryModel;
import com.evolveum.midpoint.gui.impl.factory.panel.searchfilter.SearchFilterTypeForXmlModel;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.AjaxButton;
import com.evolveum.midpoint.web.component.input.TextPanel;
import com.evolveum.midpoint.web.component.search.BasicSearchFilterModel;
import com.evolveum.midpoint.web.component.search.SearchPropertiesConfigPanel;
import com.evolveum.midpoint.web.component.util.VisibleBehaviour;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectCollectionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SearchBoxModeType;
import com.evolveum.prism.xml.ns._public.query_3.SearchFilterType;

/**
 * @author honchar
 */
public class SearchFilterConfigurationPanel<O extends ObjectType> extends InputPanel {

    private enum FieldType {
        XML, QUERY
    }

    private static final long serialVersionUID = 1L;

    private static final Trace LOGGER = TraceManager.getTrace(SearchFilterConfigurationPanel.class);

    private static final String ID_CONTAINER = "container";
    private static final String ID_ACE_EDITOR_FIELD = "aceEditorField";
    private static final String ID_TEXT_FIELD = "textField";
    private static final String ID_CONFIGURE_BUTTON = "configureButton";
    private static final String ID_FIELD_TYPE_BUTTON = "fieldTypeButton";
    private static final String ID_FIELD_TYPE_BUTTON_LABEL = "fieldTypeButtonLabel";

    private LoadableModel<Class<O>> filterTypeModel;

    private PrismContainerValueWrapper<ObjectCollectionType> containerWrapper;

    private FieldType fieldType;

    private final IModel<PrismPropertyWrapper<SearchFilterType>> itemModel;
    private final IModel<SearchFilterType> beanModel;

    public SearchFilterConfigurationPanel(String id, IModel<PrismPropertyWrapper<SearchFilterType>> itemModel,
            IModel<SearchFilterType> model, PrismContainerValueWrapper<ObjectCollectionType> containerWrapper) {
        super(id);
        this.beanModel = model;
        this.itemModel = itemModel;
        this.containerWrapper = containerWrapper;
        // todo why resolving model in constructor?
        if (model.getObject() == null || StringUtils.isNotBlank(model.getObject().getText())) {
            fieldType = FieldType.QUERY;
        } else {
            fieldType = FieldType.XML;
        }
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();
        initFilterTypeModel();
        initLayout();
    }

    private void initFilterTypeModel() {
        filterTypeModel = new LoadableModel<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public Class<O> load() {
                QName filterType = null;
                if (containerWrapper != null) {
                    ObjectCollectionType collectionObj = containerWrapper.getRealValue();
                    filterType = collectionObj.getType() != null ? collectionObj.getType() : ObjectType.COMPLEX_TYPE;
                } else if (itemModel != null && itemModel.getObject() != null ) {
                    var item = itemModel.getObject().getItem();
                    if (item != null && item.getValue() != null) {
                        var schemaContext = item.getValue().getSchemaContext();
                        if (schemaContext != null && schemaContext.getItemDefinition() != null) {
                            filterType = schemaContext.getItemDefinition().getTypeName();
                        }
                    }
                }
                return (Class<O>) WebComponentUtil.qnameToClass(
                        filterType == null ? ObjectType.COMPLEX_TYPE : filterType);
            }
        };
    }

    private void initLayout() {
        WebMarkupContainer container = new WebMarkupContainer(ID_CONTAINER);
        container.setOutputMarkupId(true);
        add(container);

        AceEditorPanel aceEditorField = new AceEditorPanel(ID_ACE_EDITOR_FIELD, null, new SearchFilterTypeForXmlModel(getModel(), getPageBase()), 10);
        aceEditorField.add(new VisibleBehaviour(() -> FieldType.XML.equals(fieldType)));
        container.add(aceEditorField);

        SearchFilterTypeForQueryModel queryModel = createQueryModel(getModel(), filterTypeModel, containerWrapper != null);
        TextPanel textPanel = new TextPanel(ID_TEXT_FIELD, queryModel);
        textPanel.add(new VisibleBehaviour(() -> FieldType.QUERY.equals(fieldType)));
        if (addEmptyBlumBehaviourToTextField()) {
            textPanel.getBaseFormComponent().add(new EmptyOnBlurAjaxFormUpdatingBehaviour());
        }
        textPanel.getBaseFormComponent().add(new ParseAxiomQueryValidator(queryModel));
        container.add(textPanel);

        AjaxButton searchConfigurationButton = new AjaxButton(ID_CONFIGURE_BUTTON) {
            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                searchConfigurationPerformed(target);
            }
        };
        searchConfigurationButton.add(new VisibleBehaviour(() -> containerWrapper != null));
        add(searchConfigurationButton);

        IModel<String> labelModel = () -> {
            String type;
            if (FieldType.XML.equals(fieldType)) {
                type = LocalizationUtil.translateEnum(SearchBoxModeType.AXIOM_QUERY);
            } else {
                type = LocalizationUtil.translate("SearchFilterConfigurationPanel.fieldType.xml");
            }
            return LocalizationUtil.translate("SearchFilterConfigurationPanel.fieldType.switchTo", new Object[]{type});
        };

        Label buttonLabel = new Label(ID_FIELD_TYPE_BUTTON_LABEL, labelModel);
        buttonLabel.setOutputMarkupId(true);

        AjaxLink fieldTypeButton = new AjaxLink<String>(ID_FIELD_TYPE_BUTTON) {
            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                fieldType = FieldType.QUERY.equals(fieldType) ? FieldType.XML : FieldType.QUERY;

                target.add(SearchFilterConfigurationPanel.this.get(ID_CONTAINER));
                target.add(getPageBase().getFeedbackPanel());
                target.add(buttonLabel);
                target.add(this);
            }
        };
        fieldTypeButton.add(buttonLabel);
        fieldTypeButton.add(new VisibleBehaviour(() -> FieldType.XML.equals(fieldType)));
        add(fieldTypeButton);
    }

    protected boolean addEmptyBlumBehaviourToTextField() {
        return false;
    }

    protected SearchFilterTypeForQueryModel createQueryModel(IModel<SearchFilterType> model, LoadableModel<Class<O>> filterTypeModel, boolean useParsing) {
        return new SearchFilterTypeForQueryModel<>(model, getPageBase(), filterTypeModel, useParsing);
    }

    private void searchConfigurationPerformed(AjaxRequestTarget target) {
        filterTypeModel.reset();
        SearchPropertiesConfigPanel<O> configPanel = new SearchPropertiesConfigPanel<>(getPageBase().getMainPopupBodyId(),
                new BasicSearchFilterModel<>(getModel(), filterTypeModel.getObject(), getPageBase()), filterTypeModel) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void filterConfiguredPerformed(ObjectFilter configuredFilter, AjaxRequestTarget target) {
                getPageBase().hideMainPopup(target);

                try {
                    if (configuredFilter == null) {
                        return;
                    }
                    SearchFilterConfigurationPanel.this.getModel().setObject(SearchFilterConfigurationPanel.this.getPageBase().getQueryConverter().createSearchFilterType(configuredFilter));
                    target.add(SearchFilterConfigurationPanel.this.get(ID_CONTAINER));
                    target.add(getPageBase().getFeedbackPanel());
                } catch (SchemaException e) {
                    LoggingUtils.logUnexpectedException(LOGGER, "Cannot serialize filter", e);
                }
            }
        };
        getPageBase().showMainPopup(configPanel, target);
    }

    public PrismPropertyWrapper<SearchFilterType> getItemModelObject() {
        return itemModel.getObject();
    }

    private IModel<SearchFilterType> getModel() {
        return beanModel;
    }

    @Override
    public FormComponent getBaseFormComponent() {
        if (fieldType == FieldType.XML) {
            AceEditorPanel panel = (AceEditorPanel) get(createComponentPath(ID_CONTAINER, ID_ACE_EDITOR_FIELD));
            if (panel != null) {
                return panel.getEditor();
            }
        } else if (fieldType == FieldType.QUERY) {
            TextPanel panel = (TextPanel) get(createComponentPath(ID_CONTAINER, ID_TEXT_FIELD));
            if (panel != null) {
                return panel.getBaseFormComponent();
            }
        }

        return null;
    }
}
