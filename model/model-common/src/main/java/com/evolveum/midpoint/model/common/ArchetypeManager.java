/*
 * Copyright (C) 2019-2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.model.common;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.xml.namespace.QName;

import com.evolveum.midpoint.CacheInvalidationContext;
import com.evolveum.midpoint.repo.api.CacheRegistry;
import com.evolveum.midpoint.repo.api.Cache;

import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.expression.ExpressionProfile;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.FocusTypeUtil;
import com.evolveum.midpoint.util.QNameUtil;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.prism.xml.ns._public.types_3.ItemPathType;

/**
 * Component that can efficiently determine archetypes for objects.
 * It is backed by caches, therefore this is supposed to be a low-overhead service that can be
 * used in many places.
 *
 * @author Radovan Semancik
 */
@Component
public class ArchetypeManager implements Cache {

    private static final Trace LOGGER = TraceManager.getTrace(ArchetypeManager.class);
    private static final Trace LOGGER_CONTENT = TraceManager.getTrace(ArchetypeManager.class.getName() + ".content");

    /**
     * Cache invalidation is invoked when an object of any of these classes is modified.
     */
    private static final Collection<Class<?>> INVALIDATION_RELATED_CLASSES = Arrays.asList(
            ArchetypeType.class,
            SystemConfigurationType.class,
            ObjectTemplateType.class
    );

    @Autowired private SystemObjectCache systemObjectCache;
    @Autowired private PrismContext prismContext;
    @Autowired private CacheRegistry cacheRegistry;

    private final Map<String, ArchetypePolicyType> archetypePolicyCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void register() {
        cacheRegistry.registerCache(this);
    }

    @PreDestroy
    public void unregister() {
        cacheRegistry.unregisterCache(this);
    }

    public PrismObject<ArchetypeType> getArchetype(String oid, OperationResult result) throws ObjectNotFoundException, SchemaException {
        // TODO: make this efficient (use cache)
        return systemObjectCache.getArchetype(oid, result);
    }

    public <O extends AssignmentHolderType> ObjectReferenceType determineArchetypeRef(PrismObject<O> assignmentHolder) throws SchemaException {
        if (assignmentHolder == null) {
            return null;
        }
        if (!assignmentHolder.canRepresent(AssignmentHolderType.class)) {
            return null;
        }

        List<ObjectReferenceType> archetypeAssignmentsRef = determineArchetypesFromAssignments(assignmentHolder.asObjectable());

        if (CollectionUtils.isNotEmpty(archetypeAssignmentsRef)) {
            if (archetypeAssignmentsRef.size() > 1) {
                throw new SchemaException("Only a single archetype for an object is supported: "+assignmentHolder);
            }
        }

        List<ObjectReferenceType> archetypeRefs = assignmentHolder.asObjectable().getArchetypeRef();
        if (CollectionUtils.isEmpty(archetypeRefs)) {
            if (CollectionUtils.isEmpty(archetypeAssignmentsRef)) {
                return null;
            }
            return archetypeAssignmentsRef.get(0);
        }
        if (archetypeRefs.size() > 1) {
            throw new SchemaException("Only a single archetype for an object is supported: "+assignmentHolder);
        }

        //check also assignments

        return archetypeRefs.get(0);
    }

    private <O extends AssignmentHolderType> List<ObjectReferenceType> determineArchetypesFromAssignments(O assignmentHolder) {
        List<AssignmentType> assignments = assignmentHolder.getAssignment();
        return assignments.stream()
                .filter(a -> {
                    ObjectReferenceType target = a.getTargetRef();
                    return target != null && QNameUtil.match(ArchetypeType.COMPLEX_TYPE, target.getType());
                })
                .map(AssignmentType::getTargetRef)
                .collect(Collectors.toList());
    }

    public <O extends AssignmentHolderType> PrismObject<ArchetypeType> determineArchetype(PrismObject<O> assignmentHolder, OperationResult result) throws SchemaException {
        return determineArchetype(assignmentHolder, null, result);
    }

    public <O extends AssignmentHolderType> PrismObject<ArchetypeType> determineArchetype(PrismObject<O> assignmentHolder, String explicitArchetypeOid, OperationResult result) throws SchemaException {
        String archetypeOid;
        if (explicitArchetypeOid != null) {
            archetypeOid = explicitArchetypeOid;
        } else {
            ObjectReferenceType archetypeRef = determineArchetypeRef(assignmentHolder);
            if (archetypeRef == null) {
                return null;
            }
            archetypeOid = archetypeRef.getOid();
        }

        PrismObject<ArchetypeType> archetype;
        try {
            archetype = systemObjectCache.getArchetype(archetypeOid, result);
        } catch (ObjectNotFoundException e) {
            LOGGER.warn("Archetype {} for object {} cannot be found", archetypeOid, assignmentHolder);
            return null;
        }
        return archetype;
    }

