package com.github.sam0delkin.intellijpsa.activity

import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.github.sam0delkin.intellijpsa.status.widget.PsaStatusBarWidgetFactory
import com.intellij.ide.util.RunOnceUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import java.io.File
import java.util.Timer
import java.util.TimerTask

class PsaStartupActivity :
    StartupActivity,
    DumbAware {
    private var timer: Timer? = null

    override fun runActivity(project: Project) {
        ApplicationManager.getApplication().invokeLater {
            val psaManager = project.service<PsaManager>()
            val settings = psaManager.getSettings()

            if (!settings.pluginEnabled) {
                val projectDir = project.guessProjectDir()

                if (null === projectDir) {
                    return@invokeLater
                }

                val scriptPath = projectDir.path + "/.psa/psa.sh"
                val file = File(scriptPath)
                if (file.exists() && file.canExecute()) {
                    RunOnceUtil.runOnceForProject(project, "project_specific_autocomplete") {
                        @Suppress("DialogTitleCapitalization")
                        val notification =
                            NotificationGroupManager
                                .getInstance()
                                .getNotificationGroup("PSA Notification")
                                .createNotification(
                                    "Project Specific Autocomplete",
                                    "PSA configuration found in the root of your project. " +
                                        "Would you like to enable it?",
                                    NotificationType.INFORMATION,
                                )
                        notification.addAction(
                            object : AnAction("Enable") {
                                override fun actionPerformed(e: AnActionEvent) {
                                    settings.pluginEnabled = true
                                    settings.scriptPath = ".psa/psa.sh"

                                    scheduleUpdate(project, settings, psaManager)
                                    notification.hideBalloon()
                                }
                            },
                        )
                        notification.notify(project)
                    }
                }
                return@invokeLater
            }

            val scriptDir = settings.getScriptDir()

            if (null === scriptDir) {
                return@invokeLater
            }

            ApplicationManager.getApplication().invokeLater {
                if (null !== timer) {
                    timer?.cancel()
                }

                scheduleUpdate(project, settings, psaManager)
            }
        }
    }

    private fun scheduleUpdate(
        project: Project,
        settings: Settings,
        psaManager: PsaManager,
    ) {
        timer = Timer()
        timer!!.schedule(
            object : TimerTask() {
                override fun run() {
                    try {
                        val info = psaManager.getInfo(settings, project, settings.scriptPath!!, false)
                        psaManager.updateInfo(settings, info)
                        psaManager.lastResultSucceed = true
                        psaManager.lastResultMessage = ""
                    } catch (e: Exception) {
                        psaManager.lastResultSucceed = false
                        psaManager.lastResultMessage = e.message.toString()
                    }
                    psaManager.updateStaticCompletions(settings, project, settings.scriptPath!!, false)
                    val psaStatusBarWidgetFactory = PsaStatusBarWidgetFactory()
                    if (null === project.service<StatusBarWidgetsManager>().findWidgetFactory(PsaStatusBarWidgetFactory.WIDGET_ID)) {
                        project.service<StatusBarWidgetsManager>().updateWidget(psaStatusBarWidgetFactory)
                    }
                    project.service<StatusBarWidgetsManager>().updateAllWidgets()
                }
            },
            500,
        )
    }
}
