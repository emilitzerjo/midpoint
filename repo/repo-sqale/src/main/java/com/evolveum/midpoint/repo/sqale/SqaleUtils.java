/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale;

import java.lang.reflect.Field;
import javax.xml.namespace.QName;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismReferenceDefinition;
import com.evolveum.midpoint.prism.Referencable;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectReferenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;

public class SqaleUtils {

    /**
     * Returns version from midPoint object as a number.
     *
     * @throws IllegalArgumentException if the version is null or non-number
     */
    public static int objectVersionAsInt(ObjectType schemaObject) {
        String version = schemaObject.getVersion();
        try {
            return Integer.parseInt(version);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Version must be a number: " + version);
        }
    }

    /**
     * Returns version from prism object as a number.
     *
     * @throws IllegalArgumentException if the version is null or non-number
     */
    public static int objectVersionAsInt(PrismObject<?> prismObject) {
        String version = prismObject.getVersion();
        try {
            return Integer.parseInt(version);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Version must be a number: " + version);
        }
    }

    /** Parametrized type friendly version of {@link Object#getClass()}. */
    public static <S> Class<S> getClass(S object) {
        //noinspection unchecked
        return (Class<S>) object.getClass();
    }

    /**
     * Fixes reference type if `null` and tries to use default from definition.
     * Use returned value.
     */
    public static Referencable referenceWithTypeFixed(Referencable value) {
        if (value.getType() != null) {
            return value;
        }

        PrismReferenceDefinition def = value.asReferenceValue().getDefinition();
        QName defaultType = def.getTargetTypeName();
        if (defaultType == null) {
            throw new IllegalArgumentException("Can't modify reference with no target type"
                    + " specified and no default type in the definition. Value: " + value
                    + " Definition: " + def);
        }
        value = new ObjectReferenceType()
                .oid(value.getOid())
                .type(defaultType)
                .relation(value.getRelation());
        return value;
    }

    public static String toString(Object object) {
        return new ToStringUtil(object).toString();
    }

    private static class ToStringUtil extends ReflectionToStringBuilder {

        @SuppressWarnings("DoubleBraceInitialization")
        private static final ToStringStyle STYLE = new ToStringStyle() {{
            setFieldSeparator(", ");
            setUseShortClassName(true);
            setUseIdentityHashCode(false);
        }};

        private ToStringUtil(Object object) {
            super(object, STYLE);
        }

        @Override
        protected boolean accept(Field field) {
            try {
                return super.accept(field) && field.get(getObject()) != null;
            } catch (IllegalAccessException e) {
                return super.accept(field);
            }
        }
    }
}