    public <O extends ObjectType> ArchetypePolicyType determineArchetypePolicy(PrismObject<O> object, OperationResult result) throws SchemaException, ConfigurationException {
        return determineArchetypePolicy(object, null, result);
    }

    public <O extends ObjectType> ArchetypePolicyType determineArchetypePolicy(PrismObject<O> object, String explicitArchetypeOid, OperationResult result) throws SchemaException, ConfigurationException {
        if (object == null) {
            return null;
        }

        PrismObject<ArchetypeType> archetype;
        if (object.canRepresent(AssignmentHolderType.class)) {
            //noinspection unchecked
            archetype = determineArchetype((PrismObject<? extends AssignmentHolderType>) object, explicitArchetypeOid, result);
        } else {
            archetype = null;
        }

        if (archetype != null) {
            ArchetypePolicyType cachedArchetypePolicy = archetypePolicyCache.get(archetype.getOid());
            if (cachedArchetypePolicy != null) {
                return cachedArchetypePolicy;
            }
        }

        ArchetypePolicyType archetypePolicy = mergeArchetypePolicies(archetype, result);

        // Try to find appropriate system configuration section for this object.
        ObjectPolicyConfigurationType objectPolicy = determineObjectPolicyConfiguration(object, result);
        // TODO: cache the result of the merge
        ArchetypePolicyType mergedPolicy = merge(archetypePolicy, objectPolicy);
        if (archetype != null && mergedPolicy != null) {
            archetypePolicyCache.put(archetype.getOid(), mergedPolicy);
        }
        return mergedPolicy;
    }

    public ArchetypePolicyType mergeArchetypePolicies(PrismObject<ArchetypeType> archetype, OperationResult result) throws SchemaException {
        if (archetype == null) {
            return null;
        }

        return mergeArchetypePolicies(archetype.asObjectable(), result);
    }

    private ArchetypePolicyType mergeArchetypePolicies(ArchetypeType archetypeType, OperationResult result) throws SchemaException {
        ObjectReferenceType superArchetypeRef = archetypeType.getSuperArchetypeRef();
        if (superArchetypeRef == null || superArchetypeRef.getOid() == null) {
            return archetypeType.getArchetypePolicy();
        }

        PrismObject<ArchetypeType> superArchetype;
        try {
            superArchetype = systemObjectCache.getArchetype(superArchetypeRef.getOid(), result);
        } catch (ObjectNotFoundException e) {
            LOGGER.warn("Archetype {} cannot be found.", superArchetypeRef);
            return archetypeType.getArchetypePolicy();
        }

        ArchetypePolicyType superPolicy = mergeArchetypePolicies(superArchetype.asObjectable(), result);
        ArchetypePolicyType currentPolicy = archetypeType.getArchetypePolicy();

        if (currentPolicy == null) {
            if (superPolicy == null) {
                return null;
            }
            return superPolicy.clone();
        }

        if (superPolicy == null) {
            return currentPolicy.clone();
        }

        ArchetypePolicyType mergedPolicy = currentPolicy.clone();

        ArchetypeAdminGuiConfigurationType mergedAdminGuiConfig = mergeAdminGuiConfig(currentPolicy, superPolicy);
        mergedPolicy.setAdminGuiConfiguration(mergedAdminGuiConfig);

        ApplicablePoliciesType mergedApplicablePolicies = mergeApplicablePolicies(currentPolicy, superPolicy);
        mergedPolicy.setApplicablePolicies(mergedApplicablePolicies);

        AssignmentRelationApproachType mergedRelationApproach = mergeRelationApproach(currentPolicy, superPolicy);
        mergedPolicy.setAssignmentHolderRelationApproach(mergedRelationApproach);

        ConflictResolutionType mergedConflictResolutionType = mergeConflictResolution(currentPolicy, superPolicy);
        mergedPolicy.setConflictResolution(mergedConflictResolutionType);

        DisplayType mergedDisplayType = mergeDisplayType(currentPolicy.getDisplay(), superPolicy.getDisplay());
        mergedPolicy.setDisplay(mergedDisplayType);


        if (currentPolicy.getExpressionProfile() == null) {
            mergedPolicy.setExpressionProfile(superPolicy.getExpressionProfile());
        }

        List<ItemConstraintType> itemConstraints = mergeItemConstraints(currentPolicy.getItemConstraint(), superPolicy.getItemConstraint());
        mergedPolicy.getItemConstraint().clear();
        mergedPolicy.getItemConstraint().addAll(itemConstraints);

        LifecycleStateModelType mergedLifecycleStateModel = mergeLifecycleStateModel(currentPolicy.getLifecycleStateModel(), superPolicy.getLifecycleStateModel());
        mergedPolicy.setLifecycleStateModel(mergedLifecycleStateModel);


        //Experimental
        if (currentPolicy.getLinks() == null) {
            mergedPolicy.setLinks(superPolicy.getLinks());
        }

        if (currentPolicy.getObjectTemplateRef() == null) {
            mergedPolicy.setObjectTemplateRef(superPolicy.getObjectTemplateRef());
        }

        //DEPRECATED
        List<ItemConstraintType> propertyConstraints = mergeItemConstraints(currentPolicy.getPropertyConstraint(), superPolicy.getPropertyConstraint());
        mergedPolicy.getPropertyConstraint().clear();
        mergedPolicy.getPropertyConstraint().addAll(propertyConstraints);

        return mergedPolicy;
    }

