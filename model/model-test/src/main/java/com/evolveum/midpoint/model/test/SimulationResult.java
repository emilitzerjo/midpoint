/*
 * Copyright (C) 2010-2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.model.test;

import static org.assertj.core.api.Assertions.assertThat;

import static com.evolveum.midpoint.util.MiscUtil.stateCheck;

import java.util.Collection;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.model.api.ProgressInformation;
import com.evolveum.midpoint.model.api.ProgressListener;
import com.evolveum.midpoint.model.api.context.ModelContext;
import com.evolveum.midpoint.model.api.simulation.ProcessedObject;
import com.evolveum.midpoint.model.api.simulation.SimulationResultManager;
import com.evolveum.midpoint.model.common.TagManager;
import com.evolveum.midpoint.provisioning.api.ProvisioningService;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.test.MidpointTestContextWithTask;
import com.evolveum.midpoint.test.TestSpringBeans;
import com.evolveum.midpoint.util.annotation.Experimental;
import com.evolveum.midpoint.util.exception.CommonException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SimulationResultType;

/**
 * TODO
 */
@SuppressWarnings("WeakerAccess") // temporary
@Experimental
public class SimulationResult {

    @NotNull private final String simulationResultOid;
    private ModelContext<?> lastModelContext;

    SimulationResult(@NotNull String simulationResultOid) {
        this.simulationResultOid = simulationResultOid;
    }

    public static SimulationResult fromSimulationResultOid(@NotNull String simulationResultOid) {
        return new SimulationResult(simulationResultOid);
    }

    public ModelContext<?> getLastModelContext() {
        return lastModelContext;
    }

    ProgressListener contextRecordingListener() {
        return new ProgressListener() {
            @Override
            public void onProgressAchieved(ModelContext<?> modelContext, ProgressInformation progressInformation) {
                lastModelContext = modelContext;
            }

            @Override
            public boolean isAbortRequested() {
                return false;
            }
        };
    }

    public SimulationResultType getSimulationResultBean(OperationResult result) throws SchemaException, ObjectNotFoundException {
        assertThat(simulationResultOid).as("simulation result OID").isNotNull();
        return TestSpringBeans.getCacheRepositoryService()
                .getObject(SimulationResultType.class, simulationResultOid, null, result)
                .asObjectable();
    }

    public @NotNull List<? extends ProcessedObject<?>> getProcessedObjects(OperationResult result)
            throws CommonException {
        stateCheck(
                simulationResultOid != null,
                "Asking for persistent processed objects but there is no simulation result OID");
        List<? extends ProcessedObject<?>> objects = TestSpringBeans.getBean(SimulationResultManager.class)
                .getStoredProcessedObjects(simulationResultOid, result);
        resolveTagNames(objects, result);
        applyAttributesDefinitions(objects, result);
        return objects;
    }

    public void resolveTagNames(Collection<? extends ProcessedObject<?>> processedObjects, OperationResult result) {
        TagManager tagManager = TestSpringBeans.getBean(TagManager.class);
        for (ProcessedObject<?> processedObject : processedObjects) {
            if (processedObject.getEventTagsMap() == null) {
                processedObject.setEventTagsMap(
                        tagManager.resolveTagNames(processedObject.getEventTags(), result));
            }
        }
    }

    /**
     * Shadow deltas stored in the repository have no definitions. These will be found and applied now.
     */
    private void applyAttributesDefinitions(List<? extends ProcessedObject<?>> objects, OperationResult result)
            throws CommonException {
        for (ProcessedObject<?> object : objects) {
            if (object.getDelta() == null
                    || !ShadowType.class.equals(object.getType())) {
                continue;
            }
            ShadowType shadow = (ShadowType) object.getAfterOrBefore();
            if (shadow == null) {
                throw new IllegalStateException("No object? In: " + object);
            }
            TestSpringBeans.getBean(ProvisioningService.class)
                    .applyDefinition(object.getDelta(), MidpointTestContextWithTask.get().getTask(), result);
        }
    }

    public @NotNull String getSimulationResultOid() {
        return simulationResultOid;
    }
}
