/*
 * Copyright (C) 2010-2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.provisioning.impl.operations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.query.NoneFilter;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.util.CloneUtil;
import com.evolveum.midpoint.provisioning.impl.CommonBeans;
import com.evolveum.midpoint.schema.*;
import com.evolveum.midpoint.schema.internals.InternalsConfig;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectQueryUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;

/**
 * Handles `searchObjects`, `searchObjectsIterative`, and `countObjects` operations for resources, shadows,
 * and other kinds of objects.
 *
 * A special responsibility: completes non-shadow objects returned from the repository. (This currently applies to resources.)
 */
public class ProvisioningSearchLikeOperation<T extends ObjectType> {

    private static final String OP_COMPLETE_OBJECTS = ProvisioningSearchLikeOperation.class.getName() + ".completeObjects";
    private static final String OP_COMPLETE_OBJECT = ProvisioningSearchLikeOperation.class.getName() + ".completeObject";

    private static final Trace LOGGER = TraceManager.getTrace(ProvisioningSearchLikeOperation.class);

    @NotNull private final Class<T> type;
    @Nullable private final ObjectQuery query;
    @Nullable private final ObjectFilter filter;
    @Nullable private final Collection<SelectorOptions<GetOperationOptions>> options;
    @Nullable private final GetOperationOptions rootOptions;
    @NotNull private final Task task;
    @NotNull private final CommonBeans beans;

    public ProvisioningSearchLikeOperation(
            @NotNull Class<T> type,
            @Nullable ObjectQuery query,
            @Nullable Collection<SelectorOptions<GetOperationOptions>> options,
            @NotNull Task task,
            @NotNull CommonBeans beans) {
        this.type = type;
        this.query = simplifyQueryFilter(query);
        this.filter = this.query != null ? this.query.getFilter() : null;
        this.options = options;
        this.rootOptions = SelectorOptions.findRootOptions(options);
        this.task = task;
        this.beans = beans;

        checkFilterConsistence();
    }

    public @NotNull SearchResultList<PrismObject<T>> executeSearch(OperationResult result)
            throws SchemaException, ExpressionEvaluationException, CommunicationException, SecurityViolationException,
            ConfigurationException, ObjectNotFoundException {

        if (filter instanceof NoneFilter) {
            SearchResultList<PrismObject<T>> objListType = new SearchResultList<>(new ArrayList<>());
            objListType.setMetadata(createNoneFilterMetadata());
            return objListType;
        }

        if (ShadowType.class.isAssignableFrom(type)) {
            //noinspection unchecked,rawtypes
            return (SearchResultList) beans.shadowsFacade.searchObjects(query, options, task, result);
        } else {
            // TODO: should searching connectors trigger rediscovery?
            SearchResultList<PrismObject<T>> repoObjects =
                    beans.cacheRepositoryService.searchObjects(type, query, createRepoOptions(), result);
            return completeNonShadowRepoObjects(repoObjects, result);
        }
    }

    public SearchResultMetadata executeIterativeSearch(@NotNull ResultHandler<T> handler, OperationResult result)
            throws SchemaException, ExpressionEvaluationException, CommunicationException, SecurityViolationException,
            ConfigurationException, ObjectNotFoundException {

        if (filter instanceof NoneFilter) {
            return createNoneFilterMetadata();
        }

        if (ShadowType.class.isAssignableFrom(type)) {
            //noinspection unchecked
            return beans.shadowsFacade.searchObjectsIterative(query, options, (ResultHandler<ShadowType>) handler, task, result);
        } else {
            ResultHandler<T> internalHandler =
                    (object, objResult) ->
                            handler.handle(
                                    completeNonShadowRepoObject(object, objResult),
                                    objResult);
            return beans.cacheRepositoryService.searchObjectsIterative(
                    type, query, internalHandler, createRepoOptions(), true, result);
        }
    }

