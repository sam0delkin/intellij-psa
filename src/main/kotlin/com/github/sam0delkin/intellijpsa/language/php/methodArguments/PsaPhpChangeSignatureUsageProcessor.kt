package com.github.sam0delkin.intellijpsa.language.php.methodArguments

import com.github.sam0delkin.intellijpsa.language.php.model.MethodArgumentProviderModel
import com.github.sam0delkin.intellijpsa.language.php.settings.PhpPsaSettings
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.changeSignature.ChangeInfo
import com.intellij.refactoring.changeSignature.ChangeSignatureUsageProcessor
import com.intellij.refactoring.rename.ResolveSnapshotProvider
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import com.jetbrains.php.lang.psi.PhpPsiElementFactory
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.refactoring.changeSignature.PhpChangeInfo

class PsaPhpChangeSignatureUsageProcessor : ChangeSignatureUsageProcessor {
    override fun findUsages(info: ChangeInfo): Array<UsageInfo> {
        val phpInfo = info as? PhpChangeInfo ?: return emptyArray()
        val method = phpInfo.method ?: return emptyArray()
        val project = method.project
        val settings = project.service<Settings>()
        val phpSettings = project.service<PhpPsaSettings>()
        val providers = phpSettings.methodArgumentProviders

        if (!settings.pluginEnabled || !phpSettings.enabled || providers.isNullOrEmpty()) return emptyArray()

        val methodName = method.name ?: return emptyArray()
        val result = mutableListOf<UsageInfo>()

        val scope = GlobalSearchScope.projectScope(project)
        PsiSearchHelper.getInstance(project).processAllFilesWithWordInLiterals(methodName, scope) { file ->
            collectFromFile(file, methodName, providers, result)
            true
        }

        collectFromFile(method.containingFile, methodName, providers, result)

        ReferencesSearch.search(method, scope).forEach { ref ->
            if (ref !is PsaPhpMethodReference) return@forEach
            val stringLiteralExpression = ref.element as? StringLiteralExpression ?: return@forEach
            if (result.any { (it as? PsaPhpDynamicCallUsageInfo)?.array?.containingFile == stringLiteralExpression.containingFile }) {
                return@forEach
            }
            val array = findArgumentsArray(stringLiteralExpression, providers) ?: return@forEach
            val provider =
                PsaPhpMethodArgumentHelper.findProviderForArgumentsArray(array, providers)?.provider
                    ?: return@forEach
            result.add(PsaPhpDynamicCallUsageInfo(array, provider))
        }

        return result.toTypedArray()
    }

    override fun processUsage(
        changeInfo: ChangeInfo,
        usageInfo: UsageInfo,
        beforeMethodChange: Boolean,
        usages: Array<UsageInfo>,
    ): Boolean {
        if (beforeMethodChange) return false
        val usage = usageInfo as? PsaPhpDynamicCallUsageInfo ?: return false
        val phpInfo = changeInfo as? PhpChangeInfo ?: return false
        applyToArgumentsArray(phpInfo, usage.array, usage.provider)

        return true
    }

    override fun processPrimaryMethod(changeInfo: ChangeInfo): Boolean = false

    override fun findConflicts(
        info: ChangeInfo,
        refUsages: Ref<Array<UsageInfo>>,
    ): MultiMap<PsiElement, String> = MultiMap.empty()

    override fun shouldPreviewUsages(
        changeInfo: ChangeInfo,
        usages: Array<UsageInfo>,
    ): Boolean = false

    override fun setupDefaultValues(
        changeInfo: ChangeInfo,
        refUsages: Ref<Array<UsageInfo>>,
        project: Project,
    ): Boolean = false

    override fun registerConflictResolvers(
        snapshots: MutableList<in ResolveSnapshotProvider.ResolveSnapshot>,
        snapshotProvider: ResolveSnapshotProvider,
        usages: Array<UsageInfo>,
        changeInfo: ChangeInfo,
    ) {
    }

    private fun applyToArgumentsArray(
        changeInfo: PhpChangeInfo,
        array: ArrayCreationExpression,
        provider: MethodArgumentProviderModel,
    ) {
        val project = array.project
        val newParams = changeInfo.newParameters
        val currentValues = arrayValues(array)

        val parts = mutableListOf<String>()
        for (i in newParams.indices) {
            val arrayIndex = i - provider.argumentsOffset
            if (arrayIndex < 0) continue

            val param = newParams[i]
            if (param.oldIndex == -1) {
                parts.add(param.defaultValue?.takeIf { it.isNotEmpty() } ?: "null")
            } else {
                val oldArrayIndex = param.oldIndex - provider.argumentsOffset
                val existing = currentValues.getOrNull(oldArrayIndex) ?: continue
                parts.add(existing.text)
            }
        }

        val newArray =
            PhpPsiElementFactory.createFromText(
                project,
                ArrayCreationExpression::class.java,
                "[${parts.joinToString(", ")}]",
            ) ?: return
        array.replace(newArray)
    }

    private fun collectFromFile(
        file: PsiFile,
        methodName: String,
        providers: List<MethodArgumentProviderModel>,
        result: MutableList<UsageInfo>,
    ) {
        val allLiterals = PsiTreeUtil.findChildrenOfType(file, StringLiteralExpression::class.java)
        val allMagicConstants =
            PsiTreeUtil
                .findChildrenOfType(file, com.jetbrains.php.lang.psi.elements.ConstantReference::class.java)
                .filter { it.name == "__METHOD__" || it.name == "__FUNCTION__" }

        (allLiterals + allMagicConstants)
            .filter { PsaPhpMethodArgumentHelper.extractStringContents(it) == methodName }
            .forEach { methodNameElement ->
                PsaPhpMethodArgumentHelper.findProviderForMethodNameElement(methodNameElement, providers)
                    ?: return@forEach
                val array = findArgumentsArray(methodNameElement, providers) ?: return@forEach
                val provider =
                    PsaPhpMethodArgumentHelper.findProviderForArgumentsArray(array, providers)?.provider
                        ?: return@forEach
                if (result.none { (it as? PsaPhpDynamicCallUsageInfo)?.array === array }) {
                    result.add(PsaPhpDynamicCallUsageInfo(array, provider))
                }
            }
    }

    private fun findArgumentsArray(
        methodNameElement: PsiElement,
        providers: List<MethodArgumentProviderModel>,
    ): ArrayCreationExpression? {
        for (provider in providers) {
            val paramList: ParameterList? =
                when {
                    provider.callableArgumentIndex != null -> {
                        val callableArray =
                            PsiTreeUtil.getParentOfType(
                                methodNameElement,
                                ArrayCreationExpression::class.java,
                            )
                        callableArray?.parent as? ParameterList
                    }

                    provider.methodArgumentIndex != null -> {
                        methodNameElement.parent as? ParameterList
                    }

                    else -> {
                        null
                    }
                }

            return paramList?.parameters?.getOrNull(provider.argumentsArgumentIndex) as? ArrayCreationExpression
        }

        return null
    }

    private fun arrayValues(array: ArrayCreationExpression): List<PsiElement> =
        array.children.filter {
            it !is PsiWhiteSpace && it.text != "," && it.text != "[" && it.text != "]" && it.text.isNotBlank()
        }
}