    private ArchetypeAdminGuiConfigurationType mergeAdminGuiConfig(ArchetypePolicyType currentPolicy, ArchetypePolicyType superPolicy) {
        ArchetypeAdminGuiConfigurationType currentAdminGuiConfig = currentPolicy.getAdminGuiConfiguration();
        ArchetypeAdminGuiConfigurationType superAdminGuiConfig = superPolicy.getAdminGuiConfiguration();
        if (currentAdminGuiConfig == null) {
            return superAdminGuiConfig;
        }

        if (superAdminGuiConfig == null) {
            return currentAdminGuiConfig;
        }

        ArchetypeAdminGuiConfigurationType mergedAdminGuiConfig = new ArchetypeAdminGuiConfigurationType(prismContext);
        GuiObjectDetailsPageType mergedObjectDetails = mergeObjectDetails(currentAdminGuiConfig, superAdminGuiConfig);
        mergedAdminGuiConfig.setObjectDetails(mergedObjectDetails);

        return mergedAdminGuiConfig;
    }

    private GuiObjectDetailsPageType mergeObjectDetails(ArchetypeAdminGuiConfigurationType currentAdminGuiConfig, ArchetypeAdminGuiConfigurationType superAdminGuiConfig) {
        GuiObjectDetailsPageType currentObjectDetails = currentAdminGuiConfig.getObjectDetails();
        GuiObjectDetailsPageType superObjectDetails = superAdminGuiConfig.getObjectDetails();
        if (currentObjectDetails == null) {
            if (superObjectDetails == null) {
                return null;
            }
            return superObjectDetails.clone();
        }

        if (superObjectDetails == null) {
            return currentObjectDetails.clone();
        }

        GuiObjectDetailsPageType mergedObjectDetails = currentObjectDetails.clone();
        List<VirtualContainersSpecificationType> mergedVirtualContainers = mergeVirtualContainers(currentObjectDetails, superObjectDetails);
        mergedObjectDetails.getContainer().clear();
        mergedObjectDetails.getContainer().addAll(mergedVirtualContainers);
        //TODO save method, objectForm, relations

        return mergedObjectDetails;
    }

    private <C extends Containerable> List<C> mergeContainers(List<C> currentContainers, List<C> superContainers, Function<C, Predicate<C>> predicate, BiFunction<C, C, C> mergeFunction) {
        if (currentContainers.isEmpty()) {
            if (superContainers.isEmpty()) {
                return Collections.emptyList();
            }
            return superContainers.stream().map(this::cloneComplex).collect(Collectors.toList());
        }

        if (superContainers.isEmpty()) {
            return currentContainers.stream().map(this::cloneComplex).collect(Collectors.toList());
        }

        List<C> mergedContainers = new ArrayList<>();
        for (C superContainer : superContainers) {
            C matchedContainer = find(predicate.apply(superContainer), currentContainers);
            if (matchedContainer != null) {
                C mergedContainer = mergeFunction.apply(matchedContainer, superContainer);
                mergedContainers.add(mergedContainer);
            } else {
                mergedContainers.add(cloneComplex(superContainer));
            }
        }

        for (C currentContainer : currentContainers) {
            if (!findAny(predicate.apply(currentContainer), mergedContainers)) {
                mergedContainers.add(cloneComplex(currentContainer));
            }
        }

        return mergedContainers;
    }

