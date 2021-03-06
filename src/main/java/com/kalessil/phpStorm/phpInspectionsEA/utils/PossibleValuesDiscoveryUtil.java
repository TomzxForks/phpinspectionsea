package com.kalessil.phpStorm.phpInspectionsEA.utils;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.psi.elements.*;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;

/*
 * This file is part of the Php Inspections (EA Extended) package.
 *
 * (c) Vladimir Reznichenko <kalessil@gmail.com>
 *
 * For the full copyright and license information, please view the LICENSE
 * file that was distributed with this source code.
 */

public class PossibleValuesDiscoveryUtil {
    @NotNull
    static public HashSet<PsiElement> discover(@NotNull PsiElement expression, @NotNull HashSet<PsiElement> processed) {
        /* un-wrap parenthesises to avoid false-positives */
        expression = ExpressionSemanticUtil.getExpressionTroughParenthesis(expression);

        /* do not process same expressions multiple times */
        HashSet<PsiElement> result = new HashSet<>();
        if (processed.contains(expression)) {
            return result;
        }
        processed.add(expression);

        /* Case 1: ternary, recursively check variants */
        if (expression instanceof TernaryExpression) {
            handleTernary((TernaryExpression) expression, result, processed);
            return result;
        }

        /* Case 2: parameter defaults, assignments */
        if (expression instanceof Variable) {
            handleVariable((Variable) expression, result, processed);
            return result;
        }

        /* Case 3: default value discovery */
        if (expression instanceof FieldReference) {
            handleClassFieldReference((FieldReference) expression, result, processed);
            return result;
        }

        /* Case 4: constants value discovery */
        if (expression instanceof ClassConstantReference) {
            handleClassConstantReference((ClassConstantReference) expression, result, processed);
            return result;
        }

        /* default case: add expression itself */
        result.add(expression);
        return result;
    }

    static private void handleVariable(
            @NotNull Variable variable, @NotNull HashSet<PsiElement> result, @NotNull HashSet<PsiElement> processed
    ) {
        final String variableName = variable.getName();
        final Function callable   = StringUtil.isEmpty(variableName) ? null : ExpressionSemanticUtil.getScope(variable);
        if (null == callable) {
            return;
        }

        /* collect default value if variable is a parameter */
        for (Parameter parameter : callable.getParameters()) {
            final PsiElement defaultValue = parameter.getDefaultValue();
            if (null != defaultValue && parameter.getName().equals(variableName)) {
                result.add(defaultValue);
                break;
            }
        }

        final GroupStatement body = ExpressionSemanticUtil.getGroupStatement(callable);
        for (AssignmentExpression expression : PsiTreeUtil.findChildrenOfType(body, AssignmentExpression.class)) {
            /* TODO: probable bug - self-assignment does not override instance of */
            if (expression instanceof SelfAssignmentExpression) {
                continue;
            }

            final PsiElement container   = expression.getVariable();
            final PsiElement storedValue = expression.getValue();
            if (null != storedValue && container instanceof Variable) {
                final String containerName = ((Variable) container).getName();
                if (containerName.equals(variableName)) {
                    final HashSet<PsiElement> discoveredWrites = discover(storedValue, processed);
                    if (discoveredWrites.size() > 0) {
                        result.addAll(discoveredWrites);
                        discoveredWrites.clear();
                    }
                }
            }
        }
    }

    static private void handleClassConstantReference(
            @NotNull ClassConstantReference reference, @NotNull HashSet<PsiElement> result, @NotNull HashSet<PsiElement> processed
    ) {
        final String constantName          = reference.getName();
        final PsiElement resolvedReference = StringUtil.isEmpty(constantName) ? null : reference.resolve();
        if (resolvedReference instanceof Field) {
            final PsiElement value = ((Field) resolvedReference).getDefaultValue();
            if (null != value) {
                result.add(value);
            }
        }
    }

    static private void handleClassFieldReference(
            @NotNull FieldReference reference, @NotNull HashSet<PsiElement> result, @NotNull HashSet<PsiElement> processed
    ) {
        final String fieldName             = reference.getName();
        final PsiElement resolvedReference = StringUtil.isEmpty(fieldName) ? null : reference.resolve();
        if (resolvedReference instanceof Field) {
            /* TODO: properties without defaults returning variable as default are difficult to identify */
            /* TODO: multi-assignments */
            final PsiElement defaultValue = ((Field) resolvedReference).getDefaultValue();
            if (null != defaultValue && !defaultValue.getText().endsWith(fieldName)) {
                result.add(defaultValue);
            }
        }

        /* TODO: inspect own constructor for overriding property there */
        final Function callable = ExpressionSemanticUtil.getScope(reference);
        if (null != callable) {
            final GroupStatement body = ExpressionSemanticUtil.getGroupStatement(callable);
            for (AssignmentExpression expression : PsiTreeUtil.findChildrenOfType(body, AssignmentExpression.class)) {
                /* TODO: probable bug - self-assignment does not override instance of */
                /* TODO: multi-assignments */
                if (expression instanceof SelfAssignmentExpression) {
                    continue;
                }

                final PsiElement container   = expression.getVariable();
                final PsiElement storedValue = expression.getValue();
                if (null != storedValue && container instanceof FieldReference) {
                    final String containerName = ((FieldReference) container).getName();
                    if (
                        null != containerName && containerName.equals(fieldName) &&
                        PsiEquivalenceUtil.areElementsEquivalent(container, reference)
                    ) {
                        final HashSet<PsiElement> discoveredWrites = discover(storedValue, processed);
                        if (discoveredWrites.size() > 0) {
                            result.addAll(discoveredWrites);
                            discoveredWrites.clear();
                        }
                    }
                }
            }
        }
    }

    static private void handleTernary(
            @NotNull TernaryExpression ternary, @NotNull HashSet<PsiElement> result, @NotNull HashSet<PsiElement> processed
    ) {
        final PsiElement trueVariant  = ternary.getTrueVariant();
        final PsiElement falseVariant = ternary.getFalseVariant();
        if (null == trueVariant || null == falseVariant) {
            return;
        }

        /* discover true and false branches */
        HashSet<PsiElement> trueVariants = discover(trueVariant, processed);
        if (trueVariants.size() > 0) {
            result.addAll(trueVariants);
            trueVariants.clear();
        }
        HashSet<PsiElement> falseVariants = discover(falseVariant, processed);
        if (falseVariants.size() > 0) {
            result.addAll(falseVariants);
            falseVariants.clear();
        }
    }
}
