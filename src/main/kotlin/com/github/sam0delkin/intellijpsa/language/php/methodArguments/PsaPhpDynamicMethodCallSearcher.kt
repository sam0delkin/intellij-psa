package com.github.sam0delkin.intellijpsa.language.php.methodArguments

import com.github.sam0delkin.intellijpsa.language.php.settings.PhpPsaSettings
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.components.service
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class PsaPhpDynamicMethodCallSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {
    override fun processQuery(
        params: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>,
    ) {
        val method = params.elementToSearch as? Method ?: return
        val project = method.project
        val settings = project.service<Settings>()
        val phpSettings = project.service<PhpPsaSettings>()
        val providers = phpSettings.methodArgumentProviders
        if (!settings.pluginEnabled || !phpSettings.enabled || providers.isNullOrEmpty()) return

        val methodName = method.name ?: return
        val scope = GlobalSearchScope.projectScope(project)

        PsiSearchHelper
            .getInstance(project)
            .processAllFilesWithWordInLiterals(methodName, scope) { file ->
                PsiTreeUtil
                    .findChildrenOfType(file, StringLiteralExpression::class.java)
                    .filter { it.contents == methodName }
                    .forEach { stringLiteralExpression ->
                        stringLiteralExpression.references.filterIsInstance<PsaPhpMethodReference>().forEach { ref ->
                            if (ref.isReferenceTo(method)) consumer.process(ref)
                        }
                    }
                true
            }
    }
}