    private List<VirtualContainersSpecificationType> mergeVirtualContainers(GuiObjectDetailsPageType currentObjectDetails, GuiObjectDetailsPageType superObjectDetails) {
        return mergeContainers(currentObjectDetails.getContainer(), superObjectDetails.getContainer(),
                this::createVirtualContainersPredicate, this::mergeVirtualContainer);
    }


    private Predicate<VirtualContainersSpecificationType> createVirtualContainersPredicate(VirtualContainersSpecificationType superContainer) {
        return c -> identifiersMatch(c.getIdentifier(), superContainer.getIdentifier());
    }

    private <C extends Containerable> C find(Predicate<C> predicate, List<C> currentContainers) {
        List<C> matchedContainers = currentContainers.stream()
                .filter(predicate)
                .collect(Collectors.toList());

        if (CollectionUtils.isEmpty(matchedContainers)) {
            return null;
        }

        if (matchedContainers.size() > 1) {
            throw new IllegalStateException("Cannot merge virtual containers. More containers with same identifier specified.");
        }

        return matchedContainers.iterator().next();
    }

    private <C extends Containerable> boolean findAny(Predicate<C> predicate, List<C> mergedContainers) {
        return mergedContainers.stream().anyMatch(predicate);
    }


    private boolean identifiersMatch(String id1, String id2) {
        return id1 != null && id1.equals(id2);
    }

    private VirtualContainersSpecificationType mergeVirtualContainer(VirtualContainersSpecificationType currentContainer, VirtualContainersSpecificationType superContainer) {
        VirtualContainersSpecificationType mergedContainer = currentContainer.clone();
        if (currentContainer.getDescription() == null) {
            mergedContainer.setDescription(superContainer.getDescription());
        }

        DisplayType mergedDisplayType = mergeDisplayType(currentContainer.getDisplay(), superContainer.getDisplay());
        mergedContainer.setDisplay(mergedDisplayType);

        if (currentContainer.getDisplayOrder() == null) {
            mergedContainer.setDisplayOrder(superContainer.getDisplayOrder());
        }

        if (currentContainer.getVisibility() == null) {
            mergedContainer.setVisibility(superContainer.getVisibility());
        }

        for (VirtualContainerItemSpecificationType virtualItem : superContainer.getItem()) {
            if (currentContainer.getItem().stream().noneMatch(i -> pathsMatch(i.getPath(), virtualItem.getPath()))) {
                mergedContainer.getItem().add(cloneComplex(virtualItem));
            }
        }

        return mergedContainer;
    }

    private <C extends Containerable> C cloneComplex(C containerable) {
        //noinspection unchecked
        PrismContainerValue<C> pcv = containerable.asPrismContainerValue().cloneComplex(CloneStrategy.REUSE);
        return pcv.asContainerable();
    }

    private ApplicablePoliciesType mergeApplicablePolicies(ArchetypePolicyType currentPolicy, ArchetypePolicyType superPolicy) {
        ApplicablePoliciesType currentApplicablePolicies = currentPolicy.getApplicablePolicies();
        ApplicablePoliciesType superApplicablePolicies = superPolicy.getApplicablePolicies();
        if (currentApplicablePolicies == null) {
            return superApplicablePolicies;
        }

        ApplicablePoliciesType mergedPolicies = currentApplicablePolicies.clone();
        if (superApplicablePolicies == null) {
            return mergedPolicies;
        }
        for (ObjectReferenceType policyGroupRef : superApplicablePolicies.getPolicyGroupRef()) {
            mergedPolicies.getPolicyGroupRef().add(policyGroupRef.clone());
        }

        return mergedPolicies;
    }

    private AssignmentRelationApproachType mergeRelationApproach(ArchetypePolicyType currentPolicy, ArchetypePolicyType superPolicy) {
        if (currentPolicy.getAssignmentHolderRelationApproach() != null) {
            return currentPolicy.getAssignmentHolderRelationApproach();
        }

        return superPolicy.getAssignmentHolderRelationApproach();

    }

