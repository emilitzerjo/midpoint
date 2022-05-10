/*
 * Copyright (c) 2010-2017 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.provisioning.ucf.impl.connid;

import java.io.File;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;

import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.impl.DisplayableValueImpl;
import com.evolveum.midpoint.prism.path.ItemName;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.schema.PrismSchema;
import com.evolveum.midpoint.schema.util.ConnectorTypeUtil;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ConnectorConfigurationType;
import com.evolveum.prism.xml.ns._public.types_3.RawType;

import org.identityconnectors.common.pooling.ObjectPoolConfiguration;
import org.identityconnectors.common.security.GuardedByteArray;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConfigurationProperties;
import org.identityconnectors.framework.api.ConfigurationProperty;
import org.identityconnectors.framework.api.ConnectorInfo;
import org.identityconnectors.framework.api.ResultsHandlerConfiguration;
import org.identityconnectors.framework.api.operations.APIOperation;

import com.evolveum.midpoint.prism.crypto.EncryptionException;
import com.evolveum.midpoint.prism.crypto.Protector;
import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ConnectorType;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;
import com.evolveum.prism.xml.ns._public.types_3.ProtectedByteArrayType;
import com.evolveum.prism.xml.ns._public.types_3.ProtectedStringType;

import org.identityconnectors.framework.common.objects.SuggestedValues;
import org.identityconnectors.framework.common.objects.ValueListOpenness;

/**
 * @author semancik
 */
public class ConnIdConfigurationTransformer {

    private static final Trace LOGGER = TraceManager.getTrace(ConnIdConfigurationTransformer.class);

    private ConnectorType connectorType;
    private ConnectorInfo cinfo;
    private Protector protector;

    public ConnIdConfigurationTransformer(ConnectorType connectorType, ConnectorInfo cinfo, Protector protector) {
        super();
        this.connectorType = connectorType;
        this.cinfo = cinfo;
        this.protector = protector;
    }

    /**
     * Transforms midPoint XML configuration of the connector to the ICF
     * configuration.
     * <p/>
     * The "configuration" part of the XML resource definition will be used.
     * <p/>
     * The provided ICF APIConfiguration will be modified, some values may be
     * overwritten.
     *
     * @throws SchemaException
     * @throws ConfigurationException
     */
    public APIConfiguration transformConnectorConfiguration(PrismContainerValue configuration, boolean isCaching)
            throws SchemaException, ConfigurationException {

        APIConfiguration apiConfig = cinfo.createDefaultAPIConfiguration();
        ConfigurationProperties configProps = apiConfig.getConfigurationProperties();

        // The namespace of all the configuration properties specific to the
        // connector instance will have a connector instance namespace. This
        // namespace can be found in the resource definition.
        String connectorConfNs = connectorType.getNamespace();

        PrismContainer configurationPropertiesContainer = configuration
                .findContainer(SchemaConstants.CONNECTOR_SCHEMA_CONFIGURATION_PROPERTIES_ELEMENT_QNAME);
        if (configurationPropertiesContainer == null) {
            // Also try this. This is an older way.
            configurationPropertiesContainer = configuration.findContainer(new QName(connectorConfNs,
                    SchemaConstants.CONNECTOR_SCHEMA_CONFIGURATION_PROPERTIES_ELEMENT_LOCAL_NAME));
        }

        transformConnectorConfigurationProperties(configProps, configurationPropertiesContainer, connectorConfNs);

        PrismContainer connectorPoolContainer = configuration.findContainer(new QName(
                SchemaConstants.NS_ICF_CONFIGURATION,
                ConnectorFactoryConnIdImpl.CONNECTOR_SCHEMA_CONNECTOR_POOL_CONFIGURATION_XML_ELEMENT_NAME));
        ObjectPoolConfiguration connectorPoolConfiguration = apiConfig.getConnectorPoolConfiguration();
        transformConnectorPoolConfiguration(connectorPoolConfiguration, connectorPoolContainer, isCaching);

        PrismProperty producerBufferSizeProperty = configuration.findProperty(new ItemName(
                SchemaConstants.NS_ICF_CONFIGURATION,
                ConnectorFactoryConnIdImpl.CONNECTOR_SCHEMA_PRODUCER_BUFFER_SIZE_XML_ELEMENT_NAME));
        if (producerBufferSizeProperty != null) {
            apiConfig.setProducerBufferSize(parseInt(producerBufferSizeProperty));
        }

        PrismContainer connectorTimeoutsContainer = configuration.findContainer(new QName(
                SchemaConstants.NS_ICF_CONFIGURATION,
                ConnectorFactoryConnIdImpl.CONNECTOR_SCHEMA_TIMEOUTS_XML_ELEMENT_NAME));
        transformConnectorTimeoutsConfiguration(apiConfig, connectorTimeoutsContainer);

        PrismContainer resultsHandlerConfigurationContainer = configuration.findContainer(new QName(
                SchemaConstants.NS_ICF_CONFIGURATION,
                ConnectorFactoryConnIdImpl.CONNECTOR_SCHEMA_RESULTS_HANDLER_CONFIGURATION_ELEMENT_LOCAL_NAME));
        ResultsHandlerConfiguration resultsHandlerConfiguration = apiConfig.getResultsHandlerConfiguration();
        transformResultsHandlerConfiguration(resultsHandlerConfiguration, resultsHandlerConfigurationContainer);

        return apiConfig;

    }

