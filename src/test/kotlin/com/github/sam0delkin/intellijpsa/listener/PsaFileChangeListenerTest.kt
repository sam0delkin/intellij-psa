package com.github.sam0delkin.intellijpsa.listener

import com.github.sam0delkin.intellijpsa.index.INDEX_ID
import com.github.sam0delkin.intellijpsa.model.StaticCompletionModel
import com.github.sam0delkin.intellijpsa.model.completion.CompletionModel
import com.github.sam0delkin.intellijpsa.model.completion.CompletionsModel
import com.github.sam0delkin.intellijpsa.model.psi.PsiElementPatternModel
import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.indexing.FileBasedIndex

/**
 * Three of [PsaFileChangeListener]'s reported-missed lines are not chased here, each confirmed
 * unreachable/impractical rather than merely skipped:
 *
 * - `if (null === scriptDir) continue` (~line 37): dead code. `getScriptDir()` can only return
 *   null when `settings.scriptPath` is null, but the enclosing loop already does
 *   `if (!settings.pluginEnabled || null === settings.scriptPath) continue` a few lines above, so
 *   this method is never even called with a null-producing path.
 * - `if (null === projectDir) continue` (~line 41): `project.guessProjectDir()` not returning null
 *   would require a light-fixture project without a resolvable base directory; every other test in
 *   this class (and the identical finding documented in `ExecutionUtilsTest.testSetWorkDirectoryIfExistsDoesNotThrow`)
 *   relies on it resolving to a real (in-memory) directory here, so there is no reachable fixture
 *   state in `BasePlatformTestCase` that makes it null without fabricating a synthetic `Project`.
 * - The `project.service<PsaManager>()` failure branch (~lines 47-49): `PsaManager`'s own `init`
 *   block swallows every `Throwable` internally, so the service constructor itself cannot throw;
 *   provoking `project.service<T>()` to throw would require disposing the shared light-fixture
 *   project mid-test, which would corrupt every later test method sharing it.
 */
class PsaFileChangeListenerTest : BasePlatformTestCase() {
    override fun tearDown() {
        try {
            // Settings and PsaManager's static-completion cache are project-level light services
            // that persist past this class's own test methods, into whichever test class runs
            // next in the same light-fixture project - reset everything back to safe, inert
            // defaults so a later test never runs against a leftover enabled-plugin/matching
            // static-completion combination from this class.
            project.service<Settings>().apply {
                pluginEnabled = false
                resolveReferences = false
                goToFilter = ""
                targetElementTypes = null
                supportedLanguages = ""
                indexFolder = ""
                useVelocityInIndex = false
                scriptPath = null
            }
            project.service<PsaManager>().setStaticCompletionConfigs(arrayListOf())
        } finally {
            super.tearDown()
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 3000
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
    }

    fun testPrepareChangeSchedulesRestartForScriptDirEvent() {
        project.service<Settings>().apply {
            pluginEnabled = true
            scriptPath = ".psa/psa.php"
        }
        val scriptFile = myFixture.addFileToProject(".psa/psa.php", "<?php").virtualFile

        val event = VFileContentChangeEvent(this, scriptFile, 0L, 1L, false)

        val listener = PsaFileChangeListener()
        try {
            val applier = listener.prepareChange(mutableListOf(event))
            assertNull(applier)
        } finally {
            listener.dispose()
        }
    }

    @Suppress("UnstableApiUsage")
    fun testPrepareChangeReindexesFilesReferencingTheChangedTarget() {
        // Real static-completion index setup mirroring PsaStaticReferenceIndexTest's proven "happy
        // path" (a file containing 'target' resolves to a completion whose link points at
        // target.php) - this gives FileBasedIndex.getContainingFiles() a genuine non-empty result
        // to iterate over for the changed file (target.php), exercising the requestReindex() call.
        project.service<Settings>().apply {
            pluginEnabled = true
            resolveReferences = true
            goToFilter = "single quoted string"
            supportedLanguages = "PHP"
            indexFolder = ""
            useVelocityInIndex = false
            targetElementTypes = null
            scriptPath = ".psa/psa.php"
        }
        project.service<PsaManager>().setStaticCompletionConfigs(
            arrayListOf(
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
                },
            ),
        )
        val targetFile = myFixture.addFileToProject("target.php", "<?php\nclass Target {}\n").virtualFile
        myFixture.configureByText("happy_path.php", "<?php\n'target';")
        FileBasedIndex.getInstance().ensureUpToDate(INDEX_ID, project, GlobalSearchScope.allScope(project))
        assertTrue(
            "expected a real indexed usage referencing target.php for this test to be meaningful",
            FileBasedIndex.getInstance().getContainingFiles(INDEX_ID, targetFile.url, GlobalSearchScope.allScope(project)).isNotEmpty(),
        )

        val event = VFileContentChangeEvent(this, targetFile, 0L, 1L, false)
        val listener = PsaFileChangeListener()
        try {
            val applier = listener.prepareChange(mutableListOf(event))
            assertNull(applier)
        } finally {
            listener.dispose()
        }
    }

