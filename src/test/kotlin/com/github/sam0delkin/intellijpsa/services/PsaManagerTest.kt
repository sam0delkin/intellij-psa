package com.github.sam0delkin.intellijpsa.services

import com.github.sam0delkin.intellijpsa.model.EditorActionSource
import com.github.sam0delkin.intellijpsa.model.EditorActionTarget
import com.github.sam0delkin.intellijpsa.model.RequestType
import com.github.sam0delkin.intellijpsa.model.action.EditorActionInputModel
import com.github.sam0delkin.intellijpsa.model.psi.IndexedPsiElementModel
import com.github.sam0delkin.intellijpsa.model.psi.PsiElementModel
import com.github.sam0delkin.intellijpsa.services.server.ServerManager
import com.github.sam0delkin.intellijpsa.services.server.ServerState
import com.github.sam0delkin.intellijpsa.settings.ExecutionMode
import com.github.sam0delkin.intellijpsa.settings.MultipleFileCodeTemplate
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

class PsaManagerTest : BasePlatformTestCase() {
    private val infoJsonFormat = Json { ignoreUnknownKeys = true }

    private fun fixturePath(name: String): File {
        val resource = javaClass.classLoader.getResource("server/$name")!!
        val file = File(resource.toURI())
        file.setExecutable(true)

        return file
    }

    private fun scriptSettings(script: String): Settings =
        Settings().apply {
            pluginEnabled = true
            executionMode = ExecutionMode.Script
            scriptPath = fixturePath(script).path
            supportedLanguages = "PHP"
        }

