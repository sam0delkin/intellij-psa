package com.github.sam0delkin.intellijpsa.action.psa

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.runWriteAction
import com.intellij.psi.PsiElement
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.EditorTextField
import com.intellij.ui.LanguageTextField
import com.intellij.util.ui.UIUtil

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

    private class Inspector(
        val template: LanguageTextField,
        val result: EditorTextField,
        val updateButton: ActionButton,
    )

    private fun createInspector(): Inspector {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        val element = myFixture.findElementByText("'test_string'", PsiElement::class.java)!!
        val panel = InspectPsiElementModelAction().createPanel(project, element)
        val fields = UIUtil.findComponentsOfType(panel, EditorTextField::class.java)

        return Inspector(
            fields.filterIsInstance<LanguageTextField>().single(),
            fields.single { it !is LanguageTextField },
            UIUtil.findComponentOfType(panel, ActionButton::class.java)!!,
        )
    }

    private fun Inspector.update(templateText: String) {
        runWriteAction { template.document.setText(templateText) }
        val action = updateButton.action
        action.actionPerformed(TestActionEvent.createTestEvent(action))
    }

    fun testPanelStartsWithElementVariableTemplateAndEmptyResult() {
        val inspector = createInspector()

        assertEquals("\$element", inspector.template.text)
        assertEquals("", inspector.result.text)
        assertEquals("Update Data", inspector.updateButton.action.templateText)
    }

    fun testUpdateDataRendersTemplateAgainstTheElement() {
        val inspector = createInspector()

        inspector.update("text=\$element.text")

        assertEquals("text='test_string'", inspector.result.text)
    }

    // An invalid template shows an error tooltip on the button and leaves the result untouched.
    fun testUpdateDataWithInvalidTemplateKeepsPreviousResult() {
        val inspector = createInspector()
        inspector.update("ok")

        inspector.update("#if(")

        assertEquals("ok", inspector.result.text)
    }
}