    public <T> Collection<PrismProperty<T>> transformSuggestedConfiguration(Map<String, SuggestedValues> suggestions)
            throws SchemaException {
        APIConfiguration apiConfig = cinfo.createDefaultAPIConfiguration();
        ConfigurationProperties configProps = apiConfig.getConfigurationProperties();

        // The namespace of all the configuration properties specific to the
        // connector instance will have a connector instance namespace.
        String connectorConfNs = connectorType.getNamespace();
        PrismSchema schema;
        try {
            schema = ConnectorTypeUtil.parseConnectorSchema(connectorType);
        } catch (SchemaException e) {
            throw new SystemException("Couldn't parse connector schema: " + e.getMessage(), e);
        }
        PrismContainerDefinition<ConnectorConfigurationType> connectorConfigDef = ConnectorTypeUtil.findConfigurationContainerDefinition(connectorType, schema);
        if (connectorConfigDef == null) {
            throw new SystemException("Couldn't find container definition of connector configuration in connector: " + connectorType.getConnectorType());
        }

        Collection<PrismProperty<T>> convertedSuggestions = new ArrayList<>();
        for (Map.Entry<String, SuggestedValues> entry : suggestions.entrySet()) {

            String propertyName = entry.getKey();
            SuggestedValues values = entry.getValue();

            if (values == null || values.getValues().isEmpty()) {
                throw new SystemException("Suggestions for configuration property " + propertyName + " is empty");
            }

            ConfigurationProperty configProperty = configProps.getProperty(propertyName);
            if (configProperty == null) {
                LOGGER.debug("Couldn't find configuration property for suggestion with name " + propertyName);
                continue;
            }

            QName qNameOfProperty = new QName(connectorConfNs, propertyName);
            PrismPropertyDefinition<?> propertyDef = connectorConfigDef.findPropertyDefinition(
                    ItemPath.create(SchemaConstants.CONNECTOR_SCHEMA_CONFIGURATION_PROPERTIES_ELEMENT_QNAME, qNameOfProperty));
            if (propertyDef == null) {
                LOGGER.debug("Couldn't find property definition of configuration property for suggestion with name " + propertyName);
                continue;
            }

            PrismContext prismContext = PrismContext.get();
            QName qNameOfType = propertyDef.getTypeName();
            MutableItemDefinition def;
            if (ValueListOpenness.OPEN.equals(values.getOpenness())) {
                def = prismContext.definitionFactory().createPropertyDefinition(qNameOfProperty, qNameOfType);
            } else if (ValueListOpenness.CLOSED.equals(values.getOpenness())) {
                Collection allowedValues = values.getValues().stream()
                        .map((value) -> new DisplayableValueImpl<>(value, null, null)).collect(Collectors.toList());
                def = prismContext.definitionFactory().createPropertyDefinition(qNameOfProperty, qNameOfType, allowedValues, null).toMutable();
            } else {
                LOGGER.debug("Suggestion " + propertyName + " contains unsupported type of ValueListOpenness: " + values.getOpenness());
                continue;
            }

            if (propertyDef.isMandatory()) {
                def.setMinOccurs(1);
            } else {
                def.setMinOccurs(0);
            }

            def.setDynamic(true);
            def.setRuntimeSchema(true);
            def.setDisplayName(propertyDef.getDisplayName());
            def.setHelp(propertyDef.getHelp());
            def.setDisplayOrder(propertyDef.getDisplayOrder());
            def.setDocumentation(propertyDef.getDocumentation());

            if (propertyDef.isMultiValue()) {
                def.setMaxOccurs(-1);
                PrismProperty<T> property = (PrismProperty<T>) def.instantiate();
                for (Object value : values.getValues()) {
                    PrismPropertyValue<T> propertyValue = prismContext.itemFactory().createPropertyValue();
                    propertyValue.setValue((T) value);
                    property.add(propertyValue);
                }
                convertedSuggestions.add(property);
            } else {
                def.setMaxOccurs(1);
                for (Object value : values.getValues()) {
                    PrismProperty<T> property = (PrismProperty<T>) def.instantiate();
                    PrismPropertyValue<T> propertyValue = prismContext.itemFactory().createPropertyValue();
                    propertyValue.setValue((T) value);
                    property.add(propertyValue);
                    convertedSuggestions.add(property);
                }
            }
        }
        return convertedSuggestions;
    }