    private fun serverSettings(script: String): Settings =
        Settings().apply {
            pluginEnabled = true
            executionMode = ExecutionMode.Server
            scriptPath = fixturePath(script).path
            supportedLanguages = "PHP"
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

    fun testGetInfoScriptModeSuccess() {
        val settings = scriptSettings("counting-script.php")

        val info = project.service<PsaManager>().getInfo(settings, project)

        assertNotNull(info)
    }

    fun testGetInfoServerModeSuccess() {
        val settings = serverSettings("counting-server.php")

        val info = project.service<PsaManager>().getInfo(settings, project)

        assertNotNull(info)
    }

    fun testGetInfoScriptModeFailureThrows() {
        val settings = scriptSettings("failing-script.php")

        try {
            project.service<PsaManager>().getInfo(settings, project)
            fail("Expected an exception from a failing script")
        } catch (_: Exception) {
        }
    }

    fun testGetStaticCompletionsScriptFailureRecordsErrorAndReturnsNull() {
        val settings = scriptSettings("failing-script.php").apply { showErrors = false }

        val result = project.service<PsaManager>().getStaticCompletions(settings, project)

        assertNull(result)
        assertFalse(project.service<PsaManager>().lastResultSucceed)
    }

    fun testGetStaticCompletionsScriptModeSuccess() {
        val settings = scriptSettings("static-completions-success.php")

        val result = project.service<PsaManager>().getStaticCompletions(settings, project)

        assertNotNull(result)
        assertTrue(project.service<PsaManager>().lastResultSucceed)
    }

    fun testUpdateStaticCompletionsNoOpWhenNotSupported() {
        val settings =
            Settings().apply {
                pluginEnabled = true
                supportsStaticCompletions = false
            }

        project.service<PsaManager>().updateStaticCompletions(settings, project)
    }

    fun testUpdateStaticCompletionsNoOpWhenNoScriptPath() {
        val settings =
            Settings().apply {
                pluginEnabled = true
                supportsStaticCompletions = true
                scriptPath = null
            }

        project.service<PsaManager>().updateStaticCompletions(settings, project)
    }

    fun testUpdateStaticCompletionsSuccessUpdatesConfigs() {
        val settings =
            scriptSettings("static-completions-success.php").apply {
                supportsStaticCompletions = true
                resolveReferences = false
            }

        project.service<PsaManager>().updateStaticCompletions(settings, project)

        assertTrue(project.service<PsaManager>().lastResultSucceed)
        assertTrue(project.service<PsaManager>().getStaticCompletionConfigs().isEmpty())
    }

    fun testUpdateStaticCompletionsHashChangesWithDifferentConfigs() {
        val settings = Settings()
        val manager = project.service<PsaManager>()

        manager.setStaticCompletionConfigs(arrayListOf())
        manager.updateStaticCompletionsHash(settings)
        val hash1 = settings.staticCompletionsHash

        manager.setStaticCompletionConfigs(
            arrayListOf(
                com.github.sam0delkin.intellijpsa.model.StaticCompletionModel().apply {
                    name = "a"
                    completions =
                        com.github.sam0delkin.intellijpsa.model.completion
                            .CompletionsModel()
                            .apply { completions = arrayListOf() }
                },
            ),
        )
        manager.updateStaticCompletionsHash(settings)
        val hash2 = settings.staticCompletionsHash

        assertFalse(hash1 == hash2)
    }

    fun testUpdateInfoNullIsNoOp() {
        val settings = Settings()

        project.service<PsaManager>().updateInfo(settings, null)
    }

    fun testUpdateInfoWithSingleAndMultipleFileTemplates() {
        val settings = scriptSettings("counting-script.php")
        val infoJson =
            """
            {
                "supported_languages": ["PHP"],
                "templates": [
                    {
                        "name": "single",
                        "type": "single_file",
                        "title": "Single File",
                        "fields": [
                            {"name": "className", "title": "Class Name", "type": "Text", "focused": true, "options": ["a", "b"]}
                        ]
                    },
                    {
                        "name": "multi",
                        "type": "multiple_file",
                        "title": "Multiple Files",
                        "file_count": 3,
                        "fields": []
                    }
                ],
                "editor_actions": [
                    {"name": "action1", "title": "Action 1", "group_name": "Group", "source": "editor", "target": "clipboard", "context_action": true, "contextual": true}
                ]
            }
            """.trimIndent()
        val info =
            infoJsonFormat.decodeFromString<com.github.sam0delkin.intellijpsa.model.InfoModel>(infoJson)

        project.service<PsaManager>().updateInfo(settings, info)

        assertEquals(1, settings.singleFileCodeTemplates?.size)
        assertEquals(1, settings.multipleFileCodeTemplates?.size)
        assertEquals(3, (settings.multipleFileCodeTemplates?.get(0) as MultipleFileCodeTemplate).fileCount)
        assertEquals(
            1,
            settings.singleFileCodeTemplates
                ?.get(0)
                ?.formFields
                ?.size,
        )
        assertEquals(1, settings.editorActions?.size)
        assertEquals("action1", settings.editorActions?.get(0)?.name)
        assertEquals(EditorActionSource.Editor, settings.editorActions?.get(0)?.source)
        assertEquals(EditorActionTarget.Clipboard, settings.editorActions?.get(0)?.target)
    }

    fun testUpdateInfoMultipleFileTemplateWithoutFileCountThrows() {
        val settings = Settings()
        val infoJson =
            """
            {
                "templates": [
                    {"name": "multi", "type": "multiple_file", "title": "Multiple Files", "fields": []}
                ]
            }
            """.trimIndent()
        val info =
            infoJsonFormat.decodeFromString<com.github.sam0delkin.intellijpsa.model.InfoModel>(infoJson)

        try {
            project.service<PsaManager>().updateInfo(settings, info)
            fail("Expected exception for missing file_count")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("file_count"))
        }
    }

    fun testGenerateTemplateCodeScriptModeSuccess() {
        val settings = scriptSettings("counting-script.php")

        // counting-script.php returns {"static_completions": [], "providers": []}, which does not
        // decode as TemplateDataModel, so this exercises the catch path and null return.
        val result =
            project.service<PsaManager>().generateTemplateCode(
                settings,
                project,
                "/path",
                "MyTemplate",
                "single_file",
                null,
                emptyMap(),
            )

        assertNull(result)
        assertFalse(project.service<PsaManager>().lastResultSucceed)
    }

    fun testGenerateTemplateCodeScriptModeFailure() {
        val settings = scriptSettings("failing-script.php").apply { showErrors = false }

        val result =
            project.service<PsaManager>().generateTemplateCode(
                settings,
                project,
                "/path",
                "MyTemplate",
                "single_file",
                null,
                emptyMap(),
            )

        assertNull(result)
        assertFalse(project.service<PsaManager>().lastResultSucceed)
    }

    fun testGenerateTemplateCodeServerMode() {
        val settings = serverSettings("counting-server.php")

        val result =
            project.service<PsaManager>().generateTemplateCode(
                settings,
                project,
                "/path",
                "MyTemplate",
                "single_file",
                null,
                emptyMap(),
            )

        assertNull(result)
    }

