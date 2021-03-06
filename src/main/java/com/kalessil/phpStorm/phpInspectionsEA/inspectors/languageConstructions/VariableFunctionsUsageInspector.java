package com.kalessil.phpStorm.phpInspectionsEA.inspectors.languageConstructions;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.php.config.PhpLanguageFeature;
import com.jetbrains.php.config.PhpLanguageLevel;
import com.jetbrains.php.config.PhpProjectConfigurationFacade;
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression;
import com.jetbrains.php.lang.psi.elements.FunctionReference;
import com.jetbrains.php.lang.psi.elements.PhpPsiElement;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import com.jetbrains.php.lang.psi.elements.impl.StatementImpl;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.BasePhpElementVisitor;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.BasePhpInspection;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.LinkedList;

public class VariableFunctionsUsageInspector extends BasePhpInspection {
    @NotNull
    public String getShortName() {
        return "VariableFunctionsUsageInspection";
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return new BasePhpElementVisitor() {
            public void visitPhpFunctionCall(FunctionReference reference) {
                /* process `call()>;<` expressions only */
                if (!(reference.getParent() instanceof StatementImpl)) {
                    return;
                }

                /* general requirements for calls */
                final String function         = reference.getName();
                final PsiElement[] parameters = reference.getParameters();
                if (0 == parameters.length || StringUtil.isEmpty(function) || !function.startsWith("call_user_func")) {
                    return;
                }

                /* only `call_user_func_array(..., array(...))` needs to be checked */
                if (2 == parameters.length && function.equals("call_user_func_array")) {
                    if (parameters[1] instanceof ArrayCreationExpression) {
                        holder.registerProblem(reference, "'call_user_func(...)' should be used instead", ProblemHighlightType.WEAK_WARNING);
                    }

                    return;
                }

                if (function.equals("call_user_func")) {
                    /* `callReturningCallable()(...)` is not possible -> syntax error */
                    if (parameters[0] instanceof FunctionReference) {
                        return;
                    }

                    if (parameters[0] instanceof ArrayCreationExpression) {
                        final ArrayCreationExpression callable = (ArrayCreationExpression) parameters[0];

                        /* get array values */
                        final LinkedList<PhpPsiElement> values = new LinkedList<>();
                        PhpPsiElement value                    = callable.getFirstPsiChild();
                        while (null != value) {
                            values.add(value.getFirstPsiChild());
                            value = value.getNextPsiSibling();
                        }

                        /* ensure we have 2 values array and first is not a callable reference */
                        if (
                            2 == values.size()
                            && null != values.get(0) && null != values.get(1)
                            && !(values.get(0) instanceof FunctionReference)
                        ) {
                            final LinkedList<String> parametersToSuggest = new LinkedList<>();
                            for (PsiElement parameter : Arrays.copyOfRange(parameters, 1, parameters.length)) {
                                parametersToSuggest.add(parameter.getText());
                            }

                            final boolean isFirstString  = values.get(0) instanceof StringLiteralExpression;
                            final boolean isSecondString = values.get(1) instanceof StringLiteralExpression;
                            /* some non-trivial magic eg `parent::call()` is not available via var functions */
                            if (isSecondString && ((StringLiteralExpression) values.get(1)).getContents().contains("::")) {
                                return;
                            }

                            /* as usually personalization of messages is overcomplicated */
                            final String message = "'%o%->{%m%}(%p%)' should be used instead"
                                .replace("%p%", StringUtil.join(parametersToSuggest, ", "))
                                .replace(
                                    isFirstString  ? "%o%->" : "%o%",
                                    isFirstString  ? ((StringLiteralExpression) values.get(0)).getContents() + "::" : values.get(0).getText()
                                )
                                .replace(
                                    isSecondString ? "{%m%}" : "%m%",
                                    isSecondString ? ((StringLiteralExpression) values.get(1)).getContents()        : values.get(1).getText()
                                );
                            parametersToSuggest.clear();

                            holder.registerProblem(reference, message, ProblemHighlightType.WEAK_WARNING);
                        }
                    } else {
                        final PhpLanguageLevel phpVersion = PhpProjectConfigurationFacade.getInstance(holder.getProject()).getLanguageLevel();
                        if (phpVersion.hasFeature(PhpLanguageFeature.SCALAR_TYPE_HINTS)) { // PHP7 and newer
                            /* in PHP7+ it's absolutely safe to use variable functions */
                            final LinkedList<String> parametersToSuggest = new LinkedList<>();
                            for (PsiElement parameter : Arrays.copyOfRange(parameters, 1, parameters.length)) {
                                parametersToSuggest.add(parameter.getText());
                            }

                            final String message = "'%c%(%p%)' should be used instead"
                                    .replace("%c%", parameters[0].getText())
                                    .replace("%p%", StringUtil.join(parametersToSuggest, ", "));
                            parametersToSuggest.clear();

                            holder.registerProblem(reference, message, ProblemHighlightType.WEAK_WARNING);
                        }
                    }
                }
            }
        };
    }
}

