package com.github.sam0delkin.intellijpsa.listener

import com.github.sam0delkin.intellijpsa.services.CompletionService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import java.util.Timer
import java.util.TimerTask

class PsaFileChangeListener : AsyncFileListener {
    private var timer: Timer? = null

    override fun prepareChange(events: MutableList<out VFileEvent>): AsyncFileListener.ChangeApplier? {
        val projects = ProjectManager.getInstance().openProjects

        for (project in projects) {
            val completionService = project.service<CompletionService>()
            val settings = completionService.getSettings()

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
                                val info = completionService.getInfo(settings, project, settings.scriptPath!!, false)
                                completionService.updateInfo(settings, info)
                                completionService.lastResultSucceed = true
                                completionService.lastResultMessage = ""
                            } catch (e: Exception) {
                                completionService.lastResultSucceed = false
                                completionService.lastResultMessage = e.message.toString()
                            }
                        }
                    },
                    500,
                )
            }
        }

        return null
    }
}