    public Integer executeCount(OperationResult result)
            throws SchemaException, ExpressionEvaluationException, CommunicationException, SecurityViolationException,
            ConfigurationException, ObjectNotFoundException {

        if (filter instanceof NoneFilter) {
            return createNoneFilterCount();
        }

        if (ShadowType.class.isAssignableFrom(type)
                && !GetOperationOptions.isNoFetch(rootOptions)
                && !GetOperationOptions.isRaw(rootOptions)) {
            // There is a irregularity: the shadow facade does not treat raw/no-fetch counting (like it does for searching)
            return beans.shadowsFacade.countObjects(query, task, result);
        } else {
            return beans.cacheRepositoryService.countObjects(type, query, options, result);
        }
    }

    private ObjectQuery simplifyQueryFilter(ObjectQuery query) {
        if (query != null) {
            ObjectFilter filter = ObjectQueryUtil.simplify(query.getFilter());
            ObjectQuery clone = query.cloneWithoutFilter();
            clone.setFilter(filter);
            return clone;
        } else {
            return null;
        }
    }

    private @NotNull SearchResultList<PrismObject<T>> completeNonShadowRepoObjects(
            @NotNull SearchResultList<PrismObject<T>> repoObjects, OperationResult parentResult) {

        if (isRawMode()) {
            return repoObjects;
        }

        OperationResult result = parentResult.createMinorSubresult(OP_COMPLETE_OBJECTS);
        result.setSummarizeSuccesses(true);
        result.setSummarizeErrors(true);
        result.setSummarizePartialErrors(true);

        try {
            List<PrismObject<T>> processedObjects = new ArrayList<>();
            for (PrismObject<T> repoObject : repoObjects) {
                processedObjects.add(
                        completeNonShadowRepoObject(repoObject, result));
                result.summarize();
            }
            return new SearchResultList<>(
                    processedObjects,
                    CloneUtil.cloneCloneable(repoObjects.getMetadata()));
        } catch (Throwable t) {
            result.recordFatalError(t);
            throw t;
        } finally {
            result.close();
        }
    }

    private @NotNull PrismObject<T> completeNonShadowRepoObject(@NotNull PrismObject<T> object, OperationResult parentResult) {
        if (isRawMode()) {
            return object;
        }
        OperationResult result = parentResult.subresult(OP_COMPLETE_OBJECT)
                .addParam(OperationResult.PARAM_OBJECT, object)
                .setMinor()
                .build();
        PrismObject<T> completedObject = object;
        try {
            if (ResourceType.class.equals(type)) {
                //noinspection unchecked
                completedObject = (PrismObject<T>) beans.resourceManager.completeResource(
                        (PrismObject<ResourceType>) object, rootOptions, task, result);
            }
        } catch (Throwable t) {
            // FIXME: Strictly speaking, the runtime exceptions should not be handled here.
            //  The runtime exceptions should be considered to be programming errors ... but some of the
            //  ICF exceptions are still translated to system exceptions. So this provides a better robustness now.
            LoggingUtils.logException(LOGGER, "Error while completing {}. Using non-complete object.", t, object);
            result.recordFatalError(t);
            // Intentionally not re-throwing the exception
        } finally {
            result.close();
        }

        // This must be done after result is closed
        if (!result.isSuccess()) {
            completedObject = completedObject.cloneIfImmutable();
            completedObject.asObjectable().setFetchResult(result.createBeanReduced());
        }
        return completedObject;
    }

    private void checkFilterConsistence() {
        if (InternalsConfig.consistencyChecks && filter != null) {
            // We may not have all the definitions here. We will apply the definitions later
            filter.checkConsistence(false);
        }
    }

    private @NotNull SearchResultMetadata createNoneFilterMetadata() {
        SearchResultMetadata metadata = new SearchResultMetadata();
        metadata.setApproxNumberOfAllResults(0);
        LOGGER.trace("Finished searching. Nothing to do. Filter is NONE. Metadata: {}", metadata.shortDumpLazily());
        return metadata;
    }

    private int createNoneFilterCount() {
        LOGGER.trace("Finished counting. Nothing to do. Filter is NONE");
        return 0;
    }

    private Collection<SelectorOptions<GetOperationOptions>> createRepoOptions() {
        if (GetOperationOptions.isReadOnly(rootOptions)) {
            return SelectorOptions.createCollection(GetOperationOptions.createReadOnly());
        } else {
            return null;
        }
    }

    private boolean isRawMode() {
        return GetOperationOptions.isRaw(rootOptions);
    }
}
