package com.github.sam0delkin.intellijpsa.listener

import com.github.sam0delkin.intellijpsa.index.PsaIndex
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager

class PsaOpenedFileListener: FileEditorManagerListener {
    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        val psiFile = PsiManager.getInstance(source.project).findFile(file)
        if (null === psiFile) {
            return
        }

        val psaIndex = source.project.service<PsaIndex>()
        psaIndex.getForFile(psiFile)
    }
}