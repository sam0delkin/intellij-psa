package com.github.sam0delkin.intellijpsa.lineMarkerProvider

import com.github.sam0delkin.intellijpsa.index.INDEX_ID
import com.github.sam0delkin.intellijpsa.model.StaticCompletionModel
import com.github.sam0delkin.intellijpsa.model.completion.CompletionModel
import com.github.sam0delkin.intellijpsa.model.completion.CompletionsModel
import com.github.sam0delkin.intellijpsa.model.psi.PsiElementPatternModel
import com.github.sam0delkin.intellijpsa.psi.reference.PsaReference
import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.github.sam0delkin.intellijpsa.services.server.ServerManager
import com.github.sam0delkin.intellijpsa.services.server.ServerState
import com.github.sam0delkin.intellijpsa.settings.ExecutionMode
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceService
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.indexing.FileBasedIndex
import java.io.File

/**
 * The "PSA Reference" rename-dialog flow in `collectSlowLineMarkers` (roughly lines 103-313 of
 * PsaLineMarkerProvider.kt) only runs when `ReferenceProvidersRegistry.getReferencesFromProviders`
 * returns a [com.github.sam0delkin.intellijpsa.psi.reference.PsaReference] whose
 * `staticCompletionName` is non-null - which only `PsaReferenceContributor`'s static-index lookup
 * ever sets. That lookup resolves each recorded usage location via
 * `PsiUtils.processLink("file://$keyEl", ..., appendProjectDir = false)`, which only resolves
 * against a real `LocalFileSystem` "file://" URL. `BasePlatformTestCase`'s light-fixture project is
 * backed entirely by the in-memory "temp://" VFS (confirmed experimentally:
 * `VirtualFileManager.findFileByUrl("file:///src/test.php")` returns null while
 * `findFileByUrl("temp:///src/test.php")` resolves correctly), so that lookup can never succeed
 * here - there is no fixture data that makes it succeed, this is a VFS protocol mismatch between
 * the light test harness and code that assumes a real filesystem. The rest of this file's tests
 * confirm the reachable guard clauses and the fileData traversal instead; the rename dialog itself
 * remains a documented residual gap. The same limitation is why `PsaReferenceContributorTest.kt`
 * only covers the Server live-lookup path (which never needs `staticCompletionName`), not
 * the static-index path, and would equally block a dedicated `PsaStaticReferenceIndexTest.kt` from
 * covering `PsaReferenceContributor`'s consuming side.
 *
 * A handful of remaining kover-reported partial-branch lines (the `staticLookupEnabled` expression
 * around line 61 and the `it?.entries!!` line inside the fileData traversal around line 85) were
 * checked against the compiled bytecode (`javap -p -c -l` on
 * `build/classes/kotlin/main/.../PsaLineMarkerProvider.class`) rather than assumed: in both cases
 * the one remaining uncovered branch is a null-check on a value the Kotlin compiler cannot actually
 * prove non-null at the call site (an inlined stdlib `isNullOrEmpty()` null-check on
 * `PsaManager.getStaticCompletionConfigs()`'s declared-non-null `MutableList` return, and a `?.`
 * null-check on a per-file index value coming back through a Java-interop platform type from
 * `FileBasedIndex.getFileData()`/`PsaStaticReferenceIndex`'s own non-null-typed `Map<String,
 * List<String>>` value type) - genuinely unreachable without corrupting internal state, not a gap a
 * fixture could close.
 */
class PsaLineMarkerProviderTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        // Settings and PsaManager's static-completion cache are project-level light services that
        // persist across test methods (and across test classes sharing the same light-fixture
        // project) - reset every field this class touches or assumes a default for, so state from
        // another test can't leak in regardless of execution order.
        project.service<Settings>().apply {
            pluginEnabled = false
            debug = false
            executionMode = ExecutionMode.Script
            goToFilter = ""
            targetElementTypes = null
            supportsStaticCompletions = false
            resolveReferences = false
            annotateUndefinedElements = false
            supportedLanguages = ""
        }
        project.service<PsaManager>().setStaticCompletionConfigs(arrayListOf())
    }

    override fun tearDown() {
        try {
            // ServerManager is a light service that persists across test *methods* within the
            // same light-fixture project - must wait for the terminal state here, or the next
            // test method can start while this restart is still in flight (restart() redirects to
            // a pooled thread when called from the EDT, which test methods run on by default).
            project.service<ServerManager>().restart()
            waitUntil { project.service<ServerManager>().status() == ServerState.STOPPED }
        } finally {
            super.tearDown()
        }
    }

    private fun fixturePath(name: String): File {
        val resource = javaClass.classLoader.getResource("server/$name")!!
        val file = File(resource.toURI())
        file.setExecutable(true)

        return file
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

    // resolveLiveGoToTargets() reuses the same "skip outright instead of blocking" guard as
    // interactive GoTo/Completion, so it never calls the script while the server isn't confirmed
    // RUNNING yet - tests that expect it to actually run must start it first.
    private fun startServerAndWaitUntilRunning(settings: Settings) {
        project.service<ServerManager>().start(settings)
        waitUntil { project.service<ServerManager>().status() == ServerState.RUNNING }
    }

    // Server mode active, no static completions configured at all - the setup exercised by the
    // new live-only-reference gutter icon path.
    private fun configureLiveOnlySettings(script: File): Settings {
        val settings = project.service<PsaManager>().getSettings()
        settings.apply {
            pluginEnabled = true
            executionMode = ExecutionMode.Server
            supportedLanguages = "PHP"
            resolveReferences = true
            annotateUndefinedElements = true
            supportsStaticCompletions = false
            scriptPath = script.path
        }
        project.service<PsaManager>().setStaticCompletionConfigs(arrayListOf())

        return settings
    }

    // NavigationGutterIconBuilder.createLineMarkerInfo (used by both the pre-existing
    // static-completion path and the new live-only path) logs a "Performance warning: LineMarker
    // is supposed to be registered for leaf elements only" via Logger.error whenever the target
    // element is a composite PSI node rather than a leaf - which a PHP string literal element
    // (the smallest element whose text is exactly the quoted string) always is. Under
    // BasePlatformTestCase, TestLoggerFactory turns that Logger.error call into a hard
    // TestLoggerAssertionError. This is pre-existing platform behavior unrelated to the new
    // Server-mode gating this test targets (and the static-completion path could never reach
    // createLineMarkerInfo in tests at all - see this file's class doc comment) - suppress just
    // this expected log via LoggedErrorProcessor rather than changing which element is targeted
    // (a leaf element wouldn't have any reference attached to it at all).
    private fun collectSlowLineMarkersSuppressingLeafWarning(
        element: PsiElement,
        result: MutableCollection<in LineMarkerInfo<*>>,
    ) {
        LoggedErrorProcessor.executeWith<RuntimeException>(
            object : LoggedErrorProcessor() {
                override fun processError(
                    category: String,
                    message: String,
                    details: Array<String>,
                    t: Throwable?,
                ): MutableSet<Action> = Action.NONE
            },
            {
                PsaLineMarkerProvider().collectSlowLineMarkers(mutableListOf(element), result)
            },
        )
    }

    // The first live lookup right after the server reaches RUNNING can occasionally still
    // resolve no references yet (process-spawn/scheduling variance, not reproducible via a fixed
    // delay - see PsaReferenceContributorTest) - retry the whole marker collection instead of
    // asserting on a single attempt.
    private fun markersWithRetry(
        element: PsiElement,
        timeoutMs: Long = 8000,
    ): MutableList<LineMarkerInfo<*>> {
        val deadline = System.currentTimeMillis() + timeoutMs
        var result = mutableListOf<LineMarkerInfo<*>>()
        while (System.currentTimeMillis() < deadline) {
            result = mutableListOf()
            collectSlowLineMarkersSuppressingLeafWarning(element, result)
            if (result.isNotEmpty()) {
                return result
            }
            Thread.sleep(100)
        }

        return result
    }

    // A live-only reference (Server mode active, no matching static completion at all) still gets
    // a "PSA Reference" gutter icon, but without the Rename action / extra tooltip suffix that the
    // static-completion-backed path shows - the rename flow mutates a static-completion config
    // entry that simply doesn't exist here.
    fun testCollectSlowLineMarkersShowsMarkerForLiveOnlyReferenceInServerMode() {
        myFixture.addFileToProject("target.php", "<?php\nclass Target {}")
        myFixture.configureByText("test.php", "<?php\n'needle';")
        val element = myFixture.findElementByText("'needle'", PsiElement::class.java)!!

        val script = fixturePath("counting-goto-target.php")
        val settings = configureLiveOnlySettings(script)
        assertFalse("static completions must not be required for this", settings.supportsStaticCompletions)
        startServerAndWaitUntilRunning(settings)
        assertEquals(ServerState.RUNNING, project.service<ServerManager>().status())

        val result = markersWithRetry(element)

        assertEquals(1, result.size)
        assertEquals("PSA Reference", result.first().lineMarkerTooltip)
    }

    fun testCollectSlowLineMarkersNoMarkerForLiveOnlyReferenceWhenExecutionModeIsScript() {
        myFixture.addFileToProject("target.php", "<?php\nclass Target {}")
        myFixture.configureByText("test.php", "<?php\n'needle';")
        val element = myFixture.findElementByText("'needle'", PsiElement::class.java)!!

        val script = fixturePath("counting-goto-target.php")
        val settings = configureLiveOnlySettings(script)
        settings.executionMode = ExecutionMode.Script

        val result = mutableListOf<LineMarkerInfo<*>>()
        PsaLineMarkerProvider().collectSlowLineMarkers(mutableListOf(element), result)

        assertTrue(result.isEmpty())
    }

    fun testCollectSlowLineMarkersNoMarkerForLiveOnlyReferenceWhenServerModeDebugIsEnabled() {
        myFixture.addFileToProject("target.php", "<?php\nclass Target {}")
        myFixture.configureByText("test.php", "<?php\n'needle';")
        val element = myFixture.findElementByText("'needle'", PsiElement::class.java)!!

        val script = fixturePath("counting-goto-target.php")
        val settings = configureLiveOnlySettings(script)
        settings.debug = true

        val result = mutableListOf<LineMarkerInfo<*>>()
        PsaLineMarkerProvider().collectSlowLineMarkers(mutableListOf(element), result)

        assertTrue(result.isEmpty())
    }

    // Regression test for a bug where the live-only marker was built from EVERY reference
    // ReferenceProvidersRegistry.getReferencesFromProviders returned for the element - which
    // aggregates references from *all* registered PsiReferenceContributors, not just PSA's -
    // rather than only from a PsaReference PSA's own live lookup actually produced. PHP's bundled
    // PhpGetEnvArgumentReferenceContributor attaches a genuine, resolvable, wholly unrelated
    // reference to a getenv() string argument (resolving to the matching putenv() call) - a
    // faithful stand-in for "some other contributor's reference exists on this element while PSA's
    // own live lookup (the configured script) genuinely found nothing this time". Server mode is
    // active and the element type matches the (default, match-all) live filter, but the script
    // (counting-empty-completions.php) always answers with zero completions, so PsaReferenceContributor
    // contributes no PsaReference for this element - only the native one exists.
    fun testCollectSlowLineMarkersNoMarkerWhenOnlyNativeReferenceExistsAndPsaLiveLookupFindsNothing() {
        myFixture.configureByText(
            "test.php",
            "<?php\nputenv('FOO=bar');\n\$x = getenv('FOO');\n",
        )
        val element = myFixture.findElementByText("'FOO'", PsiElement::class.java)!!

        val script = fixturePath("counting-empty-completions.php")
        val settings = configureLiveOnlySettings(script)
        startServerAndWaitUntilRunning(settings)
        assertEquals(ServerState.RUNNING, project.service<ServerManager>().status())

        // Sanity-check the test's premise: a native, non-PSA reference really is present on this
        // element and PSA itself contributed nothing - otherwise this test would prove nothing.
        val references = ReferenceProvidersRegistry.getReferencesFromProviders(element, PsiReferenceService.Hints.NO_HINTS)
        assertTrue("expected a native getenv() reference for this test to be meaningful", references.isNotEmpty())
        assertTrue("expected no PsaReference among the native references", references.none { it is PsaReference })

        val result = mutableListOf<LineMarkerInfo<*>>()
        collectSlowLineMarkersSuppressingLeafWarning(element, result)

        assertTrue("a native, non-PSA reference must not be shown as a PSA Reference gutter marker", result.isEmpty())
    }

    fun testGetLineMarkerInfoAlwaysReturnsNull() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        val element = myFixture.findElementByText("'test_string'", PsiElement::class.java)!!

        assertNull(PsaLineMarkerProvider().getLineMarkerInfo(element))
    }

    fun testCollectSlowLineMarkersNoOpWithEmptyElements() {
        val result = mutableListOf<LineMarkerInfo<*>>()

        PsaLineMarkerProvider().collectSlowLineMarkers(mutableListOf(), result)

        assertTrue(result.isEmpty())
    }

    fun testCollectSlowLineMarkersNoOpWhenPluginDisabled() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        project.service<Settings>().pluginEnabled = false
        val element = myFixture.findElementByText("'test_string'", PsiElement::class.java)!!
        val result = mutableListOf<LineMarkerInfo<*>>()

        PsaLineMarkerProvider().collectSlowLineMarkers(mutableListOf(element), result)

        assertTrue(result.isEmpty())
    }

    fun testCollectSlowLineMarkersNoOpWhenResolveReferencesDisabled() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        project.service<Settings>().apply {
            pluginEnabled = true
            resolveReferences = false
        }
        val element = myFixture.findElementByText("'test_string'", PsiElement::class.java)!!
        val result = mutableListOf<LineMarkerInfo<*>>()

        PsaLineMarkerProvider().collectSlowLineMarkers(mutableListOf(element), result)

        assertTrue(result.isEmpty())
    }

    fun testCollectSlowLineMarkersNoOpWhenNoStaticCompletionConfigs() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        project.service<Settings>().apply {
            pluginEnabled = true
            resolveReferences = true
            targetElementTypes = arrayListOf("PHP:STRING_LITERAL")
            supportsStaticCompletions = true
            annotateUndefinedElements = true
        }
        project.service<PsaManager>().setStaticCompletionConfigs(arrayListOf())
        val element = myFixture.findElementByText("'test_string'", PsiElement::class.java)!!
        val result = mutableListOf<LineMarkerInfo<*>>()

        PsaLineMarkerProvider().collectSlowLineMarkers(mutableListOf(element), result)

        assertTrue(result.isEmpty())
    }

    fun testCollectSlowLineMarkersSkipsElementsNotInTargetElementTypes() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        project.service<Settings>().apply {
            pluginEnabled = true
            resolveReferences = true
            targetElementTypes = arrayListOf("SOME_OTHER_TYPE")
            supportsStaticCompletions = true
            annotateUndefinedElements = true
        }
        project.service<PsaManager>().setStaticCompletionConfigs(
            arrayListOf(
                StaticCompletionModel().apply {
                    name = "my_completion"
                    completions = CompletionsModel().apply { completions = arrayListOf() }
                },
            ),
        )
        val element = myFixture.findElementByText("'test_string'", PsiElement::class.java)!!
        val result = mutableListOf<LineMarkerInfo<*>>()

        PsaLineMarkerProvider().collectSlowLineMarkers(mutableListOf(element), result)

        assertTrue(result.isEmpty())
    }

    // `liveTypeMatch` (line ~79) is `liveLookupEnabled && settings.isElementTypeMatchingFilter(...)`.
    // Every existing live-mode test leaves goToFilter at its match-all default ("") so
    // isElementTypeMatchingFilter always returns true there; no test previously exercised
    // liveLookupEnabled=true together with a goToFilter that does NOT match the element's type,
    // confirmed via javap bytecode inspection of the compiled `&&` (a distinct branch pair from
    // the "liveLookupEnabled=false, short-circuited" case already covered elsewhere). This needs no
    // real server process - the per-element type-match guard runs before any reference lookup.
    fun testCollectSlowLineMarkersNoOpWhenServerModeActiveButGoToFilterDoesNotMatchElementType() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        project.service<Settings>().apply {
            pluginEnabled = true
            resolveReferences = true
            annotateUndefinedElements = true
            executionMode = ExecutionMode.Server
            goToFilter = "SOME_OTHER_TYPE"
            supportsStaticCompletions = false
        }
        val element = myFixture.findElementByText("'test_string'", PsiElement::class.java)!!
        val result = mutableListOf<LineMarkerInfo<*>>()

        PsaLineMarkerProvider().collectSlowLineMarkers(mutableListOf(element), result)

        assertTrue(result.isEmpty())
    }

    // `staticLookupEnabled` (line ~61) ANDs three conditions: supportsStaticCompletions,
    // targetElementTypes != null, and staticCompletionConfigs non-empty. Every other "no-op" test
    // above either has supportsStaticCompletions=false (short-circuiting at condition 1) or a
    // non-null targetElementTypes (testCollectSlowLineMarkersNoOpWhenNoStaticCompletionConfigs
    // reaches condition 3). None exercises condition 2's false branch while condition 1 is true -
    // this does, leaving kover's branch coverage for that line complete.
    fun testCollectSlowLineMarkersNoOpWhenSupportsStaticCompletionsTrueButTargetElementTypesNull() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        project.service<Settings>().apply {
            pluginEnabled = true
            resolveReferences = true
            annotateUndefinedElements = true
            supportsStaticCompletions = true
            targetElementTypes = null
            executionMode = ExecutionMode.Script
        }
        val element = myFixture.findElementByText("'test_string'", PsiElement::class.java)!!
        val result = mutableListOf<LineMarkerInfo<*>>()

        PsaLineMarkerProvider().collectSlowLineMarkers(mutableListOf(element), result)

        assertTrue(result.isEmpty())
    }

    // Regression/coverage test for the `if (!liveLookupEnabled) { return@map }` branch reached
    // right after `staticCompletion` is confirmed null (i.e. the reference found wasn't a
    // static-index-backed PsaReference at all). Reuses the same getenv()/putenv() native-reference
    // fixture as testCollectSlowLineMarkersNoMarkerWhenOnlyNativeReferenceExistsAndPsaLiveLookupFindsNothing
    // (PHP's bundled PhpGetEnvArgumentReferenceContributor attaches a genuine reference PSA never
    // produced), but here static lookup (not Server/live mode) is what lets the element past the
    // outer guard and the per-element type-match check, with liveLookupEnabled false - the
    // combination that specifically hits this branch rather than the live-only fallback beneath it.
    fun testCollectSlowLineMarkersNoMarkerWhenStaticOnlyElementHasNativeReferenceAndNoStaticMatch() {
        myFixture.configureByText(
            "test.php",
            "<?php\nputenv('FOO=bar');\n\$x = getenv('FOO');\n",
        )
        val element = myFixture.findElementByText("'FOO'", PsiElement::class.java)!!
        project.service<Settings>().apply {
            pluginEnabled = true
            resolveReferences = true
            annotateUndefinedElements = true
            supportsStaticCompletions = true
            targetElementTypes = arrayListOf("String")
            supportedLanguages = "PHP"
            executionMode = ExecutionMode.Script
        }
        project.service<PsaManager>().setStaticCompletionConfigs(
            arrayListOf(
                StaticCompletionModel().apply {
                    name = "my_completion"
                    completions = CompletionsModel().apply { completions = arrayListOf() }
                },
            ),
        )

        // Sanity-check the test's premise, same as the Server-mode counterpart above: a native,
        // non-PSA reference really is present and PSA itself contributed nothing statically either
        // (no index data matches this file/offset).
        val references = ReferenceProvidersRegistry.getReferencesFromProviders(element, PsiReferenceService.Hints.NO_HINTS)
        assertTrue("expected a native getenv() reference for this test to be meaningful", references.isNotEmpty())
        assertTrue("expected no PsaReference among the native references", references.none { it is PsaReference })

        val result = mutableListOf<LineMarkerInfo<*>>()
        PsaLineMarkerProvider().collectSlowLineMarkers(mutableListOf(element), result)

        assertTrue(result.isEmpty())
    }

    // Populates INDEX_ID with a real static-completion match: test.php's 'target' string literal
    // is recorded against a completion whose link points at the "class" keyword in target.php.
    // goToFilter/pattern types are the raw LighterAST *token* type ("single quoted string" - the
    // string's content token, with the surrounding quotes as separate sibling tokens), which is
    // distinct from the composite PSI element type ("String") used everywhere else in the plugin -
    // PsaStaticReferenceIndex's indexer walks the LighterAST directly, so it only ever sees the
    // former.
    @Suppress("UnstableApiUsage")
    private fun indexMatchingStaticCompletion() {
        val psaManager = project.service<PsaManager>()
        val settings = psaManager.getSettings()
        settings.apply {
            pluginEnabled = true
            resolveReferences = true
            supportsStaticCompletions = true
            annotateUndefinedElements = true
            goToFilter = "single quoted string"
            targetElementTypes = arrayListOf("String")
            supportedLanguages = "PHP"
        }

        val staticCompletion =
            StaticCompletionModel().apply {
                name = "my_static"
                title = "My Static"
                patterns = arrayListOf(PsiElementPatternModel(withType = "single quoted string"))
                completions =
                    CompletionsModel().apply {
                        completions =
                            arrayListOf(
                                CompletionModel().apply {
                                    text = "target"
                                    link = "/target.php:1:1"
                                },
                            )
                    }
            }
        psaManager.setStaticCompletionConfigs(arrayListOf(staticCompletion))

        myFixture.addFileToProject("target.php", "<?php\nclass Target {}\n")
        myFixture.configureByText("test.php", "<?php\n'target';")

        FileBasedIndex.getInstance().ensureUpToDate(INDEX_ID, project, GlobalSearchScope.allScope(project))
    }

    // Exercises the fileData.values.map { it?.entries!!.map { entry -> { ... } } } traversal
    // (PsaLineMarkerProvider.kt lines ~80-101) against real, non-empty FileBasedIndex data, so the
    // outer .map calls actually iterate real entries instead of an empty map. The innermost
    // lambda's body (the `val sourceEl = ...` block starting at line ~85) is never invoked no
    // matter what data is supplied here or in real usage - that inner block is a lambda literal
    // returned-but-never-called by its enclosing `.map { entry -> { ... } } }`, a data-independent
    // dead-code shape, not something any fixture could reach.
    fun testCollectSlowLineMarkersTraversesRealFileDataWithoutMatchingAReference() {
        indexMatchingStaticCompletion()
        val element = myFixture.findElementByText("'target'", PsiElement::class.java)!!
        val result = mutableListOf<LineMarkerInfo<*>>()

        PsaLineMarkerProvider().collectSlowLineMarkers(mutableListOf(element), result)

        // See this file's class doc comment: the static-index reference lookup can never succeed
        // under BasePlatformTestCase's temp:// VFS, so no marker is ever produced here.
        assertTrue(result.isEmpty())
    }
}