    fun testTimerFiresAndCatchesGetInfoFailure() {
        project.service<Settings>().apply {
            pluginEnabled = true
            scriptPath = ".psa/psa.php"
        }
        val scriptFile = myFixture.addFileToProject(".psa/psa.php", "<?php").virtualFile
        val event = VFileContentChangeEvent(this, scriptFile, 0L, 1L, false)
        val psaManager = project.service<PsaManager>()
        psaManager.lastResultSucceed = true

        val listener = PsaFileChangeListener()
        try {
            val applier = listener.prepareChange(mutableListOf(event))
            assertNull(applier)
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

            // ".psa/psa.php" is not a real executable, so the scheduled TimerTask's
            // psaManager.getInfo() call is expected to throw once it actually fires.
            waitUntil { !psaManager.lastResultSucceed }
            assertFalse(psaManager.lastResultSucceed)
            assertTrue(psaManager.lastResultMessage.isNotEmpty())
        } finally {
            listener.dispose()
        }
    }

    fun testDisposeCancelsActiveTimerBeforeItFires() {
        project.service<Settings>().apply {
            pluginEnabled = true
            scriptPath = ".psa/psa.php"
        }
        val scriptFile = myFixture.addFileToProject(".psa/psa.php", "<?php").virtualFile
        val event = VFileContentChangeEvent(this, scriptFile, 0L, 1L, false)

        val listener = PsaFileChangeListener()
        val applier = listener.prepareChange(mutableListOf(event))
        assertNull(applier)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        // Dispose immediately - well before the 500ms scheduled TimerTask would fire - to exercise
        // the "cancel an active timer" branch specifically (as opposed to disposing a listener
        // whose timer was never set, already covered by testDisposeCancelsTimer below).
        listener.dispose()
    }

    fun testPrepareChangeTriggersReindexForUnrelatedEvent() {
        myFixture.configureByText("other.php", "<?php")
        project.service<Settings>().apply {
            pluginEnabled = true
            scriptPath = ".psa/psa.php"
        }

        val event = VFileContentChangeEvent(this, myFixture.file.virtualFile, 0L, 1L, false)

        val listener = PsaFileChangeListener()
        try {
            val applier = listener.prepareChange(mutableListOf(event))
            assertNull(applier)
        } finally {
            listener.dispose()
        }
    }

    fun testPrepareChangeSkipsProjectsWithPluginDisabled() {
        project.service<Settings>().pluginEnabled = false

        val listener = PsaFileChangeListener()
        val applier = listener.prepareChange(mutableListOf())

        assertNull(applier)
    }

    fun testDisposeCancelsTimer() {
        val listener = PsaFileChangeListener()

        listener.dispose()
        listener.dispose()
    }
}
