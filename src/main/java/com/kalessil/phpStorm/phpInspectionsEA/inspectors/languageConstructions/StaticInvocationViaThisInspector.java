package com.kalessil.phpStorm.phpInspectionsEA.inspectors.languageConstructions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.jetbrains.php.lang.psi.PhpPsiElementFactory;
import com.jetbrains.php.lang.psi.elements.FunctionReference;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.MethodReference;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.BasePhpElementVisitor;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.BasePhpInspection;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class StaticInvocationViaThisInspector extends BasePhpInspection {
    // configuration flags automatically saved by IDE
    @SuppressWarnings("WeakerAccess")
    public boolean RESPECT_PHPUNIT_STANDARDS = true;

    private static final String messageThisUsed       = "'static::%m%(...)' should be used instead";
    private static final String messageExpressionUsed = "'...::%m%(...)' should be used instead";

    @NotNull
    public String getShortName() {
        return "StaticInvocationViaThisInspection";
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return new BasePhpElementVisitor() {
            /* Static fields will be simply not resolved properly, so we can not do checking for them */

            public void visitPhpMethodReference(MethodReference reference) {
                final PsiReference objReference = reference.getReference();
                final String methodName         = reference.getName();
                if (null != objReference && !StringUtil.isEmpty(methodName)) {
                    final PsiElement objResolvedRef = objReference.resolve();
                    /* resolved method is static but called with $ this*/
                    if (objResolvedRef instanceof Method) {
                        final Method method  = (Method) objResolvedRef;
                        final PhpClass clazz = method.getContainingClass();
                        /* non-static methods and contract interfaces must not be reported */
                        if (null == clazz || clazz.isInterface() || !method.isStatic() || method.isAbstract()) {
                            return;
                        }

                        /* PHP Unit's official docs saying to use $this, follow the guidance */
                        if (RESPECT_PHPUNIT_STANDARDS && clazz.getFQN().startsWith("\\PHPUnit_Framework_")) {
                            return;
                        }

                        /* check first pattern $this->static */
                        final PsiElement thisCandidate = reference.getFirstChild();
                        if (thisCandidate.getText().equals("$this")) {
                            /* find operator for quick-fix */
                            PsiElement operator = thisCandidate.getNextSibling();
                            if (operator instanceof PsiWhiteSpace) {
                                operator = operator.getNextSibling();
                            }

                            final String message = messageThisUsed.replace("%m%", methodName);
                            holder.registerProblem(thisCandidate, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new TheLocalFix(thisCandidate, operator));

                            return;
                        }

                        /* check second pattern <expression>->static */
                        final PsiElement objectExpression = reference.getFirstPsiChild();
                        if (null != objectExpression && !(objectExpression instanceof FunctionReference)) {
                            /* check operator */
                            PsiElement operator = objectExpression.getNextSibling();
                            if (operator instanceof PsiWhiteSpace) {
                                operator = operator.getNextSibling();
                            }
                            if (null == operator) {
                                return;
                            }

                            if (operator.getText().replaceAll("\\s+","").equals("->")) {
                                /* info: no local fix, people shall check this code */
                                final String message = messageExpressionUsed.replace("%m%", reference.getName());
                                holder.registerProblem(reference, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                            }
                        }
                    }
                }
            }
        };
    }

    private static class TheLocalFix implements LocalQuickFix {
        private SmartPsiElementPointer<PsiElement> variable;
        private SmartPsiElementPointer<PsiElement> operator;

        TheLocalFix(@NotNull PsiElement variable, @NotNull PsiElement operator) {
            super();
            final SmartPointerManager factory = SmartPointerManager.getInstance(variable.getProject());

            this.variable = factory.createSmartPsiElementPointer(variable, variable.getContainingFile());
            this.operator = factory.createSmartPsiElementPointer(operator, operator.getContainingFile());
        }

        @NotNull
        @Override
        public String getName() {
            return "Use static::";
        }

        @NotNull
        @Override
        public String getFamilyName() {
            return getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            final PsiElement expression = descriptor.getPsiElement().getParent();
            if (expression instanceof FunctionReference) {
                final PsiElement operator = this.operator.getElement();
                final PsiElement variable = this.variable.getElement();
                if (null == operator || null == variable) {
                    return;
                }

                //noinspection ConstantConditions I' sure NPE will not happen as pattern hardcoded
                operator.replace(PhpPsiElementFactory.createFromText(project, LeafPsiElement.class, "::"));
                variable.replace(PhpPsiElementFactory.createClassReference(project, "static"));
            }
        }
    }

    public JComponent createOptionsPanel() {
        return (new StaticInvocationViaThisInspector.OptionsPanel()).getComponent();
    }

    private class OptionsPanel {
        final private JPanel optionsPanel;

        final private JCheckBox respectPhpunitStandards;

        public OptionsPanel() {
            optionsPanel = new JPanel();
            optionsPanel.setLayout(new MigLayout());

            respectPhpunitStandards = new JCheckBox("Follow PHPUnit standards", RESPECT_PHPUNIT_STANDARDS);
            respectPhpunitStandards.addChangeListener(new ChangeListener() {
                public void stateChanged(ChangeEvent e) {
                    RESPECT_PHPUNIT_STANDARDS = respectPhpunitStandards.isSelected();
                }
            });
            optionsPanel.add(respectPhpunitStandards, "wrap");
        }

        public JPanel getComponent() {
            return optionsPanel;
        }
    }
}