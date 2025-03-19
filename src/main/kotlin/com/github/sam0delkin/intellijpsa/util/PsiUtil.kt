package com.github.sam0delkin.intellijpsa.util

import com.github.sam0delkin.intellijpsa.psi.PsaElement
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import org.apache.commons.lang3.StringUtils

class PsiUtil {
    companion object {
        fun processLink(
            linkData: String,
            text: String,
            project: Project,
        ): PsiElement? {
            val settings = project.service<Settings>()
            val fm = VirtualFileManager.getInstance()
            val pm = PsiManager.getInstance(project)
            val link = linkData.split(':')
            val path = settings.replacePathMappings(link[0])
            val virtualFile = fm.findFileByUrl(project.guessProjectDir().toString() + path)

            if (null !== virtualFile) {
                val psiFile = pm.findFile(virtualFile)
                if (null !== psiFile) {
                    if (link.count() > 1) {
                        var position =
                            StringUtils.ordinalIndexOf(psiFile.originalFile.text, "\n", link[1].toInt())

                        if (link.count() > 2) {
                            position += link[2].toInt()
                        }

                        val element = psiFile.findElementAt(position)
                        if (null !== element) {
                            return PsaElement(element, text)
                        } else {
                            return PsaElement(psiFile.firstChild, psiFile.name)
                        }
                    } else {
                        return PsaElement(psiFile.firstChild, psiFile.name)
                    }
                }
            }

            return null
        }
    }
}