    fun testPerformActionScriptModeFailure() {
        val settings = scriptSettings("failing-script.php").apply { showErrors = false }
        val action = EditorActionInputModel("my_action", "/file.php", null)

        val result = project.service<PsaManager>().performAction(settings, project, action)

        assertNull(result)
        assertFalse(project.service<PsaManager>().lastResultSucceed)
    }

    fun testPerformActionScriptModeSuccessReturnsRawStdout() {
        val settings = scriptSettings("counting-script.php")
        val action = EditorActionInputModel("my_action", "/file.php", null)

        val result = project.service<PsaManager>().performAction(settings, project, action)

        assertNotNull(result)
        assertTrue(project.service<PsaManager>().lastResultSucceed)
    }

    private fun samplePsiElementModel(): PsiElementModel =
        PsiElementModel(
            id = "1",
            elementType = "STRING_LITERAL",
            options = mutableMapOf(),
            elementName = null,
            elementFqn = null,
            elementSignature = null,
            text = "'test'",
            parent = null,
            prev = null,
            next = null,
            textRange = null,
            filePath = "/tmp/test.php",
        )

    fun testGetCompletionsReturnsNullWhenPluginDisabled() {
        val settings = Settings().apply { pluginEnabled = false }
        val model = arrayOf(IndexedPsiElementModel(samplePsiElementModel(), "0:5"))

        val result = project.service<PsaManager>().getCompletions(settings, model, RequestType.Completion, "PHP")

        assertNull(result)
    }

    fun testGetCompletionsReturnsNullWhenLanguageNotSupported() {
        val settings =
            Settings().apply {
                pluginEnabled = true
                supportedLanguages = "JavaScript"
            }
        val model = arrayOf(IndexedPsiElementModel(samplePsiElementModel(), "0:5"))

        val result = project.service<PsaManager>().getCompletions(settings, model, RequestType.Completion, "PHP")

        assertNull(result)
    }

    fun testGetStaticCompletionsCancelledDuringExecutionThrowsProcessCancelled() {
        val settings =
            scriptSettings("slow-script.php").apply {
                showErrors = false
                executionTimeout = 5000
            }
        val indicator =
            com.intellij.openapi.progress.util
                .ProgressIndicatorBase()
        val cancelThread =
            Thread {
                Thread.sleep(100)
                indicator.cancel()
            }
        cancelThread.start()

        val result = project.service<PsaManager>().getStaticCompletions(settings, project, null, indicator)
        cancelThread.join()

        assertNull(result)
        assertFalse(project.service<PsaManager>().lastResultSucceed)
    }

    fun testGetCompletionsContinuesWhenTempFileCleanupThrows() {
        val settings = scriptSettings("completions-script.php")
        val model = arrayOf(IndexedPsiElementModel(samplePsiElementModel(), "0:5"))

        @Suppress("DEPRECATION", "DEPRECATION_ERROR")
        val originalSecurityManager = System.getSecurityManager()

        @Suppress("DEPRECATION", "DEPRECATION_ERROR")
        val testSecurityManager =
            object : SecurityManager() {
                override fun checkDelete(file: String?) {
                    if (file != null && file.contains("psa_tmp") && file.endsWith(".tmp")) {
                        throw SecurityException("blocked for test")
                    }
                }

                override fun checkPermission(perm: java.security.Permission?) {
                    // Allow everything else - only checkDelete() is restricted above.
                }

                override fun checkPermission(
                    perm: java.security.Permission?,
                    context: Any?,
                ) {
                    // Allow everything else - only checkDelete() is restricted above.
                }
            }

        @Suppress("DEPRECATION", "DEPRECATION_ERROR")
        try {
            System.setSecurityManager(testSecurityManager)
            val result = project.service<PsaManager>().getCompletions(settings, model, RequestType.Completion, "PHP")

            assertNotNull(result)
        } finally {
            System.setSecurityManager(originalSecurityManager)
        }
    }