    private ConflictResolutionType mergeConflictResolution(ArchetypePolicyType currentPolicy, ArchetypePolicyType superPolicy) {
        ConflictResolutionType currentConflictResolution = currentPolicy.getConflictResolution();
        ConflictResolutionType superConflictResolution = superPolicy.getConflictResolution();
        if (currentConflictResolution == null) {
            if (superConflictResolution == null) {
                return null;
            }
            return superConflictResolution.clone();
        }

        if (superConflictResolution == null) {
            return currentConflictResolution.clone();
        }

        ConflictResolutionType mergedConflictResolution = currentConflictResolution.clone();
        if (currentConflictResolution.getAction() == null) {
            mergedConflictResolution.setAction(superConflictResolution.getAction());
        }
        if (currentConflictResolution.getDelayUnit() == null) {
            mergedConflictResolution.setDelayUnit(superConflictResolution.getDelayUnit());
        }

        if (currentConflictResolution.getMaxAttempts() == null) {
            mergedConflictResolution.setMaxAttempts(superConflictResolution.getMaxAttempts());
        }

        return mergedConflictResolution;
    }

    private DisplayType mergeDisplayType(DisplayType currentDisplayType, DisplayType superDisplayType) {
        if (currentDisplayType == null) {
            if (superDisplayType == null) {
                return null;
            }
            return superDisplayType.clone();
        }

        if (superDisplayType == null) {
            return currentDisplayType.clone();
        }

        DisplayType mergedDisplayType = currentDisplayType.clone();
        if (currentDisplayType.getLabel() == null) {
            mergedDisplayType.setLabel(superDisplayType.getLabel());
        }

        if (currentDisplayType.getColor() == null) {
            mergedDisplayType.setColor(superDisplayType.getColor());
        }

        if (currentDisplayType.getCssClass() == null) {
            mergedDisplayType.setCssClass(superDisplayType.getCssClass());
        }

        if (currentDisplayType.getCssStyle() == null) {
            mergedDisplayType.setCssStyle(superDisplayType.getCssStyle());
        }

        if (currentDisplayType.getHelp() == null) {
            mergedDisplayType.setHelp(superDisplayType.getHelp());
        }

        IconType mergedIcon = mergeIcon(currentDisplayType.getIcon(), superDisplayType.getIcon());
        mergedDisplayType.setIcon(mergedIcon);

        if (currentDisplayType.getPluralLabel() == null) {
            mergedDisplayType.setPluralLabel(superDisplayType.getPluralLabel());
        }

        if (currentDisplayType.getSingularLabel() == null) {
            mergedDisplayType.setSingularLabel(superDisplayType.getSingularLabel());
        }

        if (currentDisplayType.getTooltip() == null) {
            mergedDisplayType.setTooltip(superDisplayType.getTooltip());
        }

        return mergedDisplayType;
    }

    private IconType mergeIcon(IconType currentIcon, IconType superIcon) {
        if (currentIcon == null) {
            if (superIcon == null) {
                return null;
            }
            return superIcon.clone();
        }

        if (superIcon == null) {
            return currentIcon.clone();
        }

        IconType mergedIcon = currentIcon.clone();
        if (currentIcon.getCssClass() == null) {
            mergedIcon.setCssClass(superIcon.getCssClass());
        }

        if (currentIcon.getColor() == null) {
            mergedIcon.setColor(superIcon.getColor());
        }

        if (currentIcon.getImageUrl() == null) {
            mergedIcon.setImageUrl(superIcon.getImageUrl());
        }

        return mergedIcon;
    }

    private List<ItemConstraintType> mergeItemConstraints(List<ItemConstraintType> currentConstraints, List<ItemConstraintType> superConstraints) {
        return mergeContainers(currentConstraints, superConstraints,
                this::createItemConstraintPredicate,
                this::mergeItemConstraint);
    }

    private Predicate<ItemConstraintType> createItemConstraintPredicate(ItemConstraintType constraint) {
        return c -> pathsMatch(constraint.getPath(), c.getPath());
    }

    // we want to merge according to path, but there might exist more than 1 def without path, so rather do nothing.
    private boolean pathsMatch(ItemPathType supperPath, ItemPathType currentPath) {
        return supperPath != null && currentPath != null && supperPath.equivalent(currentPath);
    }

    private ItemConstraintType mergeItemConstraint(ItemConstraintType matchedConstraint, ItemConstraintType superConstraint) {
        ItemConstraintType mergedConstraint = cloneComplex(matchedConstraint);
        if (matchedConstraint.getVisibility() == null) {
            mergedConstraint.setVisibility(superConstraint.getVisibility());
        }
        return mergedConstraint;
    }

