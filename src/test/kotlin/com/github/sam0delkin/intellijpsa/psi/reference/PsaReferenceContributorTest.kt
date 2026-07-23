package com.github.sam0delkin.intellijpsa.psi.reference

import com.github.sam0delkin.intellijpsa.index.INDEX_ID
import com.github.sam0delkin.intellijpsa.model.StaticCompletionModel
import com.github.sam0delkin.intellijpsa.model.completion.CompletionModel
import com.github.sam0delkin.intellijpsa.model.completion.CompletionsModel
import com.github.sam0delkin.intellijpsa.model.psi.PsiElementPatternModel
import com.github.sam0delkin.intellijpsa.psi.PsaElement
import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.github.sam0delkin.intellijpsa.services.server.ServerManager
import com.github.sam0delkin.intellijpsa.services.server.ServerState
import com.github.sam0delkin.intellijpsa.settings.ExecutionMode
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.github.sam0delkin.intellijpsa.util.PsiUtils
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceService
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.indexing.FileBasedIndex
import java.io.File

/**
 * Coverage for PsaReferenceContributor's live (non-static) reference resolution: in Server
 * execution mode, "Resolve References" no longer requires `supports_static_completions` -
 * it can also resolve a reference for any element by calling the server directly, since
 * the persistent process makes a per-element round trip cheap.
 *
 * Also covers the static-completion-index lookup path's reachable guard clauses and outer loop.
 * The index is keyed by each completion's resolved TARGET location (not by the source usage's own
 * location), so `testStaticIndexLookupEntersOuterLoopButInnerResolutionStillFailsUnderVfsMismatch`
 * calls `getReferencesByElement` on the TARGET-side element (e.g. `target.php`'s resolved element)
 * rather than the usual source-usage element - that's what makes `index.getValues(INDEX_ID,
 * elementUrl, ...)` return real, non-empty data at all in this harness (unlike the innermost
 * resolution one level deeper, which remains the same documented VFS dead zone as
 * `PsaLineMarkerProviderTest` and `PsaReferenceAnnotatorTest`: `PsiUtils.processLink("file://$keyEl",
 * ..., appendProjectDir = false)` reconstructs a real "file://" URL from a raw filesystem path, which
 * never resolves against this light-fixture project's "temp://"-only VFS).
 */
class PsaReferenceContributorTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        // Settings and PsaManager's static-completion cache are project-level light services that
        // persist across test methods (and across test classes sharing the same light-fixture
        // project) - reset the fields every test in this class touches or assumes a default for,
        // so state from another test can't leak in regardless of execution order.
        project.service<Settings>().apply {
            pluginEnabled = false
            executionMode = ExecutionMode.Script
            goToFilter = ""
            targetElementTypes = null
            supportsStaticCompletions = false
            resolveReferences = false
        }
        project.service<PsaManager>().setStaticCompletionConfigs(arrayListOf())
    }

    override fun tearDown() {
        try {
            // ServerManager is a light service that persists across test *methods* within
            // the same light-fixture project - must wait for the terminal state here, or the next
            // test method can start while this restart is still in flight (restart() redirects to
            // a pooled thread when called from the EDT, which test methods run on by default).
            project.service<ServerManager>().restart()
            waitUntil { project.service<ServerManager>().status() == ServerState.STOPPED }
            // Settings and PsaManager's static-completion cache persist past this class's own test
            // methods, into whichever test class runs next in the same light-fixture project -
            // reset everything back to safe, inert defaults so a later, unrelated test class's own
            // indexing/reference resolution never runs against a leftover enabled-plugin/
            // matching-pattern combination left behind by this class (e.g. by the static-index
            // lookup test, which enables the plugin and a matching static completion to drive real
            // indexing).
            project.service<Settings>().apply {
                pluginEnabled = false
                executionMode = ExecutionMode.Script
                goToFilter = ""
                targetElementTypes = null
                supportsStaticCompletions = false
                resolveReferences = false
            }
            project.service<PsaManager>().setStaticCompletionConfigs(arrayListOf())
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

    private fun counterFor(script: File): File {
        val counter = File(script.parentFile, script.name.removeSuffix(".php") + ".count")
        counter.delete()

        return counter
    }

    private fun counterValue(counter: File): Int = if (counter.isFile) counter.readText().trim().toInt() else 0

    // The first live lookup right after the server reaches RUNNING can occasionally still
    // return no references (observed in this sandboxed environment - process-spawn/scheduling
    // variance, not reproducible via a fixed delay) - retry the whole references computation
    // instead of asserting on a single attempt.
    private fun referencesWithRetry(
        element: PsiElement,
        timeoutMs: Long = 8000,
    ): Array<PsiReference> {
        val deadline = System.currentTimeMillis() + timeoutMs
        var references: Array<PsiReference> = emptyArray()
        while (System.currentTimeMillis() < deadline) {
            references = element.references
            if (references.isNotEmpty()) {
                return references
            }
            Thread.sleep(100)
        }

        return references
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

    // Switches into Server mode only after the PSI/file setup is done, so the plugin's
    // own file-change listener (which schedules a debounced server restart on every file event
    // while enabled) never fires mid-test and races with the assertions below.
    private fun configureServerSettings(script: File): Settings {
        val settings = project.service<PsaManager>().getSettings()
        settings.pluginEnabled = true
        settings.executionMode = ExecutionMode.Server
        settings.supportedLanguages = "PHP"
        settings.resolveReferences = true
        settings.scriptPath = script.path

        return settings
    }

    // resolveLiveGoToTargets() reuses the same "skip outright instead of blocking" guard as
    // interactive GoTo/Completion, so it never calls the script while the server isn't
    // confirmed RUNNING yet - tests that expect it to actually run must start it first.
    private fun startServerAndWaitUntilRunning(settings: Settings) {
        project.service<ServerManager>().start(settings)
        waitUntil { project.service<ServerManager>().status() == ServerState.RUNNING }
    }

    fun testLiveLookupResolvesAReferenceViaTheServerWithoutStaticCompletions() {
        myFixture.addFileToProject("target.php", "<?php\nclass Target {}")
        myFixture.configureByText("test.php", "<?php\n'needle';")
        val element = myFixture.findElementByText("'needle'", PsiElement::class.java)!!

        val script = fixturePath("counting-goto-target.php")
        val counter = counterFor(script)
        val settings = configureServerSettings(script)
        assertFalse("static completions must not be required for this", settings.supportsStaticCompletions)
        startServerAndWaitUntilRunning(settings)
        assertEquals(ServerState.RUNNING, project.service<ServerManager>().status())
        assertTrue(settings.isLanguageSupported(element.containingFile.language.id))

        val references = referencesWithRetry(element)

        assertEquals(1, counterValue(counter))
        assertTrue(
            "expected a reference resolving to the server's GoTo target",
            // PsaElement.getName() can fall back to the underlying declaration's own presentation
            // (e.g. PHP's class presentableText), which may carry incidental padding/whitespace -
            // not something this test should assert on, so compare trimmed.
            references.any { (it.resolve() as? PsaElement)?.name?.trim() == "Target" },
        )
    }

    fun testLiveLookupDoesNotRunOutsideServerMode() {
        myFixture.addFileToProject("target.php", "<?php\nclass Target {}")
        myFixture.configureByText("test.php", "<?php\n'needle';")
        val element = myFixture.findElementByText("'needle'", PsiElement::class.java)!!

        val script = fixturePath("counting-goto-target.php")
        val counter = counterFor(script)
        val settings = configureServerSettings(script)
        settings.executionMode = ExecutionMode.Script

        val references = element.references

        assertTrue("the server should never have been invoked", references.isEmpty())
        assertFalse(counter.exists())
    }

    fun testLiveLookupDoesNotRunWhenResolveReferencesIsDisabled() {
        myFixture.addFileToProject("target.php", "<?php\nclass Target {}")
        myFixture.configureByText("test.php", "<?php\n'needle';")
        val element = myFixture.findElementByText("'needle'", PsiElement::class.java)!!

        val script = fixturePath("counting-goto-target.php")
        val counter = counterFor(script)
        val settings = configureServerSettings(script)
        settings.resolveReferences = false

        val references = element.references

        assertTrue(references.isEmpty())
        assertFalse(counter.exists())
    }

    // `getReferencesByElement`'s very first guard: elements with no containing file (a PsiDirectory
    // is the simplest way to get one) always contribute no references. Goes through
    // `ReferenceProvidersRegistry.getReferencesFromProviders` directly (the same API
    // `PsaLineMarkerProviderTest` uses) rather than `PsiElement.references`, since `PsiDirectory`'s
    // own `getReferences()` does not necessarily delegate to the registered contributors the same
    // way a regular source PsiElement's does.
    // The static-completion-index lookup path (`element.containingFile.virtualFile.url`) used to
    // NPE for an element whose containing file has no backing virtual file - e.g. a `DummyHolder`,
    // which `PsiElement.copy()` produces for a detached single-element copy (as opposed to copying
    // a whole file). This is exactly the kind of non-physical PsiFile the completion machinery
    // creates internally, and was observed crashing real completion tests in CI.
    fun testNoReferencesWhenContainingFileHasNoVirtualFile() {
        project.service<Settings>().apply {
            pluginEnabled = true
            resolveReferences = true
            supportsStaticCompletions = true
            supportedLanguages = "PHP"
            goToFilter = ""
        }
        myFixture.configureByText("test.php", "<?php\n'target';")
        val element = myFixture.findElementByText("'target'", PsiElement::class.java)!!
        val copy = element.copy()
        assertNull(
            "expected a DummyHolder-backed copy with no virtual file, for this test to be meaningful",
            copy.containingFile.virtualFile,
        )

        val references = ReferenceProvidersRegistry.getReferencesFromProviders(copy, PsiReferenceService.Hints.NO_HINTS)

        assertTrue(references.none { it is PsaReference })
    }

    fun testNoReferencesWhenElementHasNoContainingFile() {
        project.service<Settings>().apply {
            pluginEnabled = true
            resolveReferences = true
            supportsStaticCompletions = true
            goToFilter = ""
        }
        val directory = myFixture.configureByText("test.php", "<?php\n'target';").containingDirectory!!
        assertNull(
            "expected a directory PsiElement with no containing file, for this test to be meaningful",
            directory.containingFile,
        )

        val references = ReferenceProvidersRegistry.getReferencesFromProviders(directory, PsiReferenceService.Hints.NO_HINTS)

        assertTrue(references.none { it is PsaReference })
    }

    // `null == settings.goToFilter` (line ~64) is a defensive guard distinct from the normal
    // match-everything default (`goToFilter = ""`) - covers that guard's true branch directly.
    //
    // Note: `PsaStaticReferenceIndex`'s own indexer does NOT have this same null guard - its visitor
    // does `settings.goToFilter!!.contains(...)` unconditionally, which throws an NPE the moment
    // indexing runs while `goToFilter` is null. This is a real production bug (reported, not fixed,
    // per this session's convention). Confirmed experimentally to be a genuine cross-test race, not
    // just a same-test ordering concern: even with `goToFilter` restored to "" in a `finally` block
    // immediately after this test's own call, `FileBasedIndex`'s background indexing (driven by
    // `PsaFileChangeListener`, an application-wide `AsyncFileListener` that reacts to file events in
    // ANY open project) intermittently observed the transient null value and crashed - surfacing as
    // an unrelated LATER test failing with `ServiceNotReadyException: ... status REQUIRES_REBUILD`
    // during ITS OWN setUp(), on more than one full-suite run. The actual fix is to make this test's
    // file provably ineligible for background (re)indexing in the first place, independent of
    // `goToFilter`/`pluginEnabled` timing: `PsaStaticReferenceIndex.getInputFilter().acceptInput()`
    // separately requires `settings.isLanguageSupported(...)`, so setting `supportedLanguages` to
    // something that will never match this PHP file's language - overriding whatever a *different*,
    // unrelated test in this class left it as (e.g. `configureServerSettings` sets it to "PHP" and
    // never resets it) - keeps the input filter excluding this file for the entire test, regardless
    // of exactly when any background indexing pass happens to run.
    fun testNoReferencesWhenGoToFilterIsNull() {
        myFixture.configureByText("test.php", "<?php\n'target';")
        val element = myFixture.findElementByText("'target'", PsiElement::class.java)!!
        val settings =
            project.service<Settings>().apply {
                pluginEnabled = true
                resolveReferences = true
                supportsStaticCompletions = true
                supportedLanguages = "NotPHP"
                goToFilter = null
            }

        val references =
            try {
                element.references
            } finally {
                settings.goToFilter = ""
            }

        assertTrue(references.none { it is PsaReference })
    }

    // See this file's class doc comment: querying on the TARGET-side element (rather than the usual
    // source-usage element) is what makes the outer `index.getValues(...)` lookup return real,
    // non-empty data in this harness at all - the innermost per-entry resolution one level deeper
    // still hits the same documented VFS dead zone, so this never produces an actual PsaReference.
    @Suppress("UnstableApiUsage")
    fun testStaticIndexLookupEntersOuterLoopButInnerResolutionStillFailsUnderVfsMismatch() {
        project.service<Settings>().apply {
            pluginEnabled = true
            resolveReferences = true
            supportsStaticCompletions = true
            goToFilter = "single quoted string"
            targetElementTypes = null
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
        project.service<PsaManager>().setStaticCompletionConfigs(arrayListOf(staticCompletion))

        myFixture.addFileToProject("target.php", "<?php\nclass Target {}\n")
        myFixture.configureByText("test.php", "<?php\n'target';")

        FileBasedIndex.getInstance().ensureUpToDate(INDEX_ID, project, GlobalSearchScope.allScope(project))

        // Resolve the exact same target element the indexer itself resolved (via the same link
        // string), so the query below is guaranteed to use the identical index key.
        val targetElement = PsiUtils.processLink("/target.php:1:1", "", project)!!.getOriginalPsiElement()
        val elementUrl = targetElement.containingFile.virtualFile.url + ":" + targetElement.textOffset
        val indexKeys = FileBasedIndex.getInstance().getValues(INDEX_ID, elementUrl, GlobalSearchScope.projectScope(project))
        assertTrue(
            "sanity check: expected the usage to be indexed under the target element's own url " +
                "(projectScope, matching PsaReferenceContributor's own query scope exactly)",
            indexKeys.isNotEmpty(),
        )

        val references = ReferenceProvidersRegistry.getReferencesFromProviders(targetElement, PsiReferenceService.Hints.NO_HINTS)

        assertTrue(
            "resolution still fails past the VFS dead zone, so no reference is produced",
            references.none { it is PsaReference },
        )
    }
}