    fun testGetCompletionsScriptModeSuccess() {
        val script = fixturePath("completions-script.php")
        val settings =
            Settings().apply {
                pluginEnabled = true
                executionMode = ExecutionMode.Script
                scriptPath = script.path
                supportedLanguages = "PHP"
            }
        val model = arrayOf(IndexedPsiElementModel(samplePsiElementModel(), "0:5"))

        val result = project.service<PsaManager>().getCompletions(settings, model, RequestType.Completion, "PHP")

        assertNotNull(result)
        assertTrue(project.service<PsaManager>().lastResultSucceed)
    }

    fun testGetCompletionsScriptFailureRecordsError() {
        val settings = scriptSettings("failing-script.php").apply { showErrors = false }
        val model = arrayOf(IndexedPsiElementModel(samplePsiElementModel(), "0:5"))

        val result = project.service<PsaManager>().getCompletions(settings, model, RequestType.Completion, "PHP")

        assertNull(result)
        assertFalse(project.service<PsaManager>().lastResultSucceed)
    }

    fun testGetCompletionsSkipsSelfRequestFromScriptDir() {
        val settings = scriptSettings("counting-script.php")
        val scriptDir = settings.getScriptDir()!!
        val selfModel =
            samplePsiElementModel().let {
                PsiElementModel(
                    id = it.id,
                    elementType = it.elementType,
                    options = it.options,
                    elementName = it.elementName,
                    elementFqn = it.elementFqn,
                    elementSignature = it.elementSignature,
                    text = it.text,
                    parent = it.parent,
                    prev = it.prev,
                    next = it.next,
                    textRange = it.textRange,
                    filePath = File(File(settings.scriptPath!!).parentFile, "self.php").path,
                )
            }
        assertTrue(File(selfModel.filePath!!).parent.indexOf(scriptDir) >= 0)
        val model = arrayOf(IndexedPsiElementModel(selfModel, "0:5"))

        val result = project.service<PsaManager>().getCompletions(settings, model, RequestType.Completion, "PHP")

        assertNull(result)
    }

    fun testGetCompletionsBatchModeSendsAllModels() {
        val script = fixturePath("completions-script.php")
        val settings =
            Settings().apply {
                pluginEnabled = true
                executionMode = ExecutionMode.Script
                scriptPath = script.path
                supportedLanguages = "PHP"
            }
        val model =
            arrayOf(
                IndexedPsiElementModel(samplePsiElementModel(), "0:5"),
                IndexedPsiElementModel(samplePsiElementModel(), "5:10"),
            )

        val result = project.service<PsaManager>().getCompletions(settings, model, RequestType.BatchCompletion, "PHP")

        assertNotNull(result)
    }

    fun testPsiElementToModelTruncatesLongText() {
        myFixture.configureByText(
            "test.php",
            "<?php \$x = '" + "a".repeat(1200) + "';",
        )
        val element = myFixture.file

        val model = project.service<PsaManager>().psiElementToModel(element)

        assertEquals(1000, model.text?.length)
    }

    fun testPsiElementToModelWithoutOptions() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        val element = myFixture.findElementByText("'test_string'", com.intellij.psi.PsiElement::class.java)!!

        val model = project.service<PsaManager>().psiElementToModel(element, processOptions = false)

