/*
 * Copyright (C) 2010-2023 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.model.impl.lens.projector.policy;

import com.evolveum.midpoint.model.api.context.EvaluatedAssignment;
import com.evolveum.midpoint.model.api.context.EvaluatedPolicyRule;
import com.evolveum.midpoint.model.api.context.EvaluatedPolicyRule.TargetType;
import com.evolveum.midpoint.model.common.GlobalRuleWithId;
import com.evolveum.midpoint.model.common.ModelCommonBeans;
import com.evolveum.midpoint.model.common.mapping.MappingBuilder;
import com.evolveum.midpoint.model.common.mapping.MappingImpl;
import com.evolveum.midpoint.model.impl.ModelBeans;
import com.evolveum.midpoint.model.impl.lens.EvaluatedPolicyRuleImpl;
import com.evolveum.midpoint.model.impl.lens.LensContext;
import com.evolveum.midpoint.model.impl.lens.LensFocusContext;
import com.evolveum.midpoint.model.impl.lens.LensUtil;
import com.evolveum.midpoint.model.impl.lens.assignments.EvaluatedAssignmentImpl;
import com.evolveum.midpoint.model.impl.lens.assignments.EvaluatedAssignmentTargetImpl;
import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.delta.DeltaSetTriple;
import com.evolveum.midpoint.prism.delta.PrismValueDeltaSetTriple;
import com.evolveum.midpoint.prism.util.ObjectDeltaObject;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.repo.common.expression.ExpressionUtil;
import com.evolveum.midpoint.schema.constants.ExpressionConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.PolicyRuleTypeUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static com.evolveum.midpoint.model.api.context.EvaluatedPolicyRule.TargetType.DIRECT_ASSIGNMENT_TARGET;
import static com.evolveum.midpoint.model.api.context.EvaluatedPolicyRule.TargetType.INDIRECT_ASSIGNMENT_TARGET;
import static com.evolveum.midpoint.util.MiscUtil.stateCheck;

/**
 * Collects relevant rules (for assignments, focus, or projection) from all relevant sources.
 *
 * @see #collectObjectRules(OperationResult)
 * @see #collectGlobalAssignmentRules(DeltaSetTriple, OperationResult)
 */
class PolicyRulesCollector<O extends ObjectType> {

    private static final Trace LOGGER = TraceManager.getTrace(PolicyRulesCollector.class);

    @NotNull private final LensContext<O> context;
    @NotNull private final Task task;
    @NotNull private final RepositoryService repositoryService = ModelBeans.get().cacheRepositoryService;

    private List<GlobalRuleWithId> rulesWithIds;

    PolicyRulesCollector(@NotNull LensContext<O> context, @NotNull Task task) {
        this.context = context;
        this.task = task;
    }

    public void initialize(OperationResult result) {
        rulesWithIds = getAllGlobalPolicyRules(result);
    }

    private void checkInitialized() {
        stateCheck(rulesWithIds != null, "Not initialized");
    }

    /** Collects "object rules" (i.e. for focus and assignments) from all sources: assignments and global config, incl. marks. */
    @NotNull List<EvaluatedPolicyRuleImpl> collectObjectRules(OperationResult result)
            throws SchemaException, ExpressionEvaluationException, ObjectNotFoundException, SecurityViolationException,
            ConfigurationException, CommunicationException {
        List<EvaluatedPolicyRuleImpl> rules = new ArrayList<>();
        collectObjectRulesFromAssignments(rules);
        collectGlobalObjectRules(rules, result);
        resolveConstraintReferences(rules);
        return rules;
    }

    private void collectObjectRulesFromAssignments(List<EvaluatedPolicyRuleImpl> rules) {
        // We intentionally evaluate rules also from negative (deleted) assignments.
        for (EvaluatedAssignmentImpl<?> evaluatedAssignment : context.getAllEvaluatedAssignments()) {
            rules.addAll(evaluatedAssignment.getObjectPolicyRules());
        }
    }

    private void collectGlobalObjectRules(List<EvaluatedPolicyRuleImpl> rules, OperationResult result)
            throws SchemaException, ExpressionEvaluationException, ObjectNotFoundException, SecurityViolationException,
            ConfigurationException, CommunicationException {
        PrismObject<O> focus = getFocusForSelection();
        List<GlobalRuleWithId> ruleMatchingFocus = getGlobalRulesMatchingFocus(focus);
        int globalRulesFound = 0;
        for (GlobalRuleWithId ruleWithId : ruleMatchingFocus) {
            GlobalPolicyRuleType ruleBean = ruleWithId.getRuleBean();
            if (isRuleConditionTrue(ruleBean, focus, null, result)) {
                LOGGER.trace("Collecting global policy rule '{}' ({})", ruleBean.getName(), ruleWithId.getRuleId());
                rules.add(
                        new EvaluatedPolicyRuleImpl(
                                ruleBean.clone(), ruleWithId.getRuleId(), null, TargetType.OBJECT));
                globalRulesFound++;
            } else {
                LOGGER.trace("Skipping global policy rule {} ({}) because the condition evaluated to false: {}",
                        ruleBean.getName(), ruleWithId.getRuleId(), ruleBean);
            }
        }
        LOGGER.trace("Selected {} global policy rules for further evaluation", globalRulesFound);
    }