    private LifecycleStateModelType mergeLifecycleStateModel(LifecycleStateModelType currentLifecycleStateModel, LifecycleStateModelType superLifecycleStateModel) {
        if (currentLifecycleStateModel == null) {
            if (superLifecycleStateModel == null) {
                return null;
            }
            return superLifecycleStateModel.clone();
        }

        if (superLifecycleStateModel == null) {
            return currentLifecycleStateModel.clone();
        }

        LifecycleStateModelType mergedLifecycleModel = new LifecycleStateModelType(prismContext);
        List<LifecycleStateType> mergedLifecycleState = mergeLifecycleState(currentLifecycleStateModel.getState(), superLifecycleStateModel.getState());
        mergedLifecycleModel.getState().addAll(mergedLifecycleState);

        return mergedLifecycleModel;
    }

    private List<LifecycleStateType> mergeLifecycleState(List<LifecycleStateType> currentState, List<LifecycleStateType> superLifecycleStates) {
        return mergeContainers(currentState, superLifecycleStates,
                this::createLifecycleStatePredicate,
                this::mergeLifecycleState);
    }

    private Predicate<LifecycleStateType> createLifecycleStatePredicate(LifecycleStateType currentState) {
        return s -> s.getName() != null && currentState.getName() != null && s.getName().equals(currentState.getName());
    }

    private LifecycleStateType mergeLifecycleState(LifecycleStateType currentLifecycleState, LifecycleStateType superLifecycleState) {
        LifecycleStateType mergedLifecycleState = new LifecycleStateType(prismContext);
        if (currentLifecycleState.getName() == null) {
            mergedLifecycleState.setName(superLifecycleState.getName());
        }

        if (currentLifecycleState.getDescription() == null) {
            mergedLifecycleState.setDescription(superLifecycleState.getDescription());
        }

        if (currentLifecycleState.getDisplayName() == null) {
            mergedLifecycleState.setDisplayName(superLifecycleState.getDisplayName());
        }

        List<LifecycleStateActionType> mergedEntryActions = mergeEntryAction(currentLifecycleState.getEntryAction(), superLifecycleState.getEntryAction());
        if (mergedEntryActions != null) {
            mergedLifecycleState.getEntryAction().clear();
            mergedLifecycleState.getEntryAction().addAll(mergedEntryActions);
        }

        List<LifecycleStateActionType> mergedExitActions = mergeEntryAction(currentLifecycleState.getExitAction(), superLifecycleState.getExitAction());
        if (mergedExitActions != null) {
            mergedLifecycleState.getExitAction().clear();
            mergedLifecycleState.getExitAction().addAll(mergedExitActions);
        }

        if (currentLifecycleState.getForcedActivationStatus() == null) {
            mergedLifecycleState.setForcedActivationStatus(superLifecycleState.getForcedActivationStatus());
        }

        if (currentLifecycleState.isActiveAssignments() == null) {
            mergedLifecycleState.setActiveAssignments(superLifecycleState.isActiveAssignments());
        }

        VirtualAssignmentSpecificationType mergedAssignment = mergeForcedAssignment(currentLifecycleState.getForcedAssignment(), superLifecycleState.getForcedAssignment());
        mergedLifecycleState.setForcedAssignment(mergedAssignment);

        return mergedLifecycleState;
    }

    private VirtualAssignmentSpecificationType mergeForcedAssignment(VirtualAssignmentSpecificationType currentForcedAssignment, VirtualAssignmentSpecificationType superForcedAssignment) {
        if (currentForcedAssignment == null) {
            if (superForcedAssignment == null) {
                return null;
            }
            return superForcedAssignment.clone();
        }

        if (superForcedAssignment == null) {
            return currentForcedAssignment.clone();
        }

        VirtualAssignmentSpecificationType mergedAssignment = currentForcedAssignment.clone();
        if (currentForcedAssignment.getFilter() == null) {
            mergedAssignment.setFilter(superForcedAssignment.getFilter().clone());
        }

        if (currentForcedAssignment.getTargetType() == null) {
            mergedAssignment.setTargetType(superForcedAssignment.getTargetType());
        }

        return mergedAssignment;
    }

    private List<LifecycleStateActionType> mergeEntryAction(List<LifecycleStateActionType> currentActions, List<LifecycleStateActionType> superActions) {
        return mergeContainers(currentActions, superActions,
                this::createLifecycleStateActionPredicate,
                this::mergeAction);
    }