    private void transformConnectorConfigurationProperties(ConfigurationProperties configProps,
            PrismContainer<?> configurationPropertiesContainer, String connectorConfNs)
            throws ConfigurationException, SchemaException {

        if (configurationPropertiesContainer == null || configurationPropertiesContainer.getValue() == null) {
            throw new SchemaException("No configuration properties container in " + connectorType);
        }

        int numConfingProperties = 0;
        List<QName> wrongNamespaceProperties = new ArrayList<>();

        for (PrismProperty prismProperty : configurationPropertiesContainer.getValue().getProperties()) {
            QName propertyQName = prismProperty.getElementName();

            // All the elements must be in a connector instance
            // namespace.
            if (propertyQName.getNamespaceURI() == null
                    || !propertyQName.getNamespaceURI().equals(connectorConfNs)) {
                LOGGER.warn("Found element with a wrong namespace ({}) in {}",
                        propertyQName.getNamespaceURI(), connectorType);
                wrongNamespaceProperties.add(propertyQName);
            } else {

                numConfingProperties++;

                // Local name of the element is the same as the name
                // of ConnId configuration property
                String propertyName = propertyQName.getLocalPart();
                ConfigurationProperty property = configProps.getProperty(propertyName);

                if (property == null) {
                    throw new ConfigurationException("Unknown configuration property " + propertyName);
                }

                Class<?> type = property.getType();
                if (type.isArray()) {
                    Object[] connIdArray = convertToConnIdArray(prismProperty, type.getComponentType());
                    if (connIdArray != null && connIdArray.length != 0) {
                        property.setValue(connIdArray);
                    }

                } else {
                    Object connIdValue = convertToConnIdSingle(prismProperty, type);
                    if (connIdValue != null) {
                        property.setValue(connIdValue);
                    }
                }
            }
        }
        // empty configuration is OK e.g. when creating a new resource using wizard
        if (numConfingProperties == 0 && !wrongNamespaceProperties.isEmpty()) {
            throw new SchemaException("No configuration properties found. Wrong namespace? (expected: "
                    + connectorConfNs + ", present e.g. " + wrongNamespaceProperties.get(0) + ")");
        }
    }

