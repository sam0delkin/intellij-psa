package com.github.sam0delkin.intellijpsa.language.php.services

import com.github.sam0delkin.intellijpsa.settings.ExecutionMode
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

class PhpPsaManagerTest : BasePlatformTestCase() {
    private fun fixturePath(name: String): File {
        val resource = javaClass.classLoader.getResource("server/$name")!!
        val file = File(resource.toURI())
        file.setExecutable(true)

        return file
    }

    fun testGetTypeProvidersReturnsNullAndRecordsFailureOnScriptError() {
        val settings =
            Settings().apply {
                pluginEnabled = true
                executionMode = ExecutionMode.Script
                scriptPath = fixturePath("failing-script.php").path
                showErrors = false
            }

        val providers = project.service<PhpPsaManager>().getTypeProviders(settings, project)

        assertNull(providers)
        assertFalse(project.service<com.github.sam0delkin.intellijpsa.services.PsaManager>().lastResultSucceed)
    }

    fun testUpdateTypeProvidersCancelsPreviousIndicatorOnSecondCall() {
        val script = fixturePath("counting-script.php")
        val counterFile = File(script.parentFile, script.name.removeSuffix(".php") + ".count")
        counterFile.delete()

        val settings =
            Settings().apply {
                pluginEnabled = true
                executionMode = ExecutionMode.Script
                scriptPath = script.path
            }
        val phpSettings = project.service<PhpPsaManager>().getSettings()
        phpSettings.supportsTypeProviders = true

        val manager = project.service<PhpPsaManager>()

        manager.updateTypeProviders(settings, phpSettings, project)
        manager.updateTypeProviders(settings, phpSettings, project)

        assertTrue(counterFile.exists())
    }

    fun testUpdateTypeProvidersNoOpWhenNotSupported() {
        val settings = Settings().apply { pluginEnabled = true }
        val phpSettings = project.service<PhpPsaManager>().getSettings()
        phpSettings.supportsTypeProviders = false

        project.service<PhpPsaManager>().updateTypeProviders(settings, phpSettings, project)
    }

    fun testUpdateTypeProvidersNoOpWhenNoScriptPath() {
        val settings =
            Settings().apply {
                pluginEnabled = true
                scriptPath = null
            }
        val phpSettings = project.service<PhpPsaManager>().getSettings()
        phpSettings.supportsTypeProviders = true

        project.service<PhpPsaManager>().updateTypeProviders(settings, phpSettings, project)
    }

    fun testGetTypeProvidersDecodesSuccessfulResponse() {
        val settings =
            Settings().apply {
                pluginEnabled = true
                executionMode = ExecutionMode.Script
                scriptPath = fixturePath("type-providers-success.php").path
            }

        val providers = project.service<PhpPsaManager>().getTypeProviders(settings, project)

        assertNotNull(providers)
        assertTrue(project.service<com.github.sam0delkin.intellijpsa.services.PsaManager>().lastResultSucceed)
    }

    fun testGetTypeProvidersWithAlreadyCancelledIndicator() {
        val settings =
            Settings().apply {
                pluginEnabled = true
                executionMode = ExecutionMode.Script
                scriptPath = fixturePath("counting-script.php").path
                showErrors = false
            }
        val indicator = EmptyProgressIndicator()
        indicator.cancel()

        val providers = project.service<PhpPsaManager>().getTypeProviders(settings, project, null, indicator)

        assertNull(providers)
    }
}