    private Predicate<LifecycleStateActionType> createLifecycleStateActionPredicate(LifecycleStateActionType action) {
        return a -> a.getName() != null && a.getName().equals(action.getName());
    }

    private LifecycleStateActionType mergeAction(LifecycleStateActionType currentAction, LifecycleStateActionType superAction) {
        LifecycleStateActionType mergedAction = currentAction.clone();

        LifecycleStateActionDataReductionType currentDataReduction = currentAction.getDataReduction();
        LifecycleStateActionDataReductionType superDataReduction = superAction.getDataReduction();
        if (currentDataReduction == null) {
            if (superDataReduction == null) {
                return mergedAction;
            }
            mergedAction.setDataReduction(superDataReduction.clone());
            return mergedAction;
        }

        if (superDataReduction == null) {
            return mergedAction;
        }

        LifecycleStateActionDataReductionType mergedDataReduction = mergeDataReduction(currentDataReduction, superDataReduction);
        if (mergedDataReduction != null) {
            mergedAction.setDataReduction(mergedDataReduction);
        }
        return mergedAction;
    }

    private LifecycleStateActionDataReductionType mergeDataReduction(LifecycleStateActionDataReductionType currentDataReduction, LifecycleStateActionDataReductionType superDataReduction) {
        List<ItemPathType> currentItems = currentDataReduction.getPurgeItem();
        List<ItemPathType> superItems = superDataReduction.getPurgeItem();

        LifecycleStateActionDataReductionType mergedDataReduction = currentDataReduction.clone();
        if (currentItems.isEmpty()) {
            if (superItems.isEmpty()) {
                return null;
            }
            superItems.forEach(i -> mergedDataReduction.getPurgeItem().add(i.clone()));
            return mergedDataReduction;
        }

        for (ItemPathType superItem : superItems) {
            if (!currentItems.contains(superItem)) {
                mergedDataReduction.getPurgeItem().add(superItem.clone());
            }
        }

        return mergedDataReduction;
    }

    private ArchetypePolicyType merge(ArchetypePolicyType archetypePolicy, ObjectPolicyConfigurationType objectPolicy) {
        if (archetypePolicy == null && objectPolicy == null) {
            return null;
        }
        if (archetypePolicy == null) {
            return objectPolicy.clone();
        }
        if (objectPolicy == null) {
            return archetypePolicy.clone();
        }
        ArchetypePolicyType resultPolicy = archetypePolicy.clone();
        if (archetypePolicy.getApplicablePolicies() == null && objectPolicy.getApplicablePolicies() != null) {
            resultPolicy.setApplicablePolicies(objectPolicy.getApplicablePolicies().clone());
        }
        if (archetypePolicy.getConflictResolution() == null && objectPolicy.getConflictResolution() != null) {
            resultPolicy.setConflictResolution(objectPolicy.getConflictResolution().clone());
        }
        if (archetypePolicy.getDisplay() == null && objectPolicy.getDisplay() != null) {
            resultPolicy.setDisplay(objectPolicy.getDisplay().clone());
        }
        if (archetypePolicy.getExpressionProfile() == null && objectPolicy.getExpressionProfile() != null) {
            resultPolicy.setExpressionProfile(objectPolicy.getExpressionProfile());
        }
        if (archetypePolicy.getLifecycleStateModel() == null && objectPolicy.getLifecycleStateModel() != null) {
            resultPolicy.setLifecycleStateModel(objectPolicy.getLifecycleStateModel().clone());
        }
        if (archetypePolicy.getObjectTemplateRef() == null && objectPolicy.getObjectTemplateRef() != null) {
            resultPolicy.setObjectTemplateRef(objectPolicy.getObjectTemplateRef().clone());
        }
        if (archetypePolicy.getItemConstraint().isEmpty()) {
            for (ItemConstraintType objItemConstraint : objectPolicy.getItemConstraint()) {
                resultPolicy.getItemConstraint().add(objItemConstraint.clone());
            }
        }
        // Deprecated
        if (archetypePolicy.getPropertyConstraint().isEmpty()) {
            for (ItemConstraintType objPropertyConstraint : objectPolicy.getPropertyConstraint()) {
                resultPolicy.getPropertyConstraint().add(objPropertyConstraint.clone());
            }
        }
        return resultPolicy;
    }