    private void transformConnectorPoolConfiguration(ObjectPoolConfiguration connectorPoolConfiguration,
            PrismContainer<?> connectorPoolContainer, boolean isCaching) throws SchemaException {

        if (connectorPoolConfiguration != null && connectorPoolContainer != null) {
            for (PrismProperty prismProperty : connectorPoolContainer.getValue().getProperties()) {
                QName propertyQName = prismProperty.getElementName();
                if (propertyQName.getNamespaceURI().equals(SchemaConstants.NS_ICF_CONFIGURATION)) {
                    String subelementName = propertyQName.getLocalPart();
                    if (ConnectorFactoryConnIdImpl.CONNECTOR_SCHEMA_CONNECTOR_POOL_CONFIGURATION_MIN_EVICTABLE_IDLE_TIME_MILLIS
                            .equals(subelementName)) {
                        connectorPoolConfiguration.setMinEvictableIdleTimeMillis(parseLong(prismProperty));
                    } else if (ConnectorFactoryConnIdImpl.CONNECTOR_SCHEMA_CONNECTOR_POOL_CONFIGURATION_MIN_IDLE
                            .equals(subelementName)) {
                        connectorPoolConfiguration.setMinIdle(parseInt(prismProperty));
                    } else if (ConnectorFactoryConnIdImpl.CONNECTOR_SCHEMA_CONNECTOR_POOL_CONFIGURATION_MAX_IDLE
                            .equals(subelementName)) {
                        connectorPoolConfiguration.setMaxIdle(parseInt(prismProperty));
                    } else if (ConnectorFactoryConnIdImpl.CONNECTOR_SCHEMA_CONNECTOR_POOL_CONFIGURATION_MAX_OBJECTS
                            .equals(subelementName)) {
                        connectorPoolConfiguration.setMaxObjects(parseInt(prismProperty));
                    } else if (ConnectorFactoryConnIdImpl.CONNECTOR_SCHEMA_CONNECTOR_POOL_CONFIGURATION_MAX_WAIT
                            .equals(subelementName)) {
                        connectorPoolConfiguration.setMaxWait(parseLong(prismProperty));
                    } else if (ConnectorFactoryConnIdImpl.CONNECTOR_SCHEMA_CONNECTOR_POOL_CONFIGURATION_MAX_IDLE_TIME_MILLIS
                            .equals(subelementName)) {
                        connectorPoolConfiguration.setMaxIdleTimeMillis(parseLong(prismProperty));
                    } else {
                        throw new SchemaException(
                                "Unexpected element "
                                        + propertyQName
                                        + " in "
                                        + ConnectorFactoryConnIdImpl.CONNECTOR_SCHEMA_CONNECTOR_POOL_CONFIGURATION_XML_ELEMENT_NAME);
                    }
                } else {
                    throw new SchemaException(
                            "Unexpected element "
                                    + propertyQName
                                    + " in "
                                    + ConnectorFactoryConnIdImpl.CONNECTOR_SCHEMA_CONNECTOR_POOL_CONFIGURATION_XML_ELEMENT_NAME);
                }
            }
        }
        if (!isCaching) {
            connectorPoolConfiguration.setMinIdle(0);
        }
    }