    void collectGlobalAssignmentRules(
            DeltaSetTriple<? extends EvaluatedAssignmentImpl<?>> evaluatedAssignmentTriple, OperationResult result)
            throws SchemaException, ExpressionEvaluationException, ObjectNotFoundException, SecurityViolationException,
            ConfigurationException, CommunicationException {

        PrismObject<O> focus = getFocusForSelection();

        List<GlobalRuleWithId> focusMatching = getGlobalRulesMatchingFocus(focus);
        int globalRulesInstantiated = 0;
        for (GlobalRuleWithId ruleWithId : focusMatching) {
            GlobalPolicyRuleType ruleBean = ruleWithId.getRuleBean();
            String ruleName = ruleBean.getName();
            for (EvaluatedAssignmentImpl<?> evaluatedAssignment : evaluatedAssignmentTriple.getAllValues()) {
                for (EvaluatedAssignmentTargetImpl target : evaluatedAssignment.getRoles().getNonNegativeValues()) { // MID-6403
                    boolean appliesDirectlyToTarget = target.isDirectlyAssigned();
                    if (!appliesDirectlyToTarget && !target.getAssignmentPath().last().isMatchingOrder()) {
                        // This is to be thought out well. It is of no use to include global policy rules
                        // attached to meta-roles assigned to the role being assigned to the focus.
                        //
                        // But we certainly need to include rules
                        //
                        // 1. attached to a directly assigned role (because they might be considered for assignment)
                        // 2. attached to an indirectly assigned role but of the matching order (because of exclusion violation).
                        continue;
                    }
                    if (!repositoryService.selectorMatches(
                            ruleBean.getTargetSelector(), target.getTarget(), null, LOGGER,
                            "Global policy rule "+ruleName+" target selector: ")) {
                        LOGGER.trace("Skipping global policy rule {} because target selector did not match: {}",
                                ruleName, ruleWithId);
                        continue;
                    }
                    if (!isRuleConditionTrue(ruleBean, focus, evaluatedAssignment, result)) {
                        LOGGER.trace("Skipping global policy rule {} because the condition evaluated to false: {}",
                                ruleName, ruleWithId);
                        continue;
                    }
                    LOGGER.trace("Collecting global policy rule '{}' in {}, considering target {} (applies directly: {})",
                            ruleBean.getName(), evaluatedAssignment, target, appliesDirectlyToTarget);
                    evaluatedAssignment.addTargetPolicyRule(
                            new EvaluatedPolicyRuleImpl(
                                    ruleBean.clone(),
                                    ruleWithId.getRuleId(),
                                    target.getAssignmentPath().clone(),
                                    evaluatedAssignment,
                                    appliesDirectlyToTarget ? DIRECT_ASSIGNMENT_TARGET : INDIRECT_ASSIGNMENT_TARGET));
                    globalRulesInstantiated++;
                }
            }
        }
        LOGGER.trace("Global policy rules instantiated {} times for further evaluation", globalRulesInstantiated);
    }

    /** Treats both config- and mark-based rules. */
    private List<GlobalRuleWithId> getGlobalRulesMatchingFocus(@Nullable PrismObject<O> focus)
            throws SchemaException, ExpressionEvaluationException, CommunicationException, SecurityViolationException,
            ConfigurationException, ObjectNotFoundException {
        checkInitialized();
        List<GlobalRuleWithId> matching = new ArrayList<>();
        LOGGER.trace("Checking {} global policy rules for use with the object or assignments", rulesWithIds.size());
        for (GlobalRuleWithId ruleWithId: rulesWithIds) {
            GlobalPolicyRuleType ruleBean = ruleWithId.getRuleBean();
            ObjectSelectorType focusSelector = ruleBean.getFocusSelector();
            if (focusSelector == null
                    || repositoryService.selectorMatches(
                    focusSelector,
                    focus,
                    null,
                    LOGGER,
                    "Global policy rule " + ruleBean.getName() + ": ")) {
                matching.add(ruleWithId);
            }
        }
        return matching;
    }

    private @Nullable PrismObject<O> getFocusForSelection() {
        // We select rules based on current object state, not the new one.
        // (e.g. because some of the constraints might be modification constraints)
        LensFocusContext<O> focusContext = context.getFocusContext();
        if (focusContext == null) {
            return null;
        }
        PrismObject<O> focusCurrent = focusContext.getObjectCurrent();
        if (focusCurrent != null) {
            return focusCurrent;
        }
        PrismObject<O> focusNew = focusContext.getObjectNew();
        if (focusNew != null) {
            return focusNew;
        }

        return focusContext.isDeleted() ? focusContext.getObjectOld() : null;
    }

