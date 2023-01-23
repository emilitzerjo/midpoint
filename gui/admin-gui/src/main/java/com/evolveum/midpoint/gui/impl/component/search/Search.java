/*
 * Copyright (c) 2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.component.search;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.xml.namespace.QName;

import org.apache.commons.lang3.StringUtils;

import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.api.util.WebModelServiceUtils;
import com.evolveum.midpoint.gui.impl.component.search.wrapper.*;
import com.evolveum.midpoint.model.api.authentication.CompiledObjectCollectionView;
import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.PrismContainerDefinition;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.schema.expression.TypedValue;
import com.evolveum.midpoint.schema.expression.VariablesMap;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.DebugDumpable;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

public class Search<C extends Containerable> implements Serializable, DebugDumpable {

    private static final Trace LOGGER = TraceManager.getTrace(Search.class);

    private static final String DOT_CLASS = Search.class.getName() + ".";
    private static final String OPERATION_EVALUATE_COLLECTION_FILTER = DOT_CLASS + "evaluateCollectionFilter";
    public static final String F_FULL_TEXT = "fullText";
    public static final String F_TYPE = "type";

    public static final String F_MODE = "defaultSearchBoxMode";
    public static final String F_ALLOWED_MODES = "allowedModeList";

    public static final String F_OID_SEARCH = "oidSearchItemWrapper";
    public static final String F_ADVANCED_SEARCH = "advancedQueryWrapper";
    public static final String F_AXIOM_SEARCH = "axiomQueryWrapper";
    public static final String F_FULLTEXT_SEARCH = "fulltextQueryWrapper";
    public static final String F_BASIC_SEARCH = "searchConfigurationWrapper";

    private ObjectTypeSearchItemWrapper<C> type;
    private SearchBoxModeType defaultSearchBoxMode;
    private List<SearchBoxModeType> allowedModeList = new ArrayList<>();
    private AdvancedQueryWrapper advancedQueryWrapper;
    private AxiomQueryWrapper axiomQueryWrapper;
    private FulltextQueryWrapper fulltextQueryWrapper;
    private SearchConfigurationWrapper searchConfigurationWrapper;

    private OidSearchItemWrapper oidSearchItemWrapper;
    private String advancedError;
    private String collectionViewName;
    private String collectionRefOid;

    private List<AvailableFilterType> availableFilterTypes;

    public String getCollectionViewName() {
        return collectionViewName;
    }

    public void setCollectionViewName(String collectionViewName) {
        this.collectionViewName = collectionViewName;
    }

    public String getCollectionRefOid() {
        return collectionRefOid;
    }

    public void setCollectionRefOid(String collectionRefOid) {
        this.collectionRefOid = collectionRefOid;
    }

    public Search(ObjectTypeSearchItemWrapper<C> type, SearchBoxConfigurationType searchBoxConfigurationType) {

        this.type = type;
        this.defaultSearchBoxMode = searchBoxConfigurationType.getDefaultMode();
        this.availableFilterTypes = searchBoxConfigurationType.getAvailableFilter();
    }

    public List<QName> getAllowedTypeList() {
        return type.getAvailableValues();
    }

    void setAdvancedQueryWrapper(AdvancedQueryWrapper advancedQueryWrapper) {
        this.advancedQueryWrapper = advancedQueryWrapper;
    }

    void setAxiomQueryWrapper(AxiomQueryWrapper axiomQueryWrapper) {
        this.axiomQueryWrapper = axiomQueryWrapper;
    }

    void setSearchConfigurationWrapper(SearchConfigurationWrapper searchConfigurationWrapper) {
        this.searchConfigurationWrapper = searchConfigurationWrapper;
    }

    void setFulltextQueryWrapper(FulltextQueryWrapper fulltextQueryWrapper) {
        this.fulltextQueryWrapper = fulltextQueryWrapper;
    }

    public void setOidSearchItemWrapper(OidSearchItemWrapper oidSearchItemWrapper) {
        this.oidSearchItemWrapper = oidSearchItemWrapper;
    }

    public List<FilterableSearchItemWrapper> getItems() {
        return searchConfigurationWrapper.getItemsList();
    }

    public SearchBoxModeType getSearchMode() {
        return defaultSearchBoxMode;
    }

    public void setSearchMode(SearchBoxModeType searchMode) {
        this.defaultSearchBoxMode = searchMode;
    }

    public boolean isFullTextSearchEnabled() {
        return allowedModeList.contains(SearchBoxModeType.FULLTEXT);
    }

    public List<SearchBoxModeType> getAllowedModeList() {
        return allowedModeList;
    }

    public void setAllowedModeList(List<SearchBoxModeType> allowedModeList) {
        this.allowedModeList = allowedModeList;
    }

    public void addAllowedModelType(SearchBoxModeType allowedModeType) {
        if (allowedModeList == null) {
            allowedModeList = new ArrayList<>();
        }
        allowedModeList.add(allowedModeType);
    }
    public boolean isAdvancedQueryValid(PrismContext ctx) {
        try {
            advancedError = null;

            createAdvancedObjectFilter(ctx);
            return true;
        } catch (Exception ex) {
            advancedError = createErrorMessage(ex);
        }

        return false;
    }

    public String getAdvancedError() {
        return advancedError;
    }

    public String getAdvancedQuery() {
        return advancedQueryWrapper.getAdvancedQuery();
    }

    public String getDslQuery() {
        return axiomQueryWrapper.getDslQuery();
    }

    public void setDslQuery(String dslQuery) {
        axiomQueryWrapper = new AxiomQueryWrapper();
        axiomQueryWrapper.setDslQuery(dslQuery);
    }

    private ObjectQuery createAdvancedObjectFilter(PrismContext ctx) throws SchemaException {
        SearchBoxModeType searchMode = getSearchMode();
        if (SearchBoxModeType.ADVANCED.equals(searchMode)) {
            return advancedQueryWrapper.createQuery(ctx);
        } else if (SearchBoxModeType.AXIOM_QUERY.equals(searchMode)) {
            return axiomQueryWrapper.createQuery(ctx);
        }

        return null;
    }

    public Class<C> getTypeClass() {
        if (SearchBoxModeType.OID.equals(getSearchMode())) {
            return (Class<C> )  ObjectType.class;
        }
        if (type.getValue().getValue() != null){
            return (Class<C>) WebComponentUtil.qnameToClass(PrismContext.get(), type.getValue().getValue());
        } else if (type.getValueForNull() != null) {
            return (Class<C>) WebComponentUtil.qnameToClass(PrismContext.get(), type.getValueForNull());
        }

        return null; //should not happen
    }

    private String createErrorMessage(Exception ex) {
        StringBuilder sb = new StringBuilder();

        Throwable t = ex;
        while (t != null && t.getMessage() != null) {
            sb.append(t.getMessage()).append('\n');
            t = t.getCause();
        }
        if (StringUtils.isBlank(sb.toString())) {
            sb.append(PageBase.createStringResourceStatic("SearchPanel.unexpectedQuery").getString());
        }

        return sb.toString();
    }


    public ObjectQuery createObjectQuery(PageBase pageBase) {
        return this.createObjectQuery(null, pageBase);
    }

    public ObjectQuery createObjectQuery(VariablesMap variables, PageBase pageBase) {
        return this.createObjectQuery(variables, pageBase, null);
    }

    public ObjectQuery createObjectQuery(VariablesMap variables, PageBase pageBase, ObjectQuery customizeContentQuery) {
        LOGGER.debug("Creating query from {}", this);
        ObjectQuery query;
        SearchBoxModeType searchMode = getSearchMode();
        if (SearchBoxModeType.OID.equals(getSearchMode())) {
            query = createObjectQueryOid(pageBase);
        } else {
            query = createObjectTypeItemQuery(pageBase);
            ObjectQuery searchTypeQuery = null;
            if (SearchBoxModeType.ADVANCED.equals(searchMode) || SearchBoxModeType.AXIOM_QUERY.equals(searchMode)) {
                searchTypeQuery = createObjectQueryAdvanced(pageBase);
            } else if (SearchBoxModeType.FULLTEXT.equals(searchMode)) {
                try {
                    searchTypeQuery = fulltextQueryWrapper.createQuery(pageBase.getPrismContext());//createObjectQueryFullText(pageBase);
                } catch (SchemaException e) {
                    //TODO
                    throw new RuntimeException(e);
                }
            } else {
                searchTypeQuery = createObjectQuerySimple(variables, pageBase);
            }

            query = mergeQueries(query, searchTypeQuery);
            if (query == null) {
                query = pageBase.getPrismContext().queryFor(getTypeClass()).build();
            }

            ObjectQuery archetypeQuery = evaluateCollectionFilter(pageBase);
            query = mergeQueries(query, archetypeQuery);
        }
        query = mergeQueries(query, customizeContentQuery);
        LOGGER.debug("Created query: {}", query);
        return query;
    }

    private ObjectQuery createObjectQueryAdvanced(PageBase pageBase) {
        try{
            advancedError = null;

            ObjectQuery query = createAdvancedObjectFilter(pageBase.getPrismContext());
            return query;
        } catch (Exception ex) {
            advancedError = createErrorMessage(ex);
        }

        return null;
    }

    private ObjectQuery createObjectQueryOid(PageBase pageBase) {
        OidSearchItemWrapper oidItem = findOidSearchItemWrapper();
        if (oidItem == null) {
            return null;
        }
        if (StringUtils.isEmpty(oidItem.getValue().getValue())) {
            return pageBase.getPrismContext().queryFor(ObjectType.class).build();
        }
        ObjectQuery query = pageBase.getPrismContext().queryFor(ObjectType.class)
                .id(oidItem.getValue().getValue())
                .build();
        return query;
    }

    public OidSearchItemWrapper findOidSearchItemWrapper() {
        return oidSearchItemWrapper;
//        List<FilterableSearchItemWrapper> items = searchConfigurationWrapper.getItemsList();
//        for (FilterableSearchItemWrapper item : items) {
//            if (item instanceof OidSearchItemWrapper) {
//                return (OidSearchItemWrapper) item;
//            }
//        }
//        return null;
    }

    public ObjectCollectionSearchItemWrapper findObjectCollectionSearchItemWrapper() {
        List<FilterableSearchItemWrapper> items = searchConfigurationWrapper.getItemsList();
        for (FilterableSearchItemWrapper item : items) {
            if (item instanceof ObjectCollectionSearchItemWrapper) {
                return (ObjectCollectionSearchItemWrapper) item;
            }
        }
        return null;
    }
    public AbstractRoleSearchItemWrapper findMemberSearchItem() {
        List<FilterableSearchItemWrapper<?>> items = searchConfigurationWrapper.getItemsList();
        for (FilterableSearchItemWrapper<?> item : items) {
            if (item instanceof AbstractRoleSearchItemWrapper) {
                return (AbstractRoleSearchItemWrapper) item;
            }
        }
        return null;
    }

    private ObjectQuery createObjectTypeItemQuery(PageBase pageBase) {
        ObjectQuery query;
        if (getTypeClass() != null) {
            query = pageBase.getPrismContext().queryFor(getTypeClass()).build();
        } else {
            query = pageBase.getPrismContext().queryFactory().createQuery();
        }
        return query;
    }

    private ObjectQuery evaluateCollectionFilter(PageBase pageBase) {
        CompiledObjectCollectionView view;
        OperationResult result = new OperationResult(OPERATION_EVALUATE_COLLECTION_FILTER);
        Task task = pageBase.createSimpleTask(OPERATION_EVALUATE_COLLECTION_FILTER);
        ObjectFilter collectionFilter = null;
        if (findObjectCollectionSearchItemWrapper() != null && findObjectCollectionSearchItemWrapper().getObjectCollectionView() != null) {
            view = findObjectCollectionSearchItemWrapper().getObjectCollectionView();
            collectionFilter = view != null ? view.getFilter() : null;
        } else if (StringUtils.isNotEmpty(getCollectionViewName())) {
            view = pageBase.getCompiledGuiProfile()
                    .findObjectCollectionView(WebComponentUtil.containerClassToQName(pageBase.getPrismContext(), getTypeClass()),
                            getCollectionViewName());
            collectionFilter = view != null ? view.getFilter() : null;
        } else if (StringUtils.isNotEmpty(getCollectionRefOid())) {
            try {
                PrismObject<ObjectCollectionType> collection = WebModelServiceUtils.loadObject(ObjectCollectionType.class,
                        getCollectionRefOid(), pageBase, task, result);
                if (collection != null && collection.asObjectable().getFilter() != null) {
                    collectionFilter = PrismContext.get().getQueryConverter().parseFilter(collection.asObjectable().getFilter(), getTypeClass());
                }
            } catch (SchemaException e) {
                LOGGER.error("Failed to parse filter from object collection, oid {}, {}", getCollectionRefOid(), e.getStackTrace());
                pageBase.error("Failed to parse filter from object collection, oid " + getCollectionRefOid());
            }
        }
        if (collectionFilter == null) {
            return null;
        }
        ObjectQuery query = pageBase.getPrismContext().queryFor(getTypeClass()).build();
        query.addFilter(WebComponentUtil.evaluateExpressionsInFilter(collectionFilter, result, pageBase));
        return query;

    }

    private ObjectQuery mergeQueries(ObjectQuery origQuery, ObjectQuery query) {
        if (query != null) {
            if (origQuery == null) {
                return query;
            } else {
                origQuery.addFilter(query.getFilter());
            }
        }
        return origQuery;
    }

    private ObjectQuery createObjectQuerySimple(VariablesMap defaultVariables, PageBase pageBase) {
        List<FilterableSearchItemWrapper> searchItems = getItems();
        if (searchItems.isEmpty()) {
            return null;
        }

        ObjectQuery query = null;
        if (query == null) {
            if (getTypeClass() != null) {
                query = pageBase.getPrismContext().queryFor(getTypeClass()).build();
            } else {
                query = pageBase.getPrismContext().queryFactory().createQuery();
            }
        }
        List<ObjectFilter> filters = getSearchItemFilterList(pageBase, defaultVariables);
        if (filters != null) {
            query.addFilter(pageBase.getPrismContext().queryFactory().createAnd(filters));
        }
        return query;
    }

    public List<ObjectFilter> getSearchItemFilterList(PageBase pageBase, VariablesMap defaultVariables) {
        List<ObjectFilter> conditions = new ArrayList<>();
        if (!SearchBoxModeType.BASIC.equals(getSearchMode())) {
            return conditions;
        }
        for (FilterableSearchItemWrapper item : getItems()) {

            ObjectFilter filter = item.createFilter(getTypeClass(), pageBase, defaultVariables);
            if (filter != null) {
                conditions.add(filter);
            }
        }
        return conditions;
    }

    public VariablesMap getFilterVariables(VariablesMap defaultVariables, PageBase pageBase) {
        VariablesMap variables = defaultVariables == null ? new VariablesMap() : defaultVariables;
        List<FilterableSearchItemWrapper> items = getItems();
        items.forEach(item -> {
            if (StringUtils.isNotEmpty(item.getParameterName())) {
                Object parameterValue = item.getValue() != null ? item.getValue().getValue() : null;
                TypedValue value = new TypedValue(parameterValue, item.getParameterValueType());
                variables.put(item.getParameterName(), value);
            }
        });
        return variables;
    }

    public void setAdvancedQuery(String advancedQuery) {
        advancedQueryWrapper = new AdvancedQueryWrapper();
        advancedQueryWrapper.setAdvancedQuery(advancedQuery);
    }

    public String getFullText() {
        return fulltextQueryWrapper.getFullText();
    }

    public void setFullText(String fullText) {
        fulltextQueryWrapper = new FulltextQueryWrapper();
        fulltextQueryWrapper.setFullText(fullText);
    }

    public PropertySearchItemWrapper findPropertyItemByPath(ItemPath path) {
        for (FilterableSearchItemWrapper searchItemWrapper : getItems()) {
            if (!(searchItemWrapper instanceof PropertySearchItemWrapper)) {
                continue;
            }
            if (path.equivalent(((PropertySearchItemWrapper)searchItemWrapper).getPath())) {
                return (PropertySearchItemWrapper)searchItemWrapper;
            }
        }
        return null;
    }

    public PropertySearchItemWrapper findPropertySearchItem(ItemPath path) {
        if (path == null) {
            return null;
        }
        for (FilterableSearchItemWrapper searchItem : getItems()) {
            if (!(searchItem instanceof PropertySearchItemWrapper)) {
                continue;
            }
            if (path.equivalent(((PropertySearchItemWrapper)searchItem).getPath())) {
                return (PropertySearchItemWrapper) searchItem;
            }
        }
        return null;
    }

    @Override
    public String debugDump(int indent) {
        StringBuilder sb = new StringBuilder();
        DebugUtil.indentDebugDump(sb, indent);
        sb.append("Search\n");
        DebugUtil.debugDumpWithLabelLn(sb, "advancedError", advancedError, indent + 1);
        DebugUtil.debugDumpWithLabelLn(sb, "type", getTypeClass(), indent + 1);
        DebugUtil.dumpObjectSizeEstimate(sb, "itemsList", searchConfigurationWrapper, indent + 2);
        List<FilterableSearchItemWrapper> items = searchConfigurationWrapper.getItemsList();
        for (FilterableSearchItemWrapper item : items) {
            DebugUtil.dumpObjectSizeEstimate(sb, "item " + item.getName(), item, indent + 2);
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return "Search{" +
                //todo implement
                '}';
    }

    public boolean searchByNameEquals(String nameValueToCompare) {
        String nameValue = null;
        if (SearchBoxModeType.BASIC.equals(getSearchMode())) {
            PropertySearchItemWrapper nameItem = findPropertySearchItem(ObjectType.F_NAME);
            nameValue = nameItem != null && nameItem.getValue() != null ? (String) nameItem.getValue().getValue() : null;
        } else if (SearchBoxModeType.FULLTEXT.equals(getSearchMode())) {
            nameValue = getFullText();
        }
        return nameValueToCompare != null && nameValueToCompare.equals(nameValue);
    }

    public boolean isForceReload() {
        return type.isTypeChanged();
    }

    public List<AvailableFilterType> getAvailableFilterTypes() {
        return availableFilterTypes;
    }
}
