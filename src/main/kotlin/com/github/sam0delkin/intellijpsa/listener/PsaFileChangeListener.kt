package com.github.sam0delkin.intellijpsa.listener

import com.github.sam0delkin.intellijpsa.index.INDEX_ID
import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import java.util.Timer
import java.util.TimerTask

class PsaFileChangeListener :
    AsyncFileListener,
    Disposable {
    private var timer: Timer? = null

    override fun prepareChange(events: MutableList<out VFileEvent>): AsyncFileListener.ChangeApplier? {
        val projects = ProjectManager.getInstance().openProjects

        for (project in projects) {
            val psaManager = project.service<PsaManager>()
            val settings = psaManager.getSettings()

            if (!settings.pluginEnabled || null === settings.scriptPath) {
                continue
            }

            var scriptDir = settings.getScriptDir()
            if (null === scriptDir) {
                continue
            }
            val projectDir = project.guessProjectDir()
            if (null === projectDir) {
                continue
            }

            scriptDir = projectDir.path + '/' + scriptDir
            if (events.none { e -> e.path.indexOf(scriptDir) >= 0 }) {
                events.map {
                    if (null == it.file) {
                        return@map
                    }

                    if (!DumbService.isDumb(project)) {
                        val files =
                            FileBasedIndex
                                .getInstance()
                                .getContainingFiles(
                                    INDEX_ID,
                                    it.file!!.url,
                                    GlobalSearchScope.projectScope(project),
                                )

                        files.map {
                            FileBasedIndex.getInstance().requestReindex(it)
                        }
                    }
                }
                continue
            }

            ApplicationManager.getApplication().invokeLater {
                if (null !== timer) {
                    timer?.cancel()
                }

                timer = Timer()
                timer!!.schedule(
                    object : TimerTask() {
                        override fun run() {
                            try {
                                val info = psaManager.getInfo(settings, project, false)
                                psaManager.updateInfo(settings, info)
                                psaManager.lastResultSucceed = true
                                psaManager.lastResultMessage = ""
                            } catch (e: Exception) {
                                psaManager.lastResultSucceed = false
                                psaManager.lastResultMessage = e.message.toString()
                            }
                            psaManager.updateStaticCompletions(settings, project, false)
                        }
                    },
                    500,
                )
            }
        }

        return null
    }

    override fun dispose() {
        if (null !== timer) {
            timer!!.cancel()
            timer = null
        }
    }
}
