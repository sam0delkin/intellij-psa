package com.github.sam0delkin.intellijpsa.language.javascript.methodArguments

import com.github.sam0delkin.intellijpsa.language.javascript.model.JsMethodArgumentProviderModel
import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSExpression
import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.lang.javascript.psi.JSParameterListElement
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.lang.javascript.psi.resolve.JSClassResolver
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

object JsMethodArgumentHelper {
    data class ProviderMatch(
        val provider: JsMethodArgumentProviderModel,
        val function: JSFunction,
    )

    data class ArgumentMapping(
        val value: JSExpression,
        val parameter: JSParameterListElement,
    )

    fun mapArgumentsToParameters(
        call: JSCallExpression,
        match: ProviderMatch,
    ): List<ArgumentMapping> {
        val parameters = match.function.parameters
        if (parameters.isEmpty()) return emptyList()

        val mappings = mutableListOf<ArgumentMapping>()
        call.arguments.forEachIndexed { index, argument ->
            val parameterIndex = index - match.provider.argumentsOffset
            if (parameterIndex < 0) return@forEachIndexed
            val parameter =
                parameters.getOrNull(parameterIndex)
                    ?: parameters.last().takeIf { it.isRest }
                    ?: return@forEachIndexed
            mappings.add(ArgumentMapping(argument, parameter))
        }
        return mappings
    }

    fun findProviderForMethodNameElement(
        element: PsiElement,
        providers: List<JsMethodArgumentProviderModel>,
    ): ProviderMatch? {
        val literal = element as? JSLiteralExpression ?: return null
        if (!literal.isStringLiteral) return null

        val call = PsiTreeUtil.getParentOfType(literal, JSCallExpression::class.java) ?: return null
        val args = call.arguments

        for (provider in providers) {
            if (!callMatches(call, provider)) continue
            if (args.getOrNull(provider.methodArgumentIndex) !== literal) continue

            val methodName = literal.stringValue ?: continue
            val function = resolveMethod(literal.project, provider.`class`, methodName) ?: continue
            return ProviderMatch(provider, function)
        }

        return null
    }

    fun findProviderForCall(
        call: JSCallExpression,
        providers: List<JsMethodArgumentProviderModel>,
    ): ProviderMatch? {
        val args = call.arguments
        for (provider in providers) {
            if (!callMatches(call, provider)) continue
            val methodNameElement = args.getOrNull(provider.methodArgumentIndex) as? JSLiteralExpression ?: continue
            if (!methodNameElement.isStringLiteral) continue
            val methodName = methodNameElement.stringValue ?: continue
            val function = resolveMethod(call.project, provider.`class`, methodName) ?: continue
            return ProviderMatch(provider, function)
        }
        return null
    }

    fun findProviderForMethodNamePosition(
        element: PsiElement,
        providers: List<JsMethodArgumentProviderModel>,
    ): JsMethodArgumentProviderModel? {
        val literal =
            PsiTreeUtil.getParentOfType(element, JSLiteralExpression::class.java, false)
                ?: element as? JSLiteralExpression
                ?: return null
        if (!literal.isStringLiteral) return null

        val call = PsiTreeUtil.getParentOfType(literal, JSCallExpression::class.java) ?: return null
        val args = call.arguments

        return providers.firstOrNull { provider ->
            callMatches(call, provider) && args.getOrNull(provider.methodArgumentIndex) === literal
        }
    }

    fun methodNames(
        project: Project,
        className: String,
    ): List<String> {
        if (className.isEmpty()) return emptyList()
        return methodNamesCache(project).computeIfAbsent(className) { methodNamesUncached(project, it) }
    }

    private fun methodNamesUncached(
        project: Project,
        className: String,
    ): List<String> {
        val scope = GlobalSearchScope.allScope(project)
        val resolver = JSClassResolver.getInstance()
        val names = LinkedHashSet<String>()

        runCatching { resolver.findClassesByQName(className, scope) }
            .getOrNull()
            .orEmpty()
            .forEach { jsClass -> jsClass.functions.forEach { it.name?.let(names::add) } }

        findConstructors(project, className).forEach { constructor ->
            prototypeReferences(constructor).forEach { reference ->
                collectPrototypeMembers(reference, names)
            }
        }

        return names.toList()
    }

    private fun collectPrototypeMembers(
        referenceElement: PsiElement,
        names: MutableSet<String>,
    ) {
        val classReference = referenceElement as? JSReferenceExpression ?: return
        val prototypeReference = classReference.parent as? JSReferenceExpression ?: return
        if (prototypeReference.referenceName != "prototype") return

        (prototypeReference.parent as? JSReferenceExpression)?.referenceName?.let(names::add)

        val call = PsiTreeUtil.getParentOfType(prototypeReference, JSCallExpression::class.java) ?: return
        if (!PsiTreeUtil.isAncestor(call.argumentList, prototypeReference, false)) return
        call.arguments
            .filterIsInstance<JSObjectLiteralExpression>()
            .flatMap { it.properties.toList() }
            .forEach { it.name?.let(names::add) }
    }

    private fun callMatches(
        call: JSCallExpression,
        provider: JsMethodArgumentProviderModel,
    ): Boolean {
        val reference = call.methodExpression as? JSReferenceExpression ?: return false
        return reference.referenceName == provider.referenceName
    }

