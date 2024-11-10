package com.github.sam0delkin.intellijpsa.listener

import com.github.sam0delkin.intellijpsa.index.PsaIndex
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager

class PsaOpenedFileListener : FileEditorManagerListener {
    override fun fileOpened(
        source: FileEditorManager,
        file: VirtualFile,
    ) {
        val settings = source.project.service<Settings>()

        if (!settings.indexingEnabled) {
            return
        }

        val psiFile = PsiManager.getInstance(source.project).findFile(file)

        if (null === psiFile) {
            return
        }

        val psaIndex = source.project.service<PsaIndex>()
        psaIndex.getForFile(psiFile)
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        if (null === event.newFile) {
            return
        }

        val settings = event.manager.project.service<Settings>()

        if (!settings.indexingEnabled) {
            return
        }

        val psiFile = PsiManager.getInstance(event.manager.project).findFile(event.newFile!!)
        if (null === psiFile) {
            return
        }

        val psaIndex = event.manager.project.service<PsaIndex>()
        psaIndex.getForFile(psiFile)
    }
}
