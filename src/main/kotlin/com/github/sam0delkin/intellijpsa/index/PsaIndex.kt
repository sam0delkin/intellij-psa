@file:Suppress("PLUGIN_IS_NOT_ENABLED", "ktlint:standard:no-wildcard-imports")

package com.github.sam0delkin.intellijpsa.index

import com.github.sam0delkin.intellijpsa.exception.IndexNotReadyException
import com.github.sam0delkin.intellijpsa.exception.IndexingDisabledException
import com.github.sam0delkin.intellijpsa.model.PsiElementModel
import com.github.sam0delkin.intellijpsa.services.RequestType
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.gist.GistManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.concurrent.thread

@Serializable
data class IndexedPsiElementModel(
    val model: PsiElementModel,
    val textRange: String,
)

@Service(Service.Level.PROJECT)
class PsaIndex(
    private val project: Project,
) {
    fun get(
        type: RequestType,
        position: Int,
        key: String,
        filePath: String?,
    ): String? {
        val settings = this.project.service<Settings>()
        if (!settings.indexingEnabled || settings.debug) {
            throw IndexingDisabledException()
        }

        if (null === filePath) {
            return null
        }

        val virtualFile = VirtualFileManager.getInstance().findFileByUrl("file://$filePath")
        if (virtualFile != null) {
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
            if (psiFile != null) {
                val innerElement = psiFile.findElementAt(position)

                if (null !== innerElement) {
                    val data = PsaFileIndex.getForElement(innerElement, type)

                    if (null !== data) {
                        return data
                    }
                }

                val data = PsaFileIndex.getForFile(psiFile)
                try {
                    val map = Json.decodeFromString<Map<String, String>>(data)
                    if (map.containsKey(key)) {
                        return map[key]
                    }
                } catch (_: Exception) {
                    throw IndexNotReadyException()
                }
            }
        }

        return null
    }

    fun getForFile(file: PsiFile): String = PsaFileIndex.getForFile(file)

    fun reindexFile(
        file: PsiFile,
        force: Boolean = false,
    ) {
        ApplicationManager.getApplication().invokeLater {
            thread {
                GistManager.getInstance().invalidateData(file.virtualFile)
                runReadAction {
                    PsaFileIndex.reindexFile(file, force)
                }
            }
        }
    }

    fun reindexFile(file: VirtualFile) {
        val psiFile = PsiManager.getInstance(project).findFile(file)
        if (null === psiFile) {
            return
        }

        this.reindexFile(psiFile)
    }
}