    private fun resolveMethod(
        project: Project,
        className: String,
        methodName: String,
    ): JSFunction? {
        if (className.isEmpty() || methodName.isEmpty()) return null
        val cached =
            resolveMethodCache(project).computeIfAbsent(className to methodName) {
                Optional.ofNullable(resolveMethodUncached(project, it.first, it.second))
            }
        val function = cached.orElse(null)
        return if (function != null && function.isValid) function else null
    }

    private fun resolveMethodUncached(
        project: Project,
        className: String,
        methodName: String,
    ): JSFunction? {
        val scope = GlobalSearchScope.allScope(project)
        val resolver = JSClassResolver.getInstance()

        val qualifiedNames = listOf("$className.prototype.$methodName", "$className.$methodName")
        for (qualifiedName in qualifiedNames) {
            val elements =
                runCatching { resolver.findElementsByQNameIncludingImplicit(qualifiedName, scope) }
                    .getOrNull()
                    .orEmpty()
            elements.firstNotNullOfOrNull { functionOf(it) }?.let { return it }
        }

        runCatching { resolver.findClassesByQName(className, scope) }
            .getOrNull()
            .orEmpty()
            .firstNotNullOfOrNull { it.findFunctionByName(methodName) }
            ?.let { return it }

        findConstructors(project, className).forEach { constructor ->
            prototypeReferences(constructor).forEach { reference ->
                prototypeMemberFunction(reference, methodName)?.let { return it }
            }
        }

        return null
    }

    private fun findConstructors(
        project: Project,
        className: String,
    ): List<PsiNamedElement> =
        runCatching {
            JSClassResolver
                .getInstance()
                .findElementsByNameIncludingImplicit(className, GlobalSearchScope.allScope(project))
        }.getOrNull()
            .orEmpty()
            .filterIsInstance<PsiNamedElement>()
            .filter { it.name == className }

    private fun prototypeReferences(constructor: PsiNamedElement): List<JSReferenceExpression> {
        val file = constructor.containingFile ?: return emptyList()
        val fileScope = GlobalSearchScope.fileScope(file)
        val references = mutableListOf<JSReferenceExpression>()
        runCatching {
            ReferencesSearch.search(constructor, fileScope).forEach { reference ->
                (reference.element as? JSReferenceExpression)?.let(references::add)
            }
        }
        return references
    }

    private fun prototypeMemberFunction(
        referenceElement: PsiElement,
        methodName: String,
    ): JSFunction? {
        val classReference = referenceElement as? JSReferenceExpression ?: return null
        val prototypeReference = classReference.parent as? JSReferenceExpression ?: return null
        if (prototypeReference.referenceName != "prototype") return null

        val memberReference = prototypeReference.parent as? JSReferenceExpression
        if (memberReference?.referenceName == methodName) {
            functionOf(memberReference)?.let { return it }
            functionOf(memberReference.parent)?.let { return it }
        }

        val call = PsiTreeUtil.getParentOfType(prototypeReference, JSCallExpression::class.java) ?: return null
        if (!PsiTreeUtil.isAncestor(call.argumentList, prototypeReference, false)) return null
        return call.arguments
            .filterIsInstance<JSObjectLiteralExpression>()
            .flatMap { it.properties.toList() }
            .firstOrNull { it.name == methodName }
            ?.let { functionOf(it) }
    }

    private fun functionOf(element: PsiElement?): JSFunction? {
        element ?: return null
        if (element is JSFunction) return element
        return PsiTreeUtil.getChildOfType(element, JSFunction::class.java)
            ?: PsiTreeUtil.getChildOfType(element.parent, JSFunction::class.java)
    }

    // The map value type must be a JDK type, not a class defined by this plugin: this cache is
    // keyed on the Project (via CachedValuesManager) and survives until the next PSI change, so a
    // plugin-defined value type here would keep the plugin's classloader reachable and block a
    // clean dynamic plugin reload/update. java.util.Optional (unlike a custom nullable-wrapper
    // class) lets ConcurrentHashMap cache a "no match" result without introducing one.
    private val RESOLVE_CACHE_KEY =
        Key.create<CachedValue<ConcurrentHashMap<Pair<String, String>, Optional<JSFunction>>>>("psa.js.resolveMethod")
    private val NAMES_CACHE_KEY =
        Key.create<CachedValue<ConcurrentHashMap<String, List<String>>>>("psa.js.methodNames")

    // internal (not private) so tests can assert the cache never stores a plugin-defined type.
    internal fun resolveMethodCache(project: Project): ConcurrentHashMap<Pair<String, String>, Optional<JSFunction>> =
        CachedValuesManager.getManager(project).getCachedValue(
            project,
            RESOLVE_CACHE_KEY,
            {
                CachedValueProvider.Result.create(
                    ConcurrentHashMap<Pair<String, String>, Optional<JSFunction>>(),
                    PsiModificationTracker.MODIFICATION_COUNT,
                )
            },
            false,
        )

    private fun methodNamesCache(project: Project): ConcurrentHashMap<String, List<String>> =
        CachedValuesManager.getManager(project).getCachedValue(
            project,
            NAMES_CACHE_KEY,
            {
                CachedValueProvider.Result.create(
                    ConcurrentHashMap<String, List<String>>(),
                    PsiModificationTracker.MODIFICATION_COUNT,
                )
            },
            false,
        )
}
