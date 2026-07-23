package com.github.sam0delkin.intellijpsa.language.velocity

import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Covers [PsaCompletionContributor.fillCompletionVariants].
 *
 * `PsaCompletionContributor.currentElement` is a JVM-wide (companion-object) static, not a
 * project-scoped service, and `Settings` is a light-fixture project service that persists across
 * test *methods* - both are reset in [tearDown] so state from one test can't leak into the next.
 *
 * All positive-path assertions drive real IDE completion (`myFixture.completeBasic()` on a `.vm`
 * file) rather than calling `fillCompletionVariants` directly, since building a working
 * `CompletionResultSet` by hand is impractical - this exercises the exact same code path a real
 * user would hit.
 */
class PsaCompletionContributorTest : BasePlatformTestCase() {
    override fun tearDown() {
        try {
            PsaCompletionContributor.currentElement = null
            project.service<Settings>().loadState(Settings())
        } finally {
            super.tearDown()
        }
    }

    private fun enablePlugin() {
        project.service<Settings>().apply {
            pluginEnabled = true
            scriptPath = ".psa/psa.php"
        }
    }

    private fun lookupStrings(): List<String> = myFixture.lookupElementStrings ?: emptyList()

    // --- CompletionModel reflection branch ---------------------------------------------------

    fun testCompletionReflectionBranchListsCompletionModelProperties() {
        enablePlugin()
        myFixture.configureByText("test.vm", "\$completion.<caret>")
        PsaCompletionContributor.currentElement = myFixture.file

        myFixture.completeBasic()

        val strings = lookupStrings()
        assertEquals(
            setOf("text", "bold", "presentableText", "tailText", "type", "priority", "link"),
            strings.toSet(),
        )
    }

    // --- PsiElementModel reflection branch ----------------------------------------------------

    fun testModelReflectionBranchListsPsiElementModelProperties() {
        enablePlugin()
        myFixture.configureByText("test.vm", "\$model.<caret>")
        PsaCompletionContributor.currentElement = myFixture.file

        myFixture.completeBasic()

        val strings = lookupStrings()
        assertEquals(
            setOf(
                "id",
                "elementType",
                "options",
                "elementName",
                "elementFqn",
                "elementSignature",
                "text",
                "parent",
                "prev",
                "next",
                "textRange",
                "filePath",
            ),
            strings.toSet(),
        )
    }

    // --- currentElement's own no-arg methods branch -------------------------------------------

    fun testElementReflectionBranchListsCurrentElementNoArgMethods() {
        enablePlugin()
        myFixture.configureByText("test.vm", "\$element.<caret>")
        PsaCompletionContributor.currentElement = myFixture.file

        myFixture.completeBasic()

        val strings = lookupStrings()
        assertTrue(strings.contains("getText()"))
        assertTrue(strings.contains("getProject()"))
    }

    // --- VtlMethodCallExpression / VtlIndexExpression dotted-chain reflection branch ----------

    fun testMethodCallChainReflectionBranchHappyPathResolvesFinalObjectMethods() {
        enablePlugin()
        // "$element.getViewProvider()." - position.prevSibling.prevSibling is a VtlMethodCallExpression
        // whose text is "element.getViewProvider()"; the chain resolves getViewProvider() on
        // currentElement via reflection and lists the resulting FileViewProvider's no-arg methods.
        myFixture.configureByText("test.vm", "\$element.getViewProvider().<caret>")
        PsaCompletionContributor.currentElement = myFixture.file

        myFixture.completeBasic()

        val strings = lookupStrings()
        assertTrue(strings.contains("getAllFiles()"))
        assertTrue(strings.contains("getVirtualFile()"))
    }

    fun testIndexExpressionChainReflectionBranchResolvesIterableIndexSegment() {
        enablePlugin()
        // "$element.getViewProvider().getAllFiles()[0]." - position.prevSibling.prevSibling is a
        // VtlIndexExpression. The chain resolves getViewProvider(), then the "getAllFiles()[0]"
        // segment is split by the `(.*)\[(\d+)\]` regex into method name "getAllFiles" + index 0;
        // getAllFiles() returns a List<PsiFile> (an Iterable), so `element.toList()[0]` picks the
        // single file back out - exercising the `null != index && element is Iterable<*>` branch.
        myFixture.configureByText("test.vm", "\$element.getViewProvider().getAllFiles()[0].<caret>")
        PsaCompletionContributor.currentElement = myFixture.file

        myFixture.completeBasic()

        val strings = lookupStrings()
        // getAllFiles()[0] resolves back to the (single) PsiFile itself, so its own no-arg methods
        // are listed - same shape as the plain "$element." branch above.
        assertTrue(strings.contains("getText()"))
        assertTrue(strings.contains("getViewProvider()"))
    }

