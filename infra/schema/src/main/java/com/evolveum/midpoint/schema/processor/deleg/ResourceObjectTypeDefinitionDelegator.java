package com.evolveum.midpoint.schema.processor.deleg;

import com.evolveum.midpoint.schema.processor.*;

import com.evolveum.midpoint.xml.ns._public.common.common_3.CorrelationDefinitionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ExpressionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.LayerType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowKindType;
import com.evolveum.midpoint.xml.ns._public.resource.capabilities_3.CapabilityType;

import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.prism.path.ItemName;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.util.exception.SchemaException;

import org.jetbrains.annotations.Nullable;

import javax.xml.namespace.QName;
import java.util.Collection;

public interface ResourceObjectTypeDefinitionDelegator extends ResourceObjectDefinitionDelegator, ResourceObjectTypeDefinition {

    @Override
    ResourceObjectTypeDefinition delegate();

    @Override
    default @NotNull ObjectQuery createShadowSearchQuery(String resourceOid) throws SchemaException {
        return delegate().createShadowSearchQuery(resourceOid);
    }

    @Override
    default @NotNull ShadowKindType getKind() {
        return delegate().getKind();
    }

    @Override
    default @NotNull String getIntent() {
        return delegate().getIntent();
    }

    @Override
    default boolean isDefaultForKind() {
        return delegate().isDefaultForKind();
    }

    @Override
    default boolean isDefaultForObjectClass() {
        return delegate().isDefaultForObjectClass();
    }

    @Override
    default ResourceObjectTypeDefinition forLayer(@NotNull LayerType layerType) {
        return delegate().forLayer(layerType);
    }

    @Override
    default <T extends CapabilityType> @Nullable T getConfiguredCapability(Class<T> capabilityClass) {
        return delegate().getConfiguredCapability(capabilityClass);
    }

    @Override
    default @Nullable CorrelationDefinitionType getCorrelationDefinitionBean() {
        return delegate().getCorrelationDefinitionBean();
    }

    @Override
    default @Nullable Boolean isSynchronizationEnabled() {
        return delegate().isSynchronizationEnabled();
    }

    @Override
    default @Nullable Boolean isSynchronizationOpportunistic() {
        return delegate().isSynchronizationOpportunistic();
    }

    @Override
    default @Nullable QName getFocusTypeName() {
        return delegate().getFocusTypeName();
    }

    @Override
    default boolean hasSynchronizationReactionsDefinition() {
        return delegate().hasSynchronizationReactionsDefinition();
    }

    @Override
    default @NotNull Collection<SynchronizationReactionDefinition> getSynchronizationReactions() {
        return delegate().getSynchronizationReactions();
    }

    @Override
    default @Nullable ExpressionType getClassificationCondition() {
        return delegate().getClassificationCondition();
    }

    @Override
    @NotNull
    default Collection<ResourceObjectDefinition> getAuxiliaryDefinitions() {
        return delegate().getAuxiliaryDefinitions();
    }

    @Override
    default ResourceAttributeContainer instantiate(ItemName elementName) {
        return delegate().instantiate(elementName);
    }
}
