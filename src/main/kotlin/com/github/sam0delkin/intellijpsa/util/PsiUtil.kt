package com.github.sam0delkin.intellijpsa.util

import com.github.sam0delkin.intellijpsa.psi.PsaElement
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.lang.LighterASTTokenNode
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import org.apache.commons.lang3.StringUtils

class PsiUtil {
    companion object {
        fun processLink(
            linkData: String,
            text: String?,
            project: Project,
            appendProjectDir: Boolean = true,
        ): PsaElement? {
            val settings = project.service<Settings>()
            val fm = VirtualFileManager.getInstance()
            val pm = PsiManager.getInstance(project)
            val link = linkData.split(':').toMutableList()
            var path = settings.replacePathMappings(link[0])
            if (appendProjectDir) {
                path = project.guessProjectDir().toString() + path
            } else {
                path = link[0] + ":" + link[1]
                link[0] = path
                link[1] = "0"
                link[2] = (link[2].toInt() + 2).toString()
            }
            val virtualFile = fm.findFileByUrl(path)

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
                            return PsaElement(element, text ?: element.text)
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

        fun getLighterASTTokenNodeLink(
            file: VirtualFile,
            node: LighterASTTokenNode,
        ): String = "${file.path}:${node.startOffset}"

        fun normalizeElementText(text: String): String {
            var result = text
            if (result.length >= 2 && result[0] == '\'' && result[result.length - 1] == '\'') {
                result = result.substring(1, result.length - 1)
            }

            if (result.length >= 2 && result[0] == '"' && result[result.length - 1] == '"') {
                result = result.substring(1, result.length - 1)
            }

            return result
        }
    }
}
