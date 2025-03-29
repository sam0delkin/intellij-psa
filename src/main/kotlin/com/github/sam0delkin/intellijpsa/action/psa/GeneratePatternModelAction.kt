package com.github.sam0delkin.intellijpsa.action.psa

import com.github.sam0delkin.intellijpsa.icons.Icons
import com.github.sam0delkin.intellijpsa.psi.helper.PsiElementModelHelper
import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

class GeneratePatternModelAction :
    AnAction(
        "Generate Pattern Model",
        "Generate Pattern Model and copy to clipboard",
        Icons.PluginIcon,
    ) {
    override fun actionPerformed(e: AnActionEvent) {
        if (null == e.project) {
            return
        }

        val editor: Editor = FileEditorManager.getInstance(e.project!!).selectedTextEditor ?: return
        val file = FileDocumentManager.getInstance().getFile(editor.document)
        val offset: Int = editor.caretModel.offset

        val element = PsiManager.getInstance(e.project!!).findFile(file!!)!!.findElementAt(offset) ?: return

        val psaManager = e.project!!.service<PsaManager>()

        val model = psaManager.psiElementToModel(element)
        val pattern = PsiElementModelHelper.toPattern(model)

        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(Json.encodeToString(pattern)), null)

        NotificationGroupManager
            .getInstance()
            .getNotificationGroup("PSA Notification")
            .createNotification(
                "Pattern code successfully copied to the clipboard",
                NotificationType.INFORMATION,
            ).notify(e.project)
    }
}
