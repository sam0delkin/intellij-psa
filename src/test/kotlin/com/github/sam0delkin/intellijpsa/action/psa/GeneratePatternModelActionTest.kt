package com.github.sam0delkin.intellijpsa.action.psa

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.datatransfer.DataFlavor

class GeneratePatternModelActionTest : BasePlatformTestCase() {
    fun testActionPerformedCopiesPatternToClipboard() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf("test_string"))

        val action = GeneratePatternModelAction()
        val dataContext = DataContext { dataId -> if (CommonDataKeys.PROJECT.`is`(dataId)) project else null }
        val event = TestActionEvent.createTestEvent(action, dataContext)

        action.actionPerformed(event)

        val clipboardContent =
            (CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor) as? String)!!
        assertTrue(clipboardContent.contains("STRING_LITERAL") || clipboardContent.contains("with_text"))
    }

    fun testActionPerformedDoesNothingWithoutProject() {
        val action = GeneratePatternModelAction()
        val dataContext = DataContext { null }
        val event = TestActionEvent.createTestEvent(action, dataContext)

        action.actionPerformed(event)
    }
}