    private <O extends ObjectType> ObjectPolicyConfigurationType determineObjectPolicyConfiguration(PrismObject<O> object, OperationResult result) throws SchemaException, ConfigurationException {
        if (object == null) {
            return null;
        }
        PrismObject<SystemConfigurationType> systemConfiguration = systemObjectCache.getSystemConfiguration(result);
        if (systemConfiguration == null) {
            return null;
        }
        return determineObjectPolicyConfiguration(object, systemConfiguration.asObjectable());
    }

    public <O extends ObjectType> ExpressionProfile determineExpressionProfile(PrismObject<O> object, OperationResult result) throws SchemaException, ConfigurationException {
        ArchetypePolicyType archetypePolicy = determineArchetypePolicy(object, result);
        if (archetypePolicy == null) {
            return null;
        }
        String expressionProfileId = archetypePolicy.getExpressionProfile();
        return systemObjectCache.getExpressionProfile(expressionProfileId, result);
    }

    /**
     * This has to remain static due to use from LensContext. Hopefully it will get refactored later.
     */
    private static <O extends ObjectType> ObjectPolicyConfigurationType determineObjectPolicyConfiguration(PrismObject<O> object, SystemConfigurationType systemConfigurationType) throws ConfigurationException {
        List<String> subTypes = FocusTypeUtil.determineSubTypes(object);
        return determineObjectPolicyConfiguration(object.getCompileTimeClass(), subTypes, systemConfigurationType);
    }

    public static <O extends ObjectType> ObjectPolicyConfigurationType determineObjectPolicyConfiguration(Class<O> objectClass, List<String> objectSubtypes, SystemConfigurationType systemConfigurationType) throws ConfigurationException {
        ObjectPolicyConfigurationType applicablePolicyConfigurationType = null;
        for (ObjectPolicyConfigurationType aPolicyConfigurationType: systemConfigurationType.getDefaultObjectPolicyConfiguration()) {
            QName typeQName = aPolicyConfigurationType.getType();
            if (typeQName == null) {
                continue;       // TODO implement correctly (using 'applicable policies' perhaps)
            }
            ObjectTypes objectType = ObjectTypes.getObjectTypeFromTypeQName(typeQName);
            if (objectType == null) {
                throw new ConfigurationException("Unknown type "+typeQName+" in default object policy definition in system configuration");
            }
            if (objectType.getClassDefinition() == objectClass) {
                String aSubType = aPolicyConfigurationType.getSubtype();
                if (aSubType == null) {
                    if (applicablePolicyConfigurationType == null) {
                        applicablePolicyConfigurationType = aPolicyConfigurationType;
                    }
                } else if (objectSubtypes != null && objectSubtypes.contains(aSubType)) {
                    applicablePolicyConfigurationType = aPolicyConfigurationType;
                }
            }
        }
        return applicablePolicyConfigurationType;
    }

    // TODO take object's archetype into account
    public static <O extends ObjectType> LifecycleStateModelType determineLifecycleModel(PrismObject<O> object, PrismObject<SystemConfigurationType> systemConfiguration) throws ConfigurationException {
        if (systemConfiguration == null) {
            return null;
        }
        return determineLifecycleModel(object, systemConfiguration.asObjectable());
    }

    public static <O extends ObjectType> LifecycleStateModelType determineLifecycleModel(PrismObject<O> object, SystemConfigurationType systemConfigurationType) throws ConfigurationException {
        ObjectPolicyConfigurationType objectPolicyConfiguration = determineObjectPolicyConfiguration(object, systemConfigurationType);
        if (objectPolicyConfiguration == null) {
            return null;
        }
        return objectPolicyConfiguration.getLifecycleStateModel();
    }

    @Override
    public void invalidate(Class<?> type, String oid, CacheInvalidationContext context) {
        if (type == null || INVALIDATION_RELATED_CLASSES.contains(type)) {
            archetypePolicyCache.clear();
        }
    }

    @Override
    public @NotNull Collection<SingleCacheStateInformationType> getStateInformation() {
        return Collections.singleton(new SingleCacheStateInformationType(prismContext)
                .name(ArchetypeManager.class.getName())
                .size(archetypePolicyCache.size()));
    }

    @Override
    public void dumpContent() {
        if (LOGGER_CONTENT.isInfoEnabled()) {
            archetypePolicyCache.forEach((k, v) -> LOGGER_CONTENT.info("Cached archetype policy: {}: {}", k, v));
        }
    }
}