    fun testMethodCallChainReflectionBranchStopsWhenMethodNotFoundOnChainSegment() {
        enablePlugin()
        // "nonExistentMethodXYZ" is not a real method on the PsiFile currentElement - the chain
        // resolution's `if (null == method) { error = true; break }` branch fires and nothing is
        // added.
        myFixture.configureByText("test.vm", "\$element.nonExistentMethodXYZ().<caret>")
        PsaCompletionContributor.currentElement = myFixture.file

        myFixture.completeBasic()

        assertTrue(lookupStrings().isEmpty())
    }

    fun testMethodCallChainReflectionBranchStopsGracefullyWhenAnIntermediateHopReturnsNull() {
        enablePlugin()
        // getFirstChild() resolves to the VtlFile's first child (non-null); getPrevSibling() on
        // that first child is a real, valid, zero-arg method that legitimately returns null (it
        // has no previous sibling). The *next* loop iteration then evaluates
        // `element?.javaClass?.methods?.find { ... }` with `element` itself null - exercising the
        // safe-call short-circuit path (as opposed to the "method not found on a non-null element"
        // path covered above) - which also resolves to `method == null` -> `error = true; break`.
        myFixture.configureByText("test.vm", "\$element.getFirstChild().getPrevSibling().foo().<caret>")
        PsaCompletionContributor.currentElement = myFixture.file

        myFixture.completeBasic()

        assertTrue(lookupStrings().isEmpty())
    }

    fun testMethodCallChainReflectionBranchCatchesInvocationThrowable() {
        enablePlugin()
        // "equals" resolves (by name only, regardless of parameter count) to
        // java.lang.Object#equals(Object), a 1-argument method. Invoking it via reflection with
        // zero arguments throws IllegalArgumentException, which must be caught by the
        // `catch (_: Throwable) { error = true; break }` branch rather than propagating.
        myFixture.configureByText("test.vm", "\$element.equals().<caret>")
        PsaCompletionContributor.currentElement = myFixture.file

        myFixture.completeBasic()

        assertTrue(lookupStrings().isEmpty())
    }

    // --- Early-return guards -------------------------------------------------------------------

    fun testReturnsEarlyWhenPluginDisabled() {
        project.service<Settings>().apply {
            pluginEnabled = false
            scriptPath = ".psa/psa.php"
        }
        myFixture.configureByText("test.vm", "\$completion.<caret>")
        PsaCompletionContributor.currentElement = myFixture.file

        myFixture.completeBasic()

        assertTrue(lookupStrings().isEmpty())
    }

    fun testReturnsEarlyWhenScriptPathIsNull() {
        project.service<Settings>().apply {
            pluginEnabled = true
            scriptPath = null
        }
        myFixture.configureByText("test.vm", "\$completion.<caret>")
        PsaCompletionContributor.currentElement = myFixture.file

        myFixture.completeBasic()

        assertTrue(lookupStrings().isEmpty())
    }

    fun testReturnsEarlyWhenCurrentElementIsNullForNonInjectedFile() {
        enablePlugin()
        myFixture.configureByText("test.vm", "\$completion.<caret>")
        // currentElement intentionally left null (its default) - the file is a plain (non-injected)
        // VirtualFile, so `originalFile !is VirtualFileWindow && null == currentElement` must return.

        myFixture.completeBasic()

        assertTrue(lookupStrings().isEmpty())
    }

    fun testReturnsEarlyWhenCaretPositionIsNotAVtlToken() {
        enablePlugin()
        // A plain-text file's tokens are never a VtlTokenType, so `position.elementType !is
        // VtlTokenType` must return regardless of currentElement being set.
        myFixture.configureByText("test.txt", "plain te<caret>xt")
        PsaCompletionContributor.currentElement = myFixture.file

        myFixture.completeBasic()

        assertTrue(lookupStrings().isEmpty())
    }

    // Note: the `originalFile is VirtualFileWindow && originalFile.delegate.path.indexOf(scriptDir)
    // < 0` branch (an injected-language fragment, e.g. Velocity embedded inside a PHP heredoc) is
    // not covered here - constructing a VirtualFileWindow requires a real injected-language host
    // with an actual language injector wired up for `.vm`-flavoured content, which nothing in this
    // codebase or its bundled test plugins provides. This is a documented, acceptable residual gap
    // per the task instructions.

    // Note: the `parts.size > 1` false branch (a dotted chain segment with no literal "." in it)
    // is also not covered - confirmed via manual probing that VTL has no bare `$foo()`-style
    // function-call syntax without a receiver (e.g. "$foo()" is not recognized as Velocity at all
    // and falls back to being parsed as plain template data), so a VtlMethodCallExpression's/
    // VtlIndexExpression's text always includes a dotted receiver prefix in practice. This appears
    // to be genuinely unreachable given real VTL grammar.
}