    /**
     * Gets all global policy rules: from configuration + from enabled marks.
     *
     * TODO implement more efficiently (but beware, marks can be enabled/disabled per task)
     */
    private @NotNull List<GlobalRuleWithId> getAllGlobalPolicyRules(@NotNull OperationResult result) {
        SystemConfigurationType systemConfiguration = context.getSystemConfigurationBean();
        List<GlobalRuleWithId> allRules = new ArrayList<>();
        if (systemConfiguration != null) {
            for (GlobalPolicyRuleType ruleBean : systemConfiguration.getGlobalPolicyRule()) {
                allRules.add(
                        GlobalRuleWithId.of(ruleBean, systemConfiguration.getOid()));
            }
        }
        allRules.addAll(
                ModelCommonBeans.get().markManager.getAllEnabledMarkPolicyRules(task, result));
        return allRules;
    }

    void resolveConstraintReferences(
            Collection<? extends EvaluatedPolicyRule> evaluatedRules) {
        List<PolicyRuleType> rules = evaluatedRules.stream()
                .map(EvaluatedPolicyRule::getPolicyRule)
                .collect(Collectors.toList());
        checkInitialized();
        Collection<GlobalPolicyRuleType> allGlobalRules = rulesWithIds.stream()
                .map(GlobalRuleWithId::getRuleBean)
                .collect(Collectors.toList());
        PolicyRuleTypeUtil.resolveConstraintReferences(rules, allGlobalRules);
    }

    private boolean isRuleConditionTrue(
            @NotNull GlobalPolicyRuleType globalPolicyRule,
            @Nullable PrismObject<O> focus,
            @Nullable EvaluatedAssignmentImpl<?> evaluatedAssignment,
            @NotNull OperationResult result)
            throws ExpressionEvaluationException, ObjectNotFoundException, SchemaException, SecurityViolationException,
            ConfigurationException, CommunicationException {
        MappingType condition = globalPolicyRule.getCondition();
        if (condition == null) {
            return true;
        }
        if (!task.canSee(condition)) {
            LOGGER.trace("Condition is not visible for the current task");
            return true;
        }

        MappingBuilder<PrismPropertyValue<Boolean>, PrismPropertyDefinition<Boolean>> builder =
                ModelBeans.get().mappingFactory.createMappingBuilder();
        ObjectDeltaObject<O> focusOdo = focus != null ?
                new ObjectDeltaObject<>(focus, null, focus, focus.getDefinition()) : null;
        PrismObject<?> target = evaluatedAssignment != null ? evaluatedAssignment.getTarget() : null;

        builder = builder.mappingBean(condition)
                .mappingKind(MappingKindType.POLICY_RULE_CONDITION)
                .contextDescription("condition in global policy rule " + globalPolicyRule.getName())
                .sourceContext(focusOdo)
                .defaultTargetDefinition(LensUtil.createConditionDefinition(PrismContext.get()))
                .addVariableDefinition(ExpressionConstants.VAR_USER, focusOdo)
                .addVariableDefinition(ExpressionConstants.VAR_FOCUS, focusOdo)
                .addAliasRegistration(ExpressionConstants.VAR_USER, null)
                .addAliasRegistration(ExpressionConstants.VAR_FOCUS, null)
                .addVariableDefinition(ExpressionConstants.VAR_TARGET,
                        target, target != null ? target.getDefinition() : getObjectDefinition())
                .addVariableDefinition(
                        ExpressionConstants.VAR_EVALUATED_ASSIGNMENT, evaluatedAssignment, EvaluatedAssignment.class)
                .addVariableDefinition(ExpressionConstants.VAR_ASSIGNMENT,
                        evaluatedAssignment != null ? evaluatedAssignment.getAssignment() : null, AssignmentType.class)
                .rootNode(focusOdo);

        MappingImpl<PrismPropertyValue<Boolean>, PrismPropertyDefinition<Boolean>> mapping = builder.build();

        ModelBeans.get().mappingEvaluator.evaluateMapping(mapping, context, task, result);

        PrismValueDeltaSetTriple<PrismPropertyValue<Boolean>> conditionTriple = mapping.getOutputTriple();
        return conditionTriple != null
                && ExpressionUtil.computeConditionResult(conditionTriple.getNonNegativeValues());
        // TODO: null -> true (in the method) - ok?
    }

    private PrismObjectDefinition<?> getObjectDefinition() {
        return PrismContext.get().getSchemaRegistry().findObjectDefinitionByCompileTimeClass(ObjectType.class);
    }
}
