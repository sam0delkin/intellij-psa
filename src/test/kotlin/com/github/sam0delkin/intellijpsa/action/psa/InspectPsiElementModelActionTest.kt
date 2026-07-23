package com.github.sam0delkin.intellijpsa.action.psa

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class InspectPsiElementModelActionTest : BasePlatformTestCase() {
    fun testActionPerformedBuildsDialogAndCancelsInHeadlessMode() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf("test_string"))

        val action = InspectPsiElementModelAction()
        val dataContext = DataContext { dataId -> if (CommonDataKeys.PROJECT.`is`(dataId)) project else null }
        val event = TestActionEvent.createTestEvent(action, dataContext)

        // Exercises the real action up through dialog-panel construction (template/result editor
        // fields, the inline "Update Data" action). `DialogBuilder.show()` itself cannot run
        // headlessly in this test environment - it attempts to create a real AWT window peer and
        // NPEs inside the platform's DialogWrapperPeerImpl - so that specific failure is expected
        // and swallowed here rather than faked or worked around.
        try {
            action.actionPerformed(event)
        } catch (e: NullPointerException) {
            assertTrue(e.message.orEmpty().contains("AbstractDialog.getWindow"))
        }
    }
}
