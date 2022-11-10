/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.provisioning.impl.shadows;

import static com.evolveum.midpoint.provisioning.impl.shadows.ShadowsFacade.OP_DELAYED_OPERATION;
import static com.evolveum.midpoint.provisioning.impl.shadows.Util.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;

import com.evolveum.midpoint.provisioning.impl.resourceobjects.ResourceObjectConverter;

import com.evolveum.midpoint.util.exception.CommonException.Severity;

import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.common.Clock;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.crypto.EncryptionException;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.ItemDeltaCollectionsUtil;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.delta.PropertyDelta;
import com.evolveum.midpoint.provisioning.api.EventDispatcher;
import com.evolveum.midpoint.provisioning.api.ProvisioningOperationOptions;
import com.evolveum.midpoint.provisioning.api.ResourceOperationDescription;
import com.evolveum.midpoint.provisioning.impl.*;
import com.evolveum.midpoint.provisioning.impl.shadows.errors.ErrorHandlerLocator;
import com.evolveum.midpoint.provisioning.impl.shadows.manager.ShadowManager;
import com.evolveum.midpoint.provisioning.ucf.api.ConnectorOperationOptions;
import com.evolveum.midpoint.provisioning.ucf.api.GenericFrameworkException;
import com.evolveum.midpoint.provisioning.util.ProvisioningUtil;
import com.evolveum.midpoint.schema.ObjectDeltaOperation;
import com.evolveum.midpoint.schema.RefreshShadowOperation;
import com.evolveum.midpoint.schema.internals.InternalCounters;
import com.evolveum.midpoint.schema.internals.InternalMonitor;
import com.evolveum.midpoint.schema.result.AsynchronousOperationReturnValue;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.result.OperationResultStatus;
import com.evolveum.midpoint.schema.util.ResourceTypeUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.annotation.Experimental;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OperationProvisioningScriptsType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.PendingOperationExecutionStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.PendingOperationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;

/**
 * Helps with the `modify` operation.
 */
@Component
@Experimental
class ModifyHelper {

    private static final Trace LOGGER = TraceManager.getTrace(ModifyHelper.class);

    @Autowired private ErrorHandlerLocator errorHandlerLocator;
    @Autowired private Clock clock;
    @Autowired private PrismContext prismContext;
    @Autowired private ResourceObjectConverter resourceObjectConverter;
    @Autowired protected ShadowManager shadowManager;
    @Autowired private EventDispatcher eventDispatcher;
    @Autowired private AccessChecker accessChecker;
    @Autowired private ProvisioningContextFactory ctxFactory;
    @Autowired private EntitlementsHelper entitlementsHelper;
    @Autowired private CommonHelper commonHelper;
    @Autowired private RefreshHelper refreshHelper;

