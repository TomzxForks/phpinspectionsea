package com.kalessil.phpStorm.phpInspectionsEA.inspectors.codeSmell;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.php.lang.lexer.PhpTokenTypes;
import com.jetbrains.php.lang.psi.PhpPsiElementFactory;
import com.jetbrains.php.lang.psi.elements.PhpPsiElement;
import com.jetbrains.php.lang.psi.elements.UnaryExpression;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.BasePhpElementVisitor;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.BasePhpInspection;
import org.jetbrains.annotations.NotNull;

public class NestedNotOperatorsInspector extends BasePhpInspection {
    private static final String messagePatternUseBoolCasting = "Can be replaced with (bool)%e%";
    private static final String messagePatternUseSingleNot   = "Can be replaced with !%e%";

    @NotNull
    public String getShortName() {
        return "NestedNotOperatorsInspection";
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return new BasePhpElementVisitor() {
            public void visitPhpUnaryExpression(UnaryExpression expr) {
                /* process ony not operations */
                PsiElement operator = expr.getOperation();
                if (null == operator || operator.getNode().getElementType() != PhpTokenTypes.opNOT) {
                    return;
                }

                /* process only deepest not-operator: get contained expression */
                final PhpPsiElement value = expr.getValue();
                if (null == value) {
                    return;
                }
                /* if contained expression is also inversion, do nothing -> to not report several times */
                if (value instanceof UnaryExpression) {
                    operator = ((UnaryExpression) value).getOperation();
                    if (null != operator && operator.getNode().getElementType() == PhpTokenTypes.opNOT) {
                        return;
                    }
                }

                /* check nesting level */
                PsiElement target = null;
                int nestingLevel  = 1;
                PsiElement parent = expr.getParent();
                while (parent instanceof UnaryExpression) {
                    expr     = (UnaryExpression) parent;
                    operator = expr.getOperation();

                    if (null != operator && operator.getNode().getElementType() == PhpTokenTypes.opNOT) {
                        ++nestingLevel;
                        target = parent;
                    }

                    parent = expr.getParent();
                }
                if (nestingLevel == 1) {
                    return;
                }

                /* fire warning */
                final String message =
                        (nestingLevel % 2 == 0 ? messagePatternUseBoolCasting : messagePatternUseSingleNot)
                        .replace("%e%", value.getText());
                final LocalQuickFix fixer =
                        nestingLevel % 2 == 0 ? new UseCastingLocalFix(value) : new UseSingleNotLocalFix(value);

                holder.registerProblem(target, message, ProblemHighlightType.WEAK_WARNING, fixer);
            }
        };
    }

    private static class UseSingleNotLocalFix implements LocalQuickFix {
        PsiElement value;

        UseSingleNotLocalFix(@NotNull PsiElement value) {
            super();
            this.value = value;
        }

        @NotNull
        @Override
        public String getName() {
            return "Use single not operator";
        }

        @NotNull
        @Override
        public String getFamilyName() {
            return getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            final PsiElement expression = descriptor.getPsiElement();
            if (expression instanceof UnaryExpression) {
                //noinspection ConstantConditions I'm sure that NPE will not happen as inspection reports only finished structures
                ((UnaryExpression) expression).getValue().replace(this.value);
            }

            /* Release node reference */
            this.value = null;
        }
    }

    private static class UseCastingLocalFix implements LocalQuickFix {
        PsiElement value;

        UseCastingLocalFix(@NotNull PsiElement value) {
            super();
            this.value = value;
        }

        @NotNull
        @Override
        public String getName() {
            return "Use boolean casting";
        }

        @NotNull
        @Override
        public String getFamilyName() {
            return getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            UnaryExpression replacement = PhpPsiElementFactory.createFromText(project, UnaryExpression.class, "(bool) null");
            //noinspection ConstantConditions I'm sure that NPE will not happen as we have hardcoded expression
            replacement.getValue().replace(this.value);

            descriptor.getPsiElement().replace(replacement);

            /* Release node reference */
            this.value = null;
        }
    }
}
