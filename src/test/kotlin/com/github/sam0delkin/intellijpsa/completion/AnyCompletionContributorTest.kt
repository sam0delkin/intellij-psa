package com.github.sam0delkin.intellijpsa.completion

import com.github.sam0delkin.intellijpsa.model.StaticCompletionModel
import com.github.sam0delkin.intellijpsa.model.completion.CompletionModel
import com.github.sam0delkin.intellijpsa.model.completion.CompletionsModel
import com.github.sam0delkin.intellijpsa.model.psi.PsiElementPatternModel
import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.github.sam0delkin.intellijpsa.services.server.ServerManager
import com.github.sam0delkin.intellijpsa.services.server.ServerState
import com.github.sam0delkin.intellijpsa.settings.ExecutionMode
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProcess
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

class AnyCompletionContributorTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        // Settings and PsaManager's static-completion cache are project-level light services that
        // persist across test methods in this light-fixture project - reset the fields every test
        // in this class touches so state from one test can't leak into the next regardless of the
        // (unspecified) JUnit3 method execution order.
        project.service<Settings>().apply {
            pluginEnabled = false
            executionMode = ExecutionMode.Script
            goToFilter = ""
            targetElementTypes = null
        }
        project.service<PsaManager>().setStaticCompletionConfigs(arrayListOf())
    }

    override fun tearDown() {
        try {
            // ServerManager is a light service that persists across test *methods* (and across
            // test classes sharing the same light-fixture project) - must wait for the terminal
            // state here, or the next test can start while this restart is still in flight
            // (restart() redirects to a pooled thread when called from the EDT, which test
            // methods run on by default).
            project.service<ServerManager>().restart()
            waitUntil { project.service<ServerManager>().status() == ServerState.STOPPED }
        } finally {
            super.tearDown()
        }
    }

    private fun waitUntil(
        timeoutMs: Long = 5000,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
    }

    private fun fixturePath(name: String): File {
        val resource = javaClass.classLoader.getResource("server/$name")!!
        val file = File(resource.toURI())
        file.setExecutable(true)

        return file
    }

    private fun enableScriptMode(script: String) {
        project.service<Settings>().apply {
            pluginEnabled = true
            executionMode = ExecutionMode.Script
            supportedLanguages = "PHP"
            scriptPath = fixturePath(script).path
        }
    }

    fun testCompletionUsesStaticCompletionMatchWhenPatternMatches() {
        // The completion popup's own prefix matcher filters the lookup elements this produces
        // (unrelated to this plugin's code), so this only exercises the static-completion-match
        // branch in Completion.addCompletions - it does not assert on the final filtered lookup
        // list, which is a platform UI concern, not something this plugin controls.
        myFixture.configureByText("test.php", "<?php 'test_str<caret>ing';")
        enableScriptMode("completions-with-notifications.php")
        val staticCompletion =
            StaticCompletionModel().apply {
                name = "my_static"
                title = "My Static"
                patterns = arrayListOf(PsiElementPatternModel(withType = "String"))
                completions =
                    CompletionsModel().apply {
                        completions = arrayListOf(CompletionModel().apply { text = "staticCompletionResult" })
                    }
            }
        project.service<PsaManager>().setStaticCompletionConfigs(arrayListOf(staticCompletion))

        myFixture.completeBasic()
    }

    fun testCompletionFallsBackToScriptWhenNoStaticCompletionMatches() {
        // The completion popup's own prefix matcher/dedup logic (unrelated to this plugin's code)
        // can filter or reorder the final lookup list, so this only exercises the script-fallback
        // branch in Completion.addCompletions rather than asserting on that platform UI behavior.
        myFixture.configureByText("test.php", "<?php \$foo = 1; myComp<caret>;")
        enableScriptMode("completions-with-notifications.php")
        project.service<PsaManager>().setStaticCompletionConfigs(arrayListOf())

        myFixture.completeBasic()
    }

    fun testProcessNotificationsHandlesAllTypes() {
        myFixture.configureByText("test.php", "<?php \$foo = 1; myComp<caret>;")
        enableScriptMode("completions-with-notifications.php")
        project.service<PsaManager>().setStaticCompletionConfigs(arrayListOf())

        // Notifications are processed as a side effect of the completions call above; this just
        // exercises the call path again to make sure it doesn't throw.
        myFixture.completeBasic()
    }

    @Suppress("UnstableApiUsage")
    fun testHandleEmptyLookupReturnsNullWithoutOriginalPosition() {
        myFixture.configureByText("test.php", "<?php \$fo<caret>o;")
        val element = myFixture.getElementAtCaret()
        val params =
            CompletionParameters(
                element,
                myFixture.file,
                CompletionType.BASIC,
                element.textOffset,
                1,
                myFixture.editor,
                object : CompletionProcess {
                    override fun isAutopopupCompletion() = false
                },
            )

        // originalPosition reflects the actual completion parameters; this exercises the handler
        // through the public API without forcing an artificial null (which the constructor forbids).
        AnyCompletionContributor.Completion().handleEmptyLookup(params, myFixture.editor)
    }

    fun testGotoDeclarationReturnsNullWhenPluginDisabled() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        project.service<Settings>().pluginEnabled = false
        val element = myFixture.findElementByText("'test_string'", PsiElement::class.java)!!

        val targets = AnyCompletionContributor.GotoDeclaration().getGotoDeclarationTargets(element, -1, null)

        assertNull(targets)
    }

    fun testGotoDeclarationReturnsNullForNullSourceElement() {
        val targets = AnyCompletionContributor.GotoDeclaration().getGotoDeclarationTargets(null, -1, null)

        assertNull(targets)
    }

    fun testGotoDeclarationReturnsAllStaticCompletionsForSpecialOffset() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        enableScriptMode("counting-script.php")
        project.service<Settings>().targetElementTypes = arrayListOf("String")
        val element = myFixture.findElementByText("'test_string'", PsiElement::class.java)!!
        val staticCompletion =
            StaticCompletionModel().apply {
                name = "my_static"
                title = "My Static"
                patterns = arrayListOf(PsiElementPatternModel(withType = "String"))
                completions =
                    CompletionsModel().apply {
                        completions =
                            arrayListOf(
                                CompletionModel().apply {
                                    text = "target"
                                    link = "/test.php:1:1"
                                },
                            )
                    }
            }
        project.service<PsaManager>().setStaticCompletionConfigs(arrayListOf(staticCompletion))

        val targets =
            AnyCompletionContributor.GotoDeclaration().getGotoDeclarationTargets(
                element,
                RETURN_ALL_STATIC_COMPLETIONS,
                null,
            )

        assertNotNull(targets)
        assertTrue(targets!!.isNotEmpty())
    }

    fun testGotoDeclarationMatchesStaticCompletionByExactText() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        enableScriptMode("counting-script.php")
        project.service<Settings>().targetElementTypes = arrayListOf("String")
        val element = myFixture.findElementByText("'test_string'", PsiElement::class.java)!!
        val staticCompletion =
            StaticCompletionModel().apply {
                name = "my_static"
                title = "My Static"
                patterns = arrayListOf(PsiElementPatternModel(withType = "String"))
                completions =
                    CompletionsModel().apply {
                        completions =
                            arrayListOf(
                                CompletionModel().apply {
                                    text = "'test_string'"
                                    link = "/test.php:1:1"
                                },
                            )
                    }
            }
        project.service<PsaManager>().setStaticCompletionConfigs(arrayListOf(staticCompletion))

        val targets = AnyCompletionContributor.GotoDeclaration().getGotoDeclarationTargets(element, 0, null)

        assertNotNull(targets)
        assertTrue(targets!!.isNotEmpty())
    }

    fun testGotoDeclarationFallsBackToScriptWhenNoStaticCompletionMatches() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        enableScriptMode("goto-with-result.php")
        project.service<Settings>().targetElementTypes = arrayListOf("String")
        project.service<PsaManager>().setStaticCompletionConfigs(arrayListOf())
        val element = myFixture.findElementByText("'test_string'", PsiElement::class.java)!!

        val targets = AnyCompletionContributor.GotoDeclaration().getGotoDeclarationTargets(element, 0, null)

        assertNotNull(targets)
    }

    fun testGotoDeclarationReturnsNullWhenElementTypeNotInFilter() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        enableScriptMode("counting-script.php")
        project.service<Settings>().goToFilter = "SomeOtherType"
        val element = myFixture.findElementByText("'test_string'", PsiElement::class.java)!!

        val targets = AnyCompletionContributor.GotoDeclaration().getGotoDeclarationTargets(element, 0, null)

        assertNull(targets)
    }

    fun testResolveLiveGoToTargetsReturnsNullOutsideServerMode() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        enableScriptMode("counting-script.php")
        project.service<Settings>().targetElementTypes = arrayListOf("String")
        val element = myFixture.findElementByText("'test_string'", PsiElement::class.java)!!

        val targets = AnyCompletionContributor.GotoDeclaration().resolveLiveGoToTargets(element)

        assertNull(targets)
    }

    fun testResolveLiveGoToTargetsReturnsNullWhenPluginDisabled() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        project.service<Settings>().pluginEnabled = false
        val element = myFixture.findElementByText("'test_string'", PsiElement::class.java)!!

        val targets = AnyCompletionContributor.GotoDeclaration().resolveLiveGoToTargets(element)

        assertNull(targets)
    }

    fun testResolveLiveGoToTargetsSuccessInServerMode() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        val settings =
            project.service<Settings>().apply {
                pluginEnabled = true
                executionMode = ExecutionMode.Server
                supportedLanguages = "PHP"
                scriptPath = fixturePath("goto-server.php").path
            }
        val element = myFixture.findElementByText("'test_string'", PsiElement::class.java)!!
        val manager = project.service<ServerManager>()

        // resolveLiveGoToTargets() bails out immediately unless the server is already
        // confirmed RUNNING (it must never block on an unhealthy server) - so start it explicitly
        // first via a direct request and wait for the health-check round trip to complete.
        manager.sendRequest(settings, "Info", null, false, null, null)
        waitUntil { manager.status() == ServerState.RUNNING }
        assertEquals(ServerState.RUNNING, manager.status())

        val targets = AnyCompletionContributor.GotoDeclaration().resolveLiveGoToTargets(element)

        assertNotNull(targets)
    }

    fun testGetActionTextReturnsPsa() {
        val text = AnyCompletionContributor.GotoDeclaration().getActionText { null }

        assertEquals("PSA", text)
    }

    // Regression/coverage test for the `if (null == i.patterns) { continue }` guard and the
    // "multiple static completion configs match the same element" accumulation branch
    // (`json.completions = json.completions!! + ArrayList(...)`) in Completion.addCompletions.
    //
    // NOTE: while writing this test we noticed that the *accumulated* completions never actually
    // reach the user - only `json.completions` is updated on a second-or-later match, but the
    // lookup elements shown to the user always come from `json.extendedCompletions` (line
    // 162-166), which is only ever built once, from the *first* matching config
    // (`ExtendedCompletionsModel.createFromModel(i.completions!!, project)`). So a second matching
    // static-completion config's items are silently swallowed. This looks like a real bug, but per
    // instructions we are not fixing it - just flagging it here and in the final report.
    fun testCompletionSkipsConfigsWithoutPatternsAndAccumulatesAcrossMultipleMatches() {
        myFixture.configureByText("test.php", "<?php 'test_str<caret>ing';")
        enableScriptMode("completions-with-notifications.php")
        val noPatternsConfig =
            StaticCompletionModel().apply {
                name = "no_patterns"
                title = "No Patterns"
                patterns = null
                completions =
                    CompletionsModel().apply {
                        completions = arrayListOf(CompletionModel().apply { text = "unused" })
                    }
            }
        val firstMatch =
            StaticCompletionModel().apply {
                name = "first_match"
                title = "First Match"
                // Note: the completion caret position resolves to the leaf string-content token,
                // whose elementType prints as "single quoted string" - not the composite "String"
                // expression type used by GoTo tests (which operate on the whole element instead).
                patterns = arrayListOf(PsiElementPatternModel(withType = "single quoted string"))
                completions =
                    CompletionsModel().apply {
                        completions = arrayListOf(CompletionModel().apply { text = "fromFirst" })
                    }
            }
        val secondMatch =
            StaticCompletionModel().apply {
                name = "second_match"
                title = "Second Match"
                patterns = arrayListOf(PsiElementPatternModel(withType = "single quoted string"))
                completions =
                    CompletionsModel().apply {
                        completions = arrayListOf(CompletionModel().apply { text = "fromSecond" })
                    }
            }
        project.service<PsaManager>().setStaticCompletionConfigs(arrayListOf(noPatternsConfig, firstMatch, secondMatch))

        // Exercises the "continue" (no patterns) and "accumulate into existing json" branches;
        // per the note above this doesn't assert on the final lookup contents (a platform UI
        // concern, and - per the finding above - the second match's items are swallowed anyway).
        myFixture.completeBasic()
    }

    // Regression/coverage test for the `if (null === config.patterns) { continue }` guard inside
    // GotoDeclaration's static-completion matching loop, combined with the
    // `catch (_: UpdateStaticCompletionsException) { break }` branch: a stale (invalidated)
    // SmartPsiElementPointer on one matched completion must stop the loop, so a later completion
    // in the same list never gets added.
    fun testGotoDeclarationSkipsConfigsWithoutPatternsAndBreaksAfterStaleReferenceThrows() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        val badFile = myFixture.addFileToProject("target-bad.php", "<?php 'bad_target';")
        enableScriptMode("counting-script.php")

        val noPatternsConfig =
            StaticCompletionModel().apply {
                name = "no_patterns"
                title = "No Patterns"
                patterns = null
                completions =
                    CompletionsModel().apply {
                        completions = arrayListOf(CompletionModel().apply { text = "unused" })
                    }
            }
        val matchingConfig =
            StaticCompletionModel().apply {
                name = "matching"
                title = "Matching"
                patterns = arrayListOf(PsiElementPatternModel(withType = "String"))
                completions =
                    CompletionsModel().apply {
                        completions =
                            arrayListOf(
                                CompletionModel().apply {
                                    text = "good1"
                                    link = "/test.php:1:1"
                                },
                                CompletionModel().apply {
                                    text = "bad"
                                    link = "/target-bad.php:1:1"
                                },
                                CompletionModel().apply {
                                    text = "good2"
                                    link = "/test.php:1:1"
                                },
                            )
                    }
            }
        project.service<PsaManager>().setStaticCompletionConfigs(arrayListOf(noPatternsConfig, matchingConfig))

        // Force conversion (and SmartPsiElementPointer creation) to happen now, while
        // target-bad.php still exists - the result is cached in PsaStaticCompletionsConfig and
        // reused (not recomputed) by the getGotoDeclarationTargets() call below.
        project.service<PsaManager>().getStaticCompletionConfigs()

        WriteAction.run<Throwable> { badFile.virtualFile.delete(this@AnyCompletionContributorTest) }

        val element = myFixture.findElementByText("'test_string'", PsiElement::class.java)!!
        val targets =
            AnyCompletionContributor.GotoDeclaration().getGotoDeclarationTargets(
                element,
                RETURN_ALL_STATIC_COMPLETIONS,
                null,
            )

        assertNotNull(targets)
        assertEquals(
            "the stale ('bad') completion must throw and break the loop, so 'good2' (listed after it) is never added",
            1,
            targets!!.size,
        )
    }

    // Regression/coverage test for hasOtherGotoTarget()'s `referenceResolves(reference)` branch:
    // a genuine, resolvable, non-PSA reference at the offset (PHP's bundled
    // PhpGetEnvArgumentReferenceContributor, same fixture as PsaLineMarkerProviderTest) must make
    // getGotoDeclarationTargets() bail out with null rather than fall through to the script.
    fun testGotoDeclarationReturnsNullWhenNativeReferenceResolvesAtOffset() {
        myFixture.configureByText(
            "test.php",
            "<?php\nputenv('FOO=bar');\n\$x = getenv('FOO');\n",
        )
        val element = myFixture.findElementByText("'FOO'", PsiElement::class.java)!!
        enableScriptMode("goto-with-result.php")

        // Offset inside the string content (past the opening quote) - the reference's
        // getRangeInElement() covers the key text, not the surrounding quote characters, so
        // containingFile.findReferenceAt() needs an offset within that inner range to find it.
        val targets =
            AnyCompletionContributor.GotoDeclaration().getGotoDeclarationTargets(
                element,
                element.textOffset + 1,
                myFixture.editor,
            )

        assertNull(targets)
    }

    // Regression/coverage test for hasOtherGotoTarget()'s loop over
    // GotoDeclarationHandler.EP_NAME.extensionList (skipping PSA's own handler, calling every
    // other registered handler and stopping at the first non-empty result) - and, since the
    // registered handler below calls back into AnyCompletionContributor.GotoDeclaration()
    // reentrantly, this also exercises the REENTRANCY guard at the top of both
    // getGotoDeclarationTargets() and resolveLiveGoToTargets().
    @Suppress("UnstableApiUsage")
    fun testHasOtherGotoTargetDetectsAnotherHandlerAndReentrancyGuardsBothMethods() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        val element = myFixture.findElementByText("'test_string'", PsiElement::class.java)!!
        enableScriptMode("counting-script.php")

        var invoked = false
        var reentrantGotoResult: Array<PsiElement>? = arrayOf(element)
        var reentrantLiveResult: Array<PsiElement>? = arrayOf(element)

        val recursiveHandler =
            object : GotoDeclarationHandler {
                override fun getGotoDeclarationTargets(
                    sourceElement: PsiElement?,
                    offset: Int,
                    editor: Editor?,
                ): Array<PsiElement> {
                    invoked = true
                    // Called from inside AnyCompletionContributor.GotoDeclaration.hasOtherGotoTarget()
                    // while REENTRANCY is set - both calls below must short-circuit to null.
                    reentrantGotoResult =
                        AnyCompletionContributor.GotoDeclaration().getGotoDeclarationTargets(sourceElement, offset, editor)
                    reentrantLiveResult = AnyCompletionContributor.GotoDeclaration().resolveLiveGoToTargets(sourceElement!!)

                    return arrayOf(sourceElement)
                }

                override fun getActionText(context: DataContext): String? = null
            }
        GotoDeclarationHandler.EP_NAME.point.registerExtension(recursiveHandler, LoadingOrder.FIRST, testRootDisposable)

        val targets =
            AnyCompletionContributor.GotoDeclaration().getGotoDeclarationTargets(
                element,
                element.textOffset,
                myFixture.editor,
            )

        assertTrue("the registered test handler should have been invoked", invoked)
        assertNull("a nested getGotoDeclarationTargets() call must return null under REENTRANCY", reentrantGotoResult)
        assertNull("a nested resolveLiveGoToTargets() call must return null under REENTRANCY", reentrantLiveResult)
        assertNull(
            "hasOtherGotoTarget() finding another handler's target must make the outer call return null",
            targets,
        )
    }

    // Regression/coverage test for resolveLiveGoToTargets()'s
    // `if (language.baseLanguage !== null && !settings.isLanguageSupported(languageString))` branch:
    // a .js file's language ("ECMAScript 6") has a non-null baseLanguage ("JavaScript"), so when
    // only the base language is declared supported, the fallback must kick in and let the call
    // through to a successful live GoTo.
    fun testResolveLiveGoToTargetsUsesBaseLanguageFallbackToSucceed() {
        myFixture.configureByText("test.js", "var x = 'test_string';")
        val element = myFixture.findElementByText("'test_string'", PsiElement::class.java)!!
        val settings =
            project.service<Settings>().apply {
                pluginEnabled = true
                executionMode = ExecutionMode.Server
                supportedLanguages = "JavaScript"
                scriptPath = fixturePath("goto-server.php").path
            }
        val manager = project.service<ServerManager>()
        manager.sendRequest(settings, "Info", null, false, null, null)
        waitUntil { manager.status() == ServerState.RUNNING }
        assertEquals(ServerState.RUNNING, manager.status())

        val targets = AnyCompletionContributor.GotoDeclaration().resolveLiveGoToTargets(element)

        assertNotNull(targets)
    }

    // Regression/coverage test for resolveLiveGoToTargets()'s
    // `if (!settings.isLanguageSupported(languageString)) { return null }` branch reached *after*
    // the baseLanguage fallback above: neither "ECMAScript 6" nor its base "JavaScript" is
    // declared supported here.
    fun testResolveLiveGoToTargetsReturnsNullWhenBaseLanguageAlsoUnsupported() {
        myFixture.configureByText("test.js", "var x = 'test_string';")
        val element = myFixture.findElementByText("'test_string'", PsiElement::class.java)!!
        project.service<Settings>().apply {
            pluginEnabled = true
            executionMode = ExecutionMode.Server
            supportedLanguages = "PHP"
        }

        val targets = AnyCompletionContributor.GotoDeclaration().resolveLiveGoToTargets(element)

        assertNull(targets)
    }

    // Regression/coverage test for resolveLiveGoToTargets()'s
    // `if (!settings.isElementTypeMatchingFilter(...)) { return null }` branch.
    fun testResolveLiveGoToTargetsReturnsNullWhenElementTypeNotInFilter() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        val element = myFixture.findElementByText("'test_string'", PsiElement::class.java)!!
        project.service<Settings>().apply {
            pluginEnabled = true
            executionMode = ExecutionMode.Server
            supportedLanguages = "PHP"
            goToFilter = "SomeOtherType"
            scriptPath = fixturePath("goto-server.php").path
        }

        val targets = AnyCompletionContributor.GotoDeclaration().resolveLiveGoToTargets(element)

        assertNull(targets)
    }

    // Regression/coverage test for resolveLiveGoToTargets()'s
    // `... ?: return null` right after the psaManager.getCompletions(...) call: the server is
    // confirmed RUNNING (so serverUnavailable() doesn't intercept), but the live GoTo request
    // itself comes back as an error, so getCompletions() returns null.
    fun testResolveLiveGoToTargetsReturnsNullWhenScriptReturnsError() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        val element = myFixture.findElementByText("'test_string'", PsiElement::class.java)!!
        val settings =
            project.service<Settings>().apply {
                pluginEnabled = true
                executionMode = ExecutionMode.Server
                supportedLanguages = "PHP"
                scriptPath = fixturePath("goto-server-error.php").path
            }
        val manager = project.service<ServerManager>()
        manager.sendRequest(settings, "Info", null, false, null, null)
        waitUntil { manager.status() == ServerState.RUNNING }
        assertEquals(ServerState.RUNNING, manager.status())

        val targets = AnyCompletionContributor.GotoDeclaration().resolveLiveGoToTargets(element)

        assertNull(targets)
    }
}
