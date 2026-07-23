package com.github.sam0delkin.intellijpsa.extension.extensionPoints

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.dsl.builder.Panel

class PsaExtensionTest : BasePlatformTestCase() {
    private class MinimalExtension : PsaExtension {
        override fun configure(
            panel: Panel,
            project: Project,
        ) {}

        override fun isModified(project: Project): Boolean = false

        override fun reset(project: Project) {}

        override fun apply(project: Project) {}

        override fun updateInfo(
            project: Project,
            info: String,
        ) {}

        override fun modifyStatusBar(
            project: Project,
            actionGroup: ActionGroup,
        ) {}
    }

    fun testDefaultInitializeDoesNothing() {
        val extension = MinimalExtension()

        extension.initialize(project)
    }

    fun testDefaultGetDiagnosticsReturnsNull() {
        val extension = MinimalExtension()

        assertNull(extension.getDiagnostics(project))
    }
}