    String modifyShadow(
            @NotNull ShadowType repoShadow,
            @NotNull Collection<? extends ItemDelta<?, ?>> modifications,
            @Nullable OperationProvisioningScriptsType scripts,
            @Nullable ProvisioningOperationOptions options,
            @NotNull Task task,
            @NotNull OperationResult result)
            throws CommunicationException, GenericFrameworkException, ObjectNotFoundException, SchemaException,
            ConfigurationException, SecurityViolationException, PolicyViolationException, ExpressionEvaluationException,
            EncryptionException, ObjectAlreadyExistsException {

        Validate.notNull(repoShadow, "Object to modify must not be null.");
        Validate.notNull(modifications, "Object modification must not be null.");

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Start modifying {}{}:\n{}", repoShadow, getAdditionalOperationDesc(scripts, options),
                    DebugUtil.debugDump(modifications, 1));
        }

        InternalMonitor.recordCount(InternalCounters.SHADOW_CHANGE_OPERATION_COUNT);

        ProvisioningContext ctx = ctxFactory.createForShadow(
                repoShadow,
                getAdditionalAuxObjectClassNames(modifications),
                task,
                result);
        ctx.assertDefinition();

        ProvisioningOperationState<AsynchronousOperationReturnValue<Collection<PropertyDelta<PrismPropertyValue<?>>>>> opState =
                new ProvisioningOperationState<>();
        opState.setRepoShadow(repoShadow);

        // if not explicitly we want to force retry operations during modify
        // it is quite cheap and probably more safe than not do it
        if (options == null) {
            options = ProvisioningOperationOptions.createForceRetry(Boolean.TRUE);
        } else if (options.getForceRetry() == null) {
            options.setForceRetry(Boolean.TRUE);
        }

        return modifyShadowAttempt(ctx, modifications, scripts, options, opState, false, result);
    }

    @NotNull
    private static Collection<QName> getAdditionalAuxObjectClassNames(@NotNull Collection<? extends ItemDelta<?, ?>> modifications) {
        Collection<QName> additionalAuxiliaryObjectClassQNames = new ArrayList<>();
        for (ItemDelta<?, ?> modification : modifications) {
            if (ShadowType.F_AUXILIARY_OBJECT_CLASS.equivalent(modification.getPath())) {
                //noinspection unchecked
                PropertyDelta<QName> auxDelta = (PropertyDelta<QName>) modification;
                for (PrismPropertyValue<QName> pval : auxDelta.getValues(QName.class)) {
                    additionalAuxiliaryObjectClassQNames.add(pval.getValue());
                }
            }
        }
        return additionalAuxiliaryObjectClassQNames;
    }

    /**
     * @param inRefresh True if we are already in refresh shadow method. This means we shouldn't refresh ourselves!
     */
    String modifyShadowAttempt(
            @NotNull ProvisioningContext ctx,
            @NotNull Collection<? extends ItemDelta<?, ?>> modifications,
            @Nullable OperationProvisioningScriptsType scripts,
            @Nullable ProvisioningOperationOptions options,
            @NotNull ProvisioningOperationState<AsynchronousOperationReturnValue<Collection<PropertyDelta<PrismPropertyValue<?>>>>> opState,
            boolean inRefresh,
            @NotNull OperationResult result)
            throws CommunicationException, GenericFrameworkException, ObjectNotFoundException, SchemaException,
            ConfigurationException, SecurityViolationException, PolicyViolationException, ExpressionEvaluationException,
            EncryptionException, ObjectAlreadyExistsException {

        ShadowType repoShadow = Objects.requireNonNull(opState.getRepoShadow());

        XMLGregorianCalendar now = clock.currentTimeXMLGregorianCalendar();

        PendingOperationType duplicateOperation =
                shadowManager.checkAndRecordPendingModifyOperationBeforeExecution(ctx, modifications, opState, result);
        if (duplicateOperation != null) {
            result.setInProgress();
            return repoShadow.getOid();
        }

        ctx.applyAttributesDefinition(repoShadow);

        accessChecker.checkModify(ctx, modifications, result);

        entitlementsHelper.provideEntitlementsIdentifiers(
                ctx, modifications, "delta for shadow " + repoShadow.getOid(), result);

        OperationResultStatus finalOperationStatus = null;

        if (shadowManager.isRepositoryOnlyModification(modifications)) {
            opState.setExecutionStatus(PendingOperationExecutionStatusType.COMPLETED);
            LOGGER.debug("MODIFY {}: repository-only modification", repoShadow);
        } else {
            if (ctx.shouldExecuteResourceOperationDirectly()) {
                LOGGER.trace("MODIFY {}: resource modification, execution starting\n{}", repoShadow, DebugUtil.debugDumpLazily(modifications));

                RefreshShadowOperation refreshShadowOperation;
                if (!inRefresh && Util.shouldRefresh(repoShadow)) {
                    refreshShadowOperation = refreshHelper.refreshShadow(repoShadow, options, ctx.getTask(), result);
                    ShadowType shadowAfterRefresh = refreshShadowOperation.getRefreshedShadow();
                    if (shadowAfterRefresh == null) {
                        LOGGER.trace("Shadow is gone. Nothing more to do");
                        throw new ObjectNotFoundException(
                                "Shadow disappeared during modify.", null, ShadowType.class, repoShadow.getOid(), Severity.PARTIAL_ERROR);
                    } else {
                        repoShadow = shadowAfterRefresh;
                    }
                } else {
                    refreshShadowOperation = null;
                }

                ConnectorOperationOptions connOptions = commonHelper.createConnectorOperationOptions(ctx, options, result);

                try {
                    if (ResourceTypeUtil.isInMaintenance(ctx.getResource())) {
                        throw new MaintenanceException("Resource " + ctx.getResource() + " is in the maintenance");
                    }

                    if (!shouldExecuteModify(refreshShadowOperation)) {
                        ProvisioningUtil.postponeModify(ctx, repoShadow, modifications, opState, refreshShadowOperation.getRefreshResult(), result);
                        shadowManager.recordModifyResult(ctx, repoShadow, modifications, opState, now, result);
                        return repoShadow.getOid();
                    } else {
                        LOGGER.trace("Shadow exists: {}", repoShadow.debugDump());
                    }

                    AsynchronousOperationReturnValue<Collection<PropertyDelta<PrismPropertyValue<?>>>> asyncReturnValue =
                            resourceObjectConverter
                                    .modifyResourceObject(ctx, repoShadow, scripts, connOptions, modifications, now, result);
                    opState.processAsyncResult(asyncReturnValue);

                    Collection<PropertyDelta<PrismPropertyValue<?>>> knownExecutedDeltas = asyncReturnValue.getReturnValue();
                    if (knownExecutedDeltas != null) {
                        ItemDeltaCollectionsUtil.addNotEquivalent(modifications, knownExecutedDeltas);
                    }

                } catch (Exception ex) {
                    LOGGER.debug("Provisioning exception: {}:{}, attempting to handle it",
                            ex.getClass(), ex.getMessage(), ex);
                    finalOperationStatus =
                            handleModifyError(
                                    ctx, repoShadow, modifications, options, opState, ex, result.getLastSubresult(), result);
                }

                LOGGER.debug("MODIFY {}: resource operation executed, operation state: {}", repoShadow, opState.shortDumpLazily());

            } else {
                opState.setExecutionStatus(PendingOperationExecutionStatusType.EXECUTION_PENDING);
                // Create dummy subresult with IN_PROGRESS state.
                // This will force the entire result (parent) to be IN_PROGRESS rather than SUCCESS.
                result.createSubresult(OP_DELAYED_OPERATION)
                        .recordInProgress(); // using "record" to immediately close the result
                LOGGER.debug("MODIFY {}: Resource operation NOT executed, execution pending", repoShadow);
            }
        }

        shadowManager.recordModifyResult(ctx, repoShadow, modifications, opState, now, result);

        notifyAfterModify(ctx, repoShadow, modifications, opState, result);

        setParentOperationStatus(result, opState, finalOperationStatus);

        return repoShadow.getOid();
    }

    @Contract("null -> true")
    private boolean shouldExecuteModify(RefreshShadowOperation refreshShadowOperation) {
        if (refreshShadowOperation == null) {
            LOGGER.trace("Nothing refreshed, modify can continue.");
            return true;
        }

        if (refreshShadowOperation.getExecutedDeltas() == null || refreshShadowOperation.getExecutedDeltas().isEmpty()) {
            LOGGER.trace("No executed deltas after refresh. Continue with modify operation.");
            return true;
        }

        if (refreshShadowOperation.getRefreshedShadow() == null) {
            LOGGER.trace("Shadow is gone. Probably it was deleted during refresh. Finishing modify operation now.");
            return false;
        }

        Collection<ObjectDeltaOperation<ShadowType>> objectDeltaOperations = refreshShadowOperation.getExecutedDeltas();
        for (ObjectDeltaOperation<ShadowType> shadowDelta : objectDeltaOperations) {
            if (!shadowDelta.getExecutionResult().isSuccess()) {
                LOGGER.trace("Refresh operation not successful. Finishing modify operation now.");
                return false;
            }
        }

        return true;
    }

    void notifyAfterModify(
            ProvisioningContext ctx,
            ShadowType repoShadow,
            Collection<? extends ItemDelta<?, ?>> modifications,
            ProvisioningOperationState<AsynchronousOperationReturnValue<Collection<PropertyDelta<PrismPropertyValue<?>>>>> opState,
            OperationResult result) {

        ObjectDelta<ShadowType> delta =
                prismContext.deltaFactory().object().createModifyDelta(repoShadow.getOid(), modifications, ShadowType.class);
        ResourceOperationDescription operationDescription =
                createSuccessOperationDescription(ctx, repoShadow, delta, null);

        if (opState.isExecuting()) {
            eventDispatcher.notifyInProgress(operationDescription, ctx.getTask(), result);
        } else {
            eventDispatcher.notifySuccess(operationDescription, ctx.getTask(), result);
        }
    }

    /**
     * Used to execute delayed operations.
     * Mostly copy&paste from modifyShadow(). But as consistency (handleError()) branch expects to return immediately
     * I could not find a more elegant way to structure this without complicating the code too much.
     */
    ProvisioningOperationState<AsynchronousOperationReturnValue<Collection<PropertyDelta<PrismPropertyValue<?>>>>> executeResourceModify(
            @NotNull ProvisioningContext ctx,
            @NotNull ShadowType repoShadow,
            @NotNull Collection<? extends ItemDelta<?, ?>> modifications,
            OperationProvisioningScriptsType scripts,
            ProvisioningOperationOptions options,
            XMLGregorianCalendar now,
            OperationResult result)
            throws SchemaException, GenericFrameworkException, CommunicationException, ObjectNotFoundException,
            ConfigurationException, SecurityViolationException, PolicyViolationException, ExpressionEvaluationException {
        ProvisioningOperationState<AsynchronousOperationReturnValue<Collection<PropertyDelta<PrismPropertyValue<?>>>>> opState = new ProvisioningOperationState<>();
        opState.setRepoShadow(repoShadow);

        ConnectorOperationOptions connOptions = commonHelper.createConnectorOperationOptions(ctx, options, result);

        try {

            LOGGER.trace("Applying change: {}", DebugUtil.debugDumpLazily(modifications));

            AsynchronousOperationReturnValue<Collection<PropertyDelta<PrismPropertyValue<?>>>> asyncReturnValue =
                    resourceObjectConverter
                            .modifyResourceObject(ctx, repoShadow, scripts, connOptions, modifications, now, result);
            opState.processAsyncResult(asyncReturnValue);

            Collection<PropertyDelta<PrismPropertyValue<?>>> knownExecutedDeltas = asyncReturnValue.getReturnValue();
            if (knownExecutedDeltas != null) {
                ItemDeltaCollectionsUtil.addNotEquivalent(modifications, knownExecutedDeltas);
            }

        } catch (Exception ex) {
            LOGGER.debug("Provisioning exception: {}:{}, attempting to handle it",
                    ex.getClass(), ex.getMessage(), ex);
            try {
                handleModifyError(ctx, repoShadow, modifications, options, opState, ex, result.getLastSubresult(), result);
            } catch (ObjectAlreadyExistsException e) {
                throw SystemException.unexpected(e, "while compensating communication problem for modify operation");
            }
        }

        return opState;
    }

    private OperationResultStatus handleModifyError(
            @NotNull ProvisioningContext ctx,
            @NotNull ShadowType repoShadow,
            @NotNull Collection<? extends ItemDelta<?, ?>> modifications,
            @Nullable ProvisioningOperationOptions options,
            @NotNull ProvisioningOperationState<AsynchronousOperationReturnValue<Collection<PropertyDelta<PrismPropertyValue<?>>>>> opState,
            @NotNull Exception cause,
            OperationResult failedOperationResult,
            @NotNull OperationResult result)
            throws SchemaException, GenericFrameworkException, CommunicationException, ObjectNotFoundException,
            ObjectAlreadyExistsException, ConfigurationException, SecurityViolationException, PolicyViolationException,
            ExpressionEvaluationException {

        // TODO: record operationExecution

        LOGGER.debug("Handling provisioning MODIFY exception {}: {}", cause.getClass(), cause.getMessage());
        try {

            OperationResultStatus finalStatus = errorHandlerLocator
                    .locateErrorHandlerRequired(cause)
                    .handleModifyError(ctx, repoShadow, modifications, options, opState, cause, failedOperationResult, result);
            LOGGER.debug("Handled provisioning MODIFY exception, final status: {}, operation state: {}", finalStatus, opState.shortDumpLazily());
            return finalStatus;

        } catch (CommonException e) {
            LOGGER.debug("Handled provisioning MODIFY exception, final exception: {}, operation state: {}", e, opState.shortDumpLazily());
            ObjectDelta<ShadowType> delta = repoShadow.asPrismObject().createModifyDelta();
            delta.addModifications(modifications);
            commonHelper.handleErrorHandlerException(ctx, opState, delta, e.getMessage(), result);
            throw e;
        }
    }

}
