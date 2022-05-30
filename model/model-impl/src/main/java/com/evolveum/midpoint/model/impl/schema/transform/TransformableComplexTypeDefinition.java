/*
 * Copyright (c) 2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.model.impl.schema.transform;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import javax.xml.namespace.QName;

import com.evolveum.midpoint.prism.*;

import com.evolveum.midpoint.prism.path.ItemName;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.schema.processor.*;
import com.evolveum.midpoint.schema.processor.deleg.CompositeObjectDefinitionDelegator;
import com.evolveum.midpoint.schema.processor.deleg.ResourceObjectClassDefinitionDelegator;
import com.evolveum.midpoint.schema.processor.deleg.ResourceObjectDefinitionDelegator;

import com.evolveum.midpoint.schema.processor.deleg.ResourceObjectTypeDefinitionDelegator;
import com.evolveum.midpoint.util.exception.SchemaException;

import com.evolveum.midpoint.xml.ns._public.common.common_3.CorrelationDefinitionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ExpressionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.LayerType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowKindType;
import com.evolveum.midpoint.xml.ns._public.resource.capabilities_3.CapabilityType;

import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.prism.deleg.ComplexTypeDefinitionDelegator;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.path.ItemPathCollectionsUtil;

import org.jetbrains.annotations.Nullable;

public class TransformableComplexTypeDefinition implements ComplexTypeDefinitionDelegator, PartiallyMutableComplexTypeDefinition {


    private static final long serialVersionUID = 1L;
    private static final TransformableItemDefinition REMOVED = new Removed();
    private final Map<QName,ItemDefinition<?>> overrides = new HashMap<>();

    protected DelegatedItem<ComplexTypeDefinition> delegate;
    private transient List<ItemDefinition<?>> definitionsCache;

    public TransformableComplexTypeDefinition(ComplexTypeDefinition delegate) {
        var schemaDef = PrismContext.get().getSchemaRegistry().findComplexTypeDefinitionByType(delegate.getTypeName());
        if (schemaDef == delegate) {
            this.delegate = new DelegatedItem.StaticComplexType(delegate);
        } else {
            this.delegate = new DelegatedItem.FullySerializable<>(delegate);
        }
    }

    @Override
    public ComplexTypeDefinition delegate() {
        return delegate.get();
    }

    public static TransformableComplexTypeDefinition from(ComplexTypeDefinition complexTypeDefinition) {
        if (complexTypeDefinition instanceof ResourceObjectDefinition) {
            if (complexTypeDefinition instanceof ResourceObjectTypeDefinition) {
                return new TrResourceObjectTypeDefinition((ResourceObjectTypeDefinition) complexTypeDefinition);
            }
            if (complexTypeDefinition instanceof ResourceObjectClassDefinition) {
                return new TrResourceObjectClassDefinition(((ResourceObjectClassDefinition) complexTypeDefinition));
            }
            if (complexTypeDefinition instanceof CompositeObjectDefinition) {
                return new TrCompositeObjectDefinition((CompositeObjectDefinition) complexTypeDefinition);
            }
            throw new IllegalStateException("Unsupported type of object definition: " + complexTypeDefinition.getClass());
        }
        if (complexTypeDefinition != null) {
            return new TransformableComplexTypeDefinition(complexTypeDefinition);
        }
        return null;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public <ID extends ItemDefinition<?>> ID findLocalItemDefinition(@NotNull QName name) {
        return overriden(ComplexTypeDefinitionDelegator.super.findLocalItemDefinition(name));
    }

    @SuppressWarnings("unchecked")
    private <ID extends ItemDefinition<?>> ID overriden(ID originalItem) {
        if (originalItem == null) {
            return null;
        }
        ItemDefinition<?> overriden = overrides.computeIfAbsent(originalItem.getItemName(), k -> TransformableItemDefinition.from(originalItem).attachTo(this));
        if (overriden instanceof Removed) {
            return null;
        }
        TransformableItemDefinition.apply(overriden, originalItem);
        return (ID) overriden;
    }

    @Override
    public <ID extends ItemDefinition<?>> ID findLocalItemDefinition(@NotNull QName name, @NotNull Class<ID> clazz,
            boolean caseInsensitive) {
        return overriden(ComplexTypeDefinitionDelegator.super.findLocalItemDefinition(name, clazz, caseInsensitive));
    }

    @SuppressWarnings("rawtypes")
    @Override
    public <ID extends ItemDefinition<?>> ID findItemDefinition(@NotNull ItemPath path, @NotNull Class<ID> clazz) {
        // FIXME: Implement proper
        var firstChild = overriden(ComplexTypeDefinitionDelegator.super.findItemDefinition(path));
        if (firstChild == null) {
            return null;
        }
        var rest = path.rest();
        if (rest.isEmpty()) {
            return clazz.cast(firstChild);
        }
        return firstChild.findItemDefinition(path, clazz);

    }

    @Override
    public <ID extends ItemDefinition<?>> ID findItemDefinition(@NotNull ItemPath path) {
        //noinspection unchecked
        return (ID) findItemDefinition(path, ItemDefinition.class);
    }

    @SuppressWarnings("rawtypes")
    public <ID extends ItemDefinition> ID findNamedItemDefinition(@NotNull QName firstName, @NotNull ItemPath rest,
            @NotNull Class<ID> clazz) {

        ItemDefinition<?> itemDef = findLocalItemDefinition(firstName);
        if (itemDef != null) {
            // FIXME: Is this correct?
            return itemDef.findItemDefinition(rest, clazz);
        }
        return null;
    }

    @Override
    public @NotNull List<? extends ItemDefinition<?>> getDefinitions() {

        if (definitionsCache == null) {
            List<ItemDefinition<?>> ret = new ArrayList<>();
            for (ItemDefinition<?> originalItem : ComplexTypeDefinitionDelegator.super.getDefinitions()) {
                ItemDefinition<?> wrapped = overriden(originalItem);
                if (wrapped != null) {
                    ret.add(wrapped);
                }
            }
            definitionsCache = ret;
        }
        return definitionsCache;
    }

    @Override
    public boolean isEmpty() {
        return getDefinitions().isEmpty();
    }

    @Override
    public Optional<ItemDefinition<?>> substitution(QName name) {
        Optional<ItemDefinition<?>> original = ComplexTypeDefinitionDelegator.super.substitution(name);
        if (original.isPresent()) {
            return Optional.of(overriden(original.get()));
        }
        return Optional.empty();
    }

    @Override
    public Optional<ItemDefinition<?>> itemOrSubstitution(QName name) {
        Optional<ItemDefinition<?>> original = ComplexTypeDefinitionDelegator.super.itemOrSubstitution(name);
        if (original.isPresent()) {
            return Optional.of(overriden(original.get()));
        }
        return Optional.empty();
    }

    @Override
    public void revive(PrismContext prismContext) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public @NotNull ComplexTypeDefinition clone() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isImmutable() {
        return false;
    }

    @Override
    public void freeze() {
        // NOOP for now
    }

    @Override
    public @NotNull ComplexTypeDefinition deepClone(
            @NotNull DeepCloneOperation operation) {
        return operation.execute(
                this,
                this::copy,
                copy -> {
                    for (Entry<QName, ItemDefinition<?>> entry : overrides.entrySet()) {
                        ItemDefinition<?> item = entry.getValue().deepClone(operation);
                        ((TransformableComplexTypeDefinition) copy).overrides.put(entry.getKey(), item);
                        // TODO what about "post action" ?
                    }
                });
    }

    @Override
    public MutableComplexTypeDefinition toMutable() {
        return this;
    }

    /**
     *
     * Currently used only to replace Refined* with LayerRefined*
     *
     * @param name
     * @param definition
     */
    @SuppressWarnings("rawtypes")
    @Override
    public void replaceDefinition(@NotNull QName name, ItemDefinition definition) {
        overrides.put(name, definition);
    }

    @Override
    public void delete(QName itemName) {
        ItemDefinition<?> existing = findLocalItemDefinition(itemName);
        if (existing != null) {
            definitionsCache = null;
            overrides.put(existing.getItemName(), REMOVED);
        }
    }

    public TransformableComplexTypeDefinition copy() {
        TransformableComplexTypeDefinition copy = new TransformableComplexTypeDefinition(delegate());
        copy.overrides.putAll(overrides);
        return copy;
    }

    @Override
    public void trimTo(@NotNull Collection<ItemPath> paths) {
        for (ItemDefinition<?> itemDef : getDefinitions()) {
            ItemPath itemPath = itemDef.getItemName();
            if (!ItemPathCollectionsUtil.containsSuperpathOrEquivalent(paths, itemPath)) {
                delete(itemDef.getItemName());
            } else if (itemDef instanceof PrismContainerDefinition) {
                PrismContainerDefinition<?> itemPcd = (PrismContainerDefinition<?>) itemDef;
                if (itemPcd.getComplexTypeDefinition() != null) {
                    itemPcd.getComplexTypeDefinition().trimTo(ItemPathCollectionsUtil.remainder(paths, itemPath, false));
                }
            }
        }
    }

    public abstract static class TrResourceObjectDefinition extends TransformableComplexTypeDefinition
            implements ResourceObjectDefinitionDelegator {

        private static final long serialVersionUID = 1L;

        TrResourceObjectDefinition(ComplexTypeDefinition delegate) {
            super(delegate);
        }

        @Override
        public ResourceObjectDefinition delegate() {
            return (ResourceObjectDefinition) super.delegate();
        }

        @Override
        public abstract @NotNull TrResourceObjectDefinition clone();

        @Override
        public @NotNull ResourceObjectClassDefinition deepClone(@NotNull DeepCloneOperation operation) {
            return (ResourceObjectClassDefinition) super.deepClone(operation);
        }

        @Override
        public ResourceAttributeContainer instantiate(ItemName elementName) {
            return ResourceObjectDefinitionDelegator.super.instantiate(elementName);
        }
    }

    public static class TrResourceObjectClassDefinition extends TrResourceObjectDefinition
            implements ResourceObjectClassDefinitionDelegator, PartiallyMutableComplexTypeDefinition.ObjectClassDefinition {

        TrResourceObjectClassDefinition(ResourceObjectClassDefinition delegate) {
            super(delegate);
        }

        @Override
        public ResourceObjectClassDefinition delegate() {
            return (ResourceObjectClassDefinition) super.delegate();
        }

        @Override
        public @NotNull TrResourceObjectClassDefinition clone() {
            return copy();
        }

        @Override
        public TrResourceObjectClassDefinition copy() {
            return new TrResourceObjectClassDefinition(this); // TODO or delegate() instead of this?
        }

        @Override
        public MutableResourceObjectClassDefinition toMutable() {
            return this;
        }

    }

    public static class TrResourceObjectTypeDefinition extends TrResourceObjectDefinition
            implements ResourceObjectTypeDefinitionDelegator {

        TrResourceObjectTypeDefinition(ResourceObjectTypeDefinition delegate) {
            super(delegate);
        }

        @Override
        public ResourceObjectTypeDefinition delegate() {
            return (ResourceObjectTypeDefinition) super.delegate();
        }

        @Override
        public @NotNull TrResourceObjectTypeDefinition clone() {
            return copy();
        }

        @Override
        public TrResourceObjectTypeDefinition copy() {
            return new TrResourceObjectTypeDefinition(this); // TODO or delegate() instead of this?
        }
    }

    public static class TrCompositeObjectDefinition extends TrResourceObjectDefinition
            implements CompositeObjectDefinitionDelegator {

        TrCompositeObjectDefinition(CompositeObjectDefinition delegate) {
            super(delegate);
        }

        @Override
        public CompositeObjectDefinition delegate() {
            return (CompositeObjectDefinition) super.delegate();
        }

        @Override
        public @NotNull TrCompositeObjectDefinition clone() {
            return copy();
        }

        @Override
        public TrCompositeObjectDefinition copy() {
            return new TrCompositeObjectDefinition(this); // TODO or delegate() instead of this?
        }
    }

    @SuppressWarnings("rawtypes")
    private static class Removed extends TransformableItemDefinition {

        private static final long serialVersionUID = 1L;

        @Override
        public ItemDefinition delegate() {
            return null;
        }

        protected Removed() {
            super(null);
        }

        @Override
        protected ItemDefinition publicView() {
            return null;
        }


        @Override
        public String toString() {
            return "REMOVED";
        }

        @Override
        protected TransformableItemDefinition copy() {
            return this;
        }

        // TODO why is this needed?
        @Override
        public boolean canBeDefinitionOf(Item item) {
            return false;
        }
    }

}