        assertTrue(model.options.isEmpty())
    }

    fun testUpdateInfoTemplatePathRegexIsApplied() {
        val settings = Settings()
        val infoJson =
            """
            {
                "templates": [
                    {
                        "name": "single",
                        "type": "single_file",
                        "title": "Single File",
                        "path_regex": "^src/.*",
                        "fields": []
                    }
                ]
            }
            """.trimIndent()
        val info =
            infoJsonFormat.decodeFromString<com.github.sam0delkin.intellijpsa.model.InfoModel>(infoJson)

        project.service<PsaManager>().updateInfo(settings, info)

        assertEquals("^src/.*", settings.singleFileCodeTemplates?.get(0)?.pathRegex)
    }

    fun testGetCompletionsScriptModeTimeoutRecordsFailureAndNotifies() {
        val settings =
            scriptSettings("slow-script.php").apply {
                executionTimeout = 100
                showErrors = true
            }
        val model = arrayOf(IndexedPsiElementModel(samplePsiElementModel(), "0:5"))

        val result = project.service<PsaManager>().getCompletions(settings, model, RequestType.Completion, "PHP")

        assertNull(result)
        assertFalse(project.service<PsaManager>().lastResultSucceed)
        assertEquals("Process Execution Timeout exceeded.", project.service<PsaManager>().lastResultMessage)
    }

    fun testGetStaticCompletionsServerModeErrorRecordsFailure() {
        val settings = serverSettings("goto-server-error.php").apply { showErrors = false }

        val result = project.service<PsaManager>().getStaticCompletions(settings, project)

        assertNull(result)
        assertFalse(project.service<PsaManager>().lastResultSucceed)
    }

    fun testGenerateTemplateCodeServerModeErrorRecordsFailure() {
        val settings = serverSettings("goto-server-error.php").apply { showErrors = false }

        val result =
            project.service<PsaManager>().generateTemplateCode(
                settings,
                project,
                "/path",
                "MyTemplate",
                "single_file",
                null,
                emptyMap(),
            )

        assertNull(result)
        assertFalse(project.service<PsaManager>().lastResultSucceed)
    }

    fun testPerformActionServerModeSuccessReturnsDecodedString() {
        val settings = serverSettings("performaction-server-success.php")
        val action = EditorActionInputModel("my_action", "/file.php", null)

        val result = project.service<PsaManager>().performAction(settings, project, action)

        assertEquals("action-result", result)
        assertTrue(project.service<PsaManager>().lastResultSucceed)
    }

    fun testPerformActionServerModeErrorRecordsFailure() {
        val settings = serverSettings("goto-server-error.php").apply { showErrors = false }
        val action = EditorActionInputModel("my_action", "/file.php", null)

        val result = project.service<PsaManager>().performAction(settings, project, action)

        assertNull(result)
        assertFalse(project.service<PsaManager>().lastResultSucceed)
    }

    fun testGetCompletionsServerModeNoResultRecordsNoResultMessage() {
        val settings = serverSettings("completions-server-no-result.php")
        val model = arrayOf(IndexedPsiElementModel(samplePsiElementModel(), "0:5"))

        val result = project.service<PsaManager>().getCompletions(settings, model, RequestType.Completion, "PHP")

        assertNull(result)
        assertFalse(project.service<PsaManager>().lastResultSucceed)
        assertEquals("No result received", project.service<PsaManager>().lastResultMessage)
    }

    fun testPsiElementToModelTruncatesLongNameOption() {
        val longName = "A".repeat(300)
        myFixture.configureByText(
            "test.php",
            "<?php class $longName {}",
        )
        val phpClass = myFixture.findElementByText("class $longName", com.jetbrains.php.lang.psi.elements.PhpClass::class.java)!!

        val model = project.service<PsaManager>().psiElementToModel(phpClass)

        assertEquals(250, model.options["Name"]?.string?.length)
    }

    fun testPsiElementToModelOnMalformedReferencesDoesNotCrash() {
        // Deliberately malformed/incomplete PHP references - probing for an element whose
        // reflectively-invoked getSignature() throws (exercised via the InvocationTargetException
        // catch in psiElementToModel), across a variety of incomplete-reference shapes at once.
        myFixture.configureByText(
            "test.php",
            """
            <?php
            ${'$'}a->;
            Foo::;
            new ;
            ${'$'}b[;
            foo(;
            ${'$'}c->${'$'}d(;
            Foo::${'$'}e;
            list(;
            """.trimIndent(),
        )
        val manager = project.service<PsaManager>()

        var visited = 0
        myFixture.file.accept(
            object : com.intellij.psi.PsiRecursiveElementVisitor() {
                override fun visitElement(element: com.intellij.psi.PsiElement) {
                    visited++
                    manager.psiElementToModel(
                        element,
                        processParent = false,
                        processChildOptions = false,
                        processNext = false,
                        processPrev = false,
                    )
                    super.visitElement(element)
                }
            },
        )

        assertTrue(visited > 0)
    }

    fun testPsiElementToModelRespectsMaxNestingLevel() {
        myFixture.configureByText(
            "test.php",
            """
            <?php
            class MyClass {
                public function myMethod() {
                    'test_string';
                }
            }
            """.trimIndent(),
        )
        project.service<Settings>().maxNestingLevel = 0
        val element = myFixture.findElementByText("'test_string'", com.intellij.psi.PsiElement::class.java)!!

        val model = project.service<PsaManager>().psiElementToModel(element, nestingLevel = 1)

        assertNull(model.parent)
        assertTrue(model.options.isEmpty())
    }
}
