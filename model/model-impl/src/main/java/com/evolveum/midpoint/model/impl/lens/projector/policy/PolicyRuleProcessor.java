/*
 * Copyright (C) 2010-2023 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.model.impl.lens.projector.policy;

import com.evolveum.midpoint.model.impl.lens.LensContext;
import com.evolveum.midpoint.model.impl.lens.LensFocusContext;
import com.evolveum.midpoint.model.impl.lens.LensProjectionContext;
import com.evolveum.midpoint.model.impl.lens.projector.ProjectorProcessor;
import com.evolveum.midpoint.model.impl.lens.projector.util.ProcessorMethod;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AssignmentHolderType;

import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.model.impl.lens.projector.util.ProcessorExecution;

import javax.xml.datatype.XMLGregorianCalendar;

/**
 * A facade for various actions related to handling of policy rules: evaluation, enforcement, and so on.
 */
@Component
@ProcessorExecution
public class PolicyRuleProcessor implements ProjectorProcessor {

    private static final String CLASS_DOT = PolicyRuleProcessor.class.getName() + ".";
    private static final String OP_EVALUATE_ASSIGNMENT_POLICY_RULES = CLASS_DOT + "evaluateAssignmentPolicyRules";
    private static final String OP_ENFORCE = CLASS_DOT + "enforce";

    public <F extends AssignmentHolderType> void evaluateAssignmentPolicyRules(
            @NotNull LensFocusContext<F> focusContext,
            @NotNull Task task,
            @NotNull OperationResult parentResult)
            throws SchemaException, ExpressionEvaluationException, ObjectNotFoundException, CommunicationException,
            ConfigurationException, SecurityViolationException {
        OperationResult result = parentResult.createSubresult(OP_EVALUATE_ASSIGNMENT_POLICY_RULES);
        try {
            new AssignmentPolicyRuleEvaluator<>(focusContext, task)
                    .evaluate(result);
        } catch (Throwable t) {
            result.recordException(t);
            throw t;
        } finally {
            result.close();
        }
    }

    @ProcessorMethod
    public <AH extends AssignmentHolderType> void evaluateFocusPolicyRules(
            LensContext<AH> context, @SuppressWarnings("unused") XMLGregorianCalendar now, Task task, OperationResult result)
            throws SchemaException, ExpressionEvaluationException, ObjectNotFoundException, SecurityViolationException,
            ConfigurationException, CommunicationException {
        // No need for custom operation result, as this already has one
        new ObjectPolicyRulesEvaluator.FocusPolicyRulesEvaluator<>(context.getFocusContextRequired(), task)
                .evaluate(result);
    }

    @ProcessorMethod
    public <AH extends AssignmentHolderType> void evaluateProjectionPolicyRules(
            LensContext<AH> ignoredContext,
            LensProjectionContext projectionContext,
            String ignoredActivityDescription,
            XMLGregorianCalendar ignoredNow,
            Task task,
            OperationResult result)
            throws SchemaException, ExpressionEvaluationException, ObjectNotFoundException, SecurityViolationException,
            ConfigurationException, CommunicationException {
        // No need for custom operation result, as this already has one
        new ObjectPolicyRulesEvaluator.ProjectionPolicyRulesEvaluator(projectionContext, task)
                .evaluate(result);
    }

    /** Updates counters for policy rules, with the goal of determining if rules' thresholds have been reached. */
    @ProcessorMethod
    public <AH extends AssignmentHolderType> void updateCounters(
            LensContext<AH> context, @SuppressWarnings("unused") XMLGregorianCalendar now, Task task, OperationResult result)
            throws SchemaException, ObjectNotFoundException, ObjectAlreadyExistsException {
        // No need for custom operation result, as this already has one
        new PolicyRuleCounterUpdater<>(context, task)
                .updateCounters(result);
    }

    public <O extends ObjectType> void enforce(@NotNull LensContext<O> context, OperationResult parentResult)
            throws PolicyViolationException, ConfigurationException {
        OperationResult result = parentResult.createMinorSubresult(OP_ENFORCE);
        try {
            new PolicyRuleEnforcer<>(context)
                    .enforce(result);
        } catch (Throwable t) {
            result.recordException(t);
            throw t;
        } finally {
            result.close();
        }
    }
}