    private void transformConnectorTimeoutsConfiguration(APIConfiguration apiConfig,
            PrismContainer<?> connectorTimeoutsContainer) throws SchemaException {

        if (connectorTimeoutsContainer == null || connectorTimeoutsContainer.getValue() == null) {
            return;
        }

        for (PrismProperty prismProperty : connectorTimeoutsContainer.getValue().getProperties()) {
            QName propertQName = prismProperty.getElementName();

            if (SchemaConstants.NS_ICF_CONFIGURATION.equals(propertQName.getNamespaceURI())) {
                String opName = propertQName.getLocalPart();
                Collection<Class<? extends APIOperation>> apiOpClasses = ConnectorFactoryConnIdImpl.resolveApiOpClass(opName);
                if (apiOpClasses != null) {
                    for (Class<? extends APIOperation> apiOpClass : apiOpClasses) {
                        apiConfig.setTimeout(apiOpClass, parseInt(prismProperty));
                    }
                } else {
                    throw new SchemaException("Unknown operation name " + opName + " in "
                            + ConnectorFactoryConnIdImpl.CONNECTOR_SCHEMA_TIMEOUTS_XML_ELEMENT_NAME);
                }
            }
        }
    }

    private void transformResultsHandlerConfiguration(ResultsHandlerConfiguration resultsHandlerConfiguration,
            PrismContainer<?> resultsHandlerConfigurationContainer) throws SchemaException {

        if (resultsHandlerConfigurationContainer == null || resultsHandlerConfigurationContainer.getValue() == null) {
            return;
        }

        for (PrismProperty prismProperty : resultsHandlerConfigurationContainer.getValue().getProperties()) {
            QName propertyQName = prismProperty.getElementName();
            if (propertyQName.getNamespaceURI().equals(SchemaConstants.NS_ICF_CONFIGURATION)) {
                String subelementName = propertyQName.getLocalPart();
                if (ConnectorFactoryConnIdImpl.CONNECTOR_SCHEMA_RESULTS_HANDLER_CONFIGURATION_ENABLE_NORMALIZING_RESULTS_HANDLER
                        .equals(subelementName)) {
                    resultsHandlerConfiguration.setEnableNormalizingResultsHandler(parseBoolean(prismProperty));
                } else if (ConnectorFactoryConnIdImpl.CONNECTOR_SCHEMA_RESULTS_HANDLER_CONFIGURATION_ENABLE_FILTERED_RESULTS_HANDLER
                        .equals(subelementName)) {
                    resultsHandlerConfiguration.setEnableFilteredResultsHandler(parseBoolean(prismProperty));
                } else if (ConnectorFactoryConnIdImpl.CONNECTOR_SCHEMA_RESULTS_HANDLER_CONFIGURATION_FILTERED_RESULTS_HANDLER_IN_VALIDATION_MODE
                        .equals(subelementName)) {
                    resultsHandlerConfiguration.setFilteredResultsHandlerInValidationMode(parseBoolean(prismProperty));
                } else if (ConnectorFactoryConnIdImpl.CONNECTOR_SCHEMA_RESULTS_HANDLER_CONFIGURATION_ENABLE_CASE_INSENSITIVE_HANDLER
                        .equals(subelementName)) {
                    resultsHandlerConfiguration.setEnableCaseInsensitiveFilter(parseBoolean(prismProperty));
                } else if (ConnectorFactoryConnIdImpl.CONNECTOR_SCHEMA_RESULTS_HANDLER_CONFIGURATION_ENABLE_ATTRIBUTES_TO_GET_SEARCH_RESULTS_HANDLER
                        .equals(subelementName)) {
                    resultsHandlerConfiguration.setEnableAttributesToGetSearchResultsHandler(parseBoolean(prismProperty));
                } else {
                    throw new SchemaException(
                            "Unexpected element "
                                    + propertyQName
                                    + " in "
                                    + ConnectorFactoryConnIdImpl.CONNECTOR_SCHEMA_RESULTS_HANDLER_CONFIGURATION_ELEMENT_LOCAL_NAME);
                }
            } else {
                throw new SchemaException(
                        "Unexpected element "
                                + propertyQName
                                + " in "
                                + ConnectorFactoryConnIdImpl.CONNECTOR_SCHEMA_RESULTS_HANDLER_CONFIGURATION_ELEMENT_LOCAL_NAME);
            }
        }
    }

