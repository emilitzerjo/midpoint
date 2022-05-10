/*
 * Copyright (c) 2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.page.admin.reports.component;

import javax.xml.namespace.QName;

import org.apache.commons.lang3.StringUtils;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.model.IModel;

import com.evolveum.midpoint.gui.api.component.BasePanel;
import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismContainerValueWrapper;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.impl.factory.panel.SearchFilterTypeForQueryModel;
import com.evolveum.midpoint.gui.impl.factory.panel.SearchFilterTypeForXmlModel;
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
public class SearchFilterConfigurationPanel<O extends ObjectType> extends BasePanel<SearchFilterType> {

    private enum FieldType {
        XML, QUERY
    }

    private static final long serialVersionUID = 1L;

    private static final Trace LOGGER = TraceManager.getTrace(SearchFilterConfigurationPanel.class);

    private static final String ID_ACE_EDITOR_CONTAINER = "aceEditorContainer";
    private static final String ID_ACE_EDITOR_FIELD = "aceEditorField";
    private static final String ID_TEXT_FIELD_CONTAINER = "textFieldContainer";
    private static final String ID_TEXT_FIELD = "textField";
    private static final String ID_CONFIGURE_BUTTON = "configureButton";
    private static final String ID_FIELD_TYPE_BUTTON = "fieldTypeButton";
    private static final String ID_FIELD_TYPE_BUTTON_LABEL = "fieldTypeButtonLabel";

    private LoadableModel<Class<O>> filterTypeModel;
    private PrismContainerValueWrapper<ObjectCollectionType> containerWrapper;
    private FieldType fieldType;

    public SearchFilterConfigurationPanel(String id, IModel<SearchFilterType> model, PrismContainerValueWrapper<ObjectCollectionType> containerWrapper) {
        super(id, model);
        this.containerWrapper = containerWrapper;
        // todo why resolving model in constructor?
        if (model.getObject() != null && StringUtils.isNotBlank(model.getObject().getText())) {
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
                }
                return (Class<O>) WebComponentUtil.qnameToClass(SearchFilterConfigurationPanel.this.getPageBase().getPrismContext(),
                        filterType == null ? ObjectType.COMPLEX_TYPE : filterType);
            }
        };
    }

    private void initLayout() {
        WebMarkupContainer aceEditorContainer = new WebMarkupContainer(ID_ACE_EDITOR_CONTAINER);
        aceEditorContainer.setOutputMarkupId(true);
        // todo why this is not handled via VisibleBehaviour?
        aceEditorContainer.add(AttributeAppender.append("style", (IModel<?>) () -> {
            if (FieldType.XML.equals(fieldType)) {
                return "display: block;";
            }
            return "display: none;";
        }));
        add(aceEditorContainer);

        AceEditorPanel aceEditorField = new AceEditorPanel(ID_ACE_EDITOR_FIELD, null, new SearchFilterTypeForXmlModel(getModel(), getPageBase()), 10);
        aceEditorField.setOutputMarkupId(true);
        aceEditorContainer.add(aceEditorField);

        WebMarkupContainer textFieldContainer = new WebMarkupContainer(ID_TEXT_FIELD_CONTAINER);
        textFieldContainer.setOutputMarkupId(true);
        // todo why this is not handled via VisibleBehaviour?
        textFieldContainer.add(AttributeAppender.append("style", (IModel<?>) () -> {
            if (FieldType.QUERY.equals(fieldType)) {
                return "display: block;";
            }
            return "display: none;";
        }));
        add(textFieldContainer);

        TextPanel textPanel = new TextPanel(ID_TEXT_FIELD, new SearchFilterTypeForQueryModel<O>(getModel(), getPageBase(), filterTypeModel, containerWrapper != null));
        textPanel.setOutputMarkupId(true);
        textFieldContainer.add(textPanel);

        AjaxButton searchConfigurationButton = new AjaxButton(ID_CONFIGURE_BUTTON) {
            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                searchConfigurationPerformed(target);
            }
        };
        searchConfigurationButton.setOutputMarkupId(true);
        searchConfigurationButton.add(new VisibleBehaviour(() -> true || containerWrapper != null));
        add(searchConfigurationButton);

        IModel<String> labelModel = (IModel) () -> {
            if (FieldType.XML.equals(fieldType)) {
                return getString(SearchBoxModeType.AXIOM_QUERY);
            }
            return getString("SearchFilterConfigurationPanel.fieldType.xml");
        };

        Label buttonLabel = new Label(ID_FIELD_TYPE_BUTTON_LABEL, labelModel);
        buttonLabel.setOutputMarkupId(true);

        AjaxLink fieldTypeButton = new AjaxLink<String>(ID_FIELD_TYPE_BUTTON) {
            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                if (FieldType.QUERY.equals(fieldType)) {
                    fieldType = FieldType.XML;
                } else {
                    fieldType = FieldType.QUERY;
                }
                target.add(getAceEditorContainer());
                target.add(getTextFieldContainer());
                target.add(getPageBase().getFeedbackPanel());
                target.add(buttonLabel);
            }
        };
        fieldTypeButton.setOutputMarkupId(true);
        add(fieldTypeButton);
        fieldTypeButton.add(buttonLabel);
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
                    target.add(getAceEditorContainer());
                    target.add(getTextFieldContainer());
                    target.add(getPageBase().getFeedbackPanel());
                } catch (SchemaException e) {
                    LoggingUtils.logUnexpectedException(LOGGER, "Cannot serialize filter", e);
                }
            }
        };
        getPageBase().showMainPopup(configPanel, target);
    }

    private Component getAceEditorContainer() {
        return get(ID_ACE_EDITOR_CONTAINER);
    }

    private Component getTextFieldContainer() {
        return get(ID_TEXT_FIELD_CONTAINER);
    }
}