    private int parseInt(PrismProperty<?> prop) {
        return prop.getRealValue(Integer.class);
    }

    private long parseLong(PrismProperty<?> prop) {
        Object realValue = prop.getRealValue();
        if (realValue instanceof Long) {
            return (Long) realValue;
        } else if (realValue instanceof Integer) {
            return ((Integer) realValue);
        } else {
            throw new IllegalArgumentException("Cannot convert " + realValue.getClass() + " to long");
        }
    }

    private boolean parseBoolean(PrismProperty<?> prop) {
        return prop.getRealValue(Boolean.class);
    }

    private Object convertToConnIdSingle(PrismProperty<?> configProperty, Class<?> expectedType)
            throws ConfigurationException {
        if (configProperty == null) {
            return null;
        }
        PrismPropertyValue<?> pval = configProperty.getValue();
        return convertToConnId(pval, expectedType);
    }

    private Object[] convertToConnIdArray(PrismProperty prismProperty, Class<?> componentType) throws ConfigurationException, SchemaException {
        List<PrismPropertyValue> values = prismProperty.getValues();
        Object valuesArrary = Array.newInstance(componentType, values.size());
        for (int j = 0; j < values.size(); ++j) {
            Object icfValue = convertToConnId(values.get(j), componentType);
            if (icfValue != null && icfValue instanceof RawType) {
                throw new SchemaException("Cannot convert value of " + prismProperty.getElementName().getLocalPart() + " because it is still raw. Missing definition in connector schema?");
            }
            Array.set(valuesArrary, j, icfValue);
        }
        return (Object[]) valuesArrary;
    }

    private Object convertToConnId(PrismPropertyValue<?> pval, Class<?> expectedType) throws ConfigurationException {
        Object midPointRealValue = pval.getValue();
        if (expectedType.equals(GuardedString.class)) {
            // Guarded string is a special ICF beast
            // The value must be ProtectedStringType
            if (midPointRealValue instanceof ProtectedStringType) {
                ProtectedStringType ps = (ProtectedStringType) pval.getValue();
                return ConnIdUtil.toGuardedString(ps, pval.getParent().getElementName().getLocalPart(), protector);
            } else {
                throw new ConfigurationException(
                        "Expected protected string as value of configuration property "
                                + pval.getParent().getElementName().getLocalPart() + " but got "
                                + midPointRealValue.getClass());
            }

        } else if (expectedType.equals(GuardedByteArray.class)) {
            // Guarded string is a special ICF beast
            // TODO
//            return new GuardedByteArray(Base64.decodeBase64((ProtectedByteArrayType) pval.getValue()));
            return new GuardedByteArray(((ProtectedByteArrayType) pval.getValue()).getClearBytes());
        } else if (midPointRealValue instanceof PolyString) {
            return ((PolyString) midPointRealValue).getOrig();
        } else if (midPointRealValue instanceof PolyStringType) {
            return ((PolyStringType) midPointRealValue).getOrig();
        } else if (expectedType.equals(File.class) && midPointRealValue instanceof String) {
            return new File((String) midPointRealValue);
        } else if (expectedType.equals(String.class) && midPointRealValue instanceof ProtectedStringType) {
            try {
                return protector.decryptString((ProtectedStringType) midPointRealValue);
            } catch (EncryptionException e) {
                throw new ConfigurationException(e);
            }
        } else {
            // Cannot really make this simple check because of boxed types (boolean vs Boolear).
//            if (midPointRealValue != null && !expectedType.isAssignableFrom(midPointRealValue.getClass())) {
//                throw new IllegalArgumentException("Type mismatch for "+pval+", expected "+expectedType+", got "+midPointRealValue.getClass());
//            }
            return midPointRealValue;
        }
    }
}
