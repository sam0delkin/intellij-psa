package com.github.sam0delkin.intellijpsa.status.widget

import com.github.sam0delkin.intellijpsa.icons.Icons
import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.github.sam0delkin.intellijpsa.settings.ProjectSettingsForm
import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Consumer
import java.awt.event.MouseEvent
import java.util.Timer
import java.util.TimerTask
import javax.swing.Icon

class PsaStatusBarWidgetFactory : StatusBarWidgetFactory {
    companion object {
        const val WIDGET_ID = "psa.status_bar"
    }

    override fun getId(): String = "psa.statusBar.widget_factory"

    override fun getDisplayName(): String = "Project Specific Autocomplete"

    override fun isAvailable(project: Project): Boolean =
        try {
            val psaManager = project.service<PsaManager>()
            val settings = psaManager.getSettings()

            settings.pluginEnabled
        } catch (e: IllegalStateException) {
            false
        }

    override fun createWidget(project: Project): StatusBarWidget {
        val psaManager = project.service<PsaManager>()
        val settings = psaManager.getSettings()
        var timer: Timer? = null

        return object : StatusBarWidget, StatusBarWidget.IconPresentation {
            override fun dispose() {
                if (null !== timer) {
                    timer!!.cancel()
                }
            }

            override fun ID(): String = WIDGET_ID

            override fun install(statusBar: StatusBar) {
                if (null !== timer) {
                    timer!!.cancel()
                }
                timer = Timer()
                timer!!.schedule(
                    object : TimerTask() {
                        override fun run() {
                            statusBar.updateWidget("psa.status_bar")
                        }
                    },
                    100,
                    100,
                )
            }

            fun createPopup(context: DataContext): ListPopup {
                val actionGroup = DefaultActionGroup("PSA", false)
                actionGroup.add(
                    object : AnAction("Settings", "", AllIcons.General.Settings) {
                        override fun actionPerformed(e: AnActionEvent) {
                            ShowSettingsUtil.getInstance().editConfigurable(project, ProjectSettingsForm(project))
                        }
                    },
                )
                if (settings.debug) {
                    actionGroup.add(
                        object : AnAction("Disable Debug", "", AllIcons.Actions.RestartDebugger) {
                            override fun actionPerformed(e: AnActionEvent) {
                                settings.debug = false
                            }
                        },
                    )
                } else {
                    actionGroup.add(
                        object : AnAction("Enable Debug", "", AllIcons.Actions.StartDebugger) {
                            override fun actionPerformed(e: AnActionEvent) {
                                settings.debug = true
                            }
                        },
                    )
                }
                if (settings.supportsStaticCompletions) {
                    actionGroup.add(
                        object : AnAction("Update Static Completions", "", AllIcons.Actions.Refresh) {
                            override fun actionPerformed(e: AnActionEvent) {
                                psaManager.updateStaticCompletions(
                                    settings,
                                    project,
                                    settings.scriptPath!!,
                                    settings.debug,
                                )
                            }
                        },
                    )
                }
                actionGroup.add(
                    object : AnAction("Update Info", "", AllIcons.General.BalloonInformation) {
                        override fun actionPerformed(e: AnActionEvent) {
                            if (null !== settings.scriptPath) {
                                val thread =
                                    Thread {
                                        try {
                                            val info = psaManager.getInfo(settings, project, settings.scriptPath!!)
                                            var filter: List<String> = listOf()
                                            var templateCount = 0

                                            if (null != info.goToElementFilter) {
                                                filter = info.goToElementFilter
                                            }

                                            val languages: List<String> = info.supportedLanguages

                                            if (info.templates != null) {
                                                val templates = info.templates
                                                templateCount = templates.size
                                            }
                                            psaManager.updateInfo(settings, info)
                                            val languagesString =
                                                "<ul>" + languages.joinToString("") { i -> "<li>$i</li>" } + "</ul>"
                                            val filterString =
                                                "<ul>" + filter.joinToString("") { i -> "<li>$i</li>" } + "</ul>"
                                            NotificationGroupManager
                                                .getInstance()
                                                .getNotificationGroup("PSA Notification")
                                                .createNotification(
                                                    "Successfully retrieved info: <br />" +
                                                        "Supported Languages: $languagesString<br />" +
                                                        "GoTo Element Filter: $filterString<br />" +
                                                        "Editor Actions: ${info.editorActions?.size ?: 0}<br />" +
                                                        "Template Count: $templateCount",
                                                    NotificationType.INFORMATION,
                                                ).notify(project)
                                            psaManager.lastResultSucceed = true
                                            psaManager.lastResultMessage = ""
                                        } catch (e: Exception) {
                                            psaManager.lastResultSucceed = false
                                            psaManager.lastResultMessage = e.message.toString()
                                            NotificationGroupManager
                                                .getInstance()
                                                .getNotificationGroup("PSA Notification")
                                                .createNotification(
                                                    psaManager.lastResultMessage,
                                                    NotificationType.ERROR,
                                                ).notify(project)
                                        }
                                        project.service<StatusBarWidgetsManager>().updateAllWidgets()
                                    }
                                thread.start()
                            }
                        }
                    },
                )
                actionGroup.add(
                    object : AnAction("Show Last Error", "", AllIcons.Debugger.Db_exception_breakpoint) {
                        override fun actionPerformed(e: AnActionEvent) {
                            if ("" !== psaManager.lastResultMessage) {
                                NotificationGroupManager
                                    .getInstance()
                                    .getNotificationGroup("PSA Notification")
                                    .createNotification(psaManager.lastResultMessage, NotificationType.ERROR)
                                    .notify(project)
                            }
                        }
                    },
                )
                return JBPopupFactory.getInstance().createActionGroupPopup(
                    "Project Specific Autocomplete",
                    actionGroup,
                    context,
                    JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                    false,
                )
            }

            override fun getTooltipText(): String {
                if (psaManager.lastResultSucceed) {
                    return "PSA: Working"
                }

                return "PSA: Error"
            }

            override fun getClickConsumer(): Consumer<MouseEvent> {
                val dataContext: DataContext = SimpleDataContext.builder().build()

                return Consumer<MouseEvent> { e -> createPopup(dataContext).show(RelativePoint(e)) }
            }

            override fun getIcon(): Icon {
                if (psaManager.lastResultSucceed) {
                    return Icons.PluginActiveIcon
                }

                return Icons.PluginErrorIcon
            }

            override fun getPresentation(): StatusBarWidget.WidgetPresentation = this
        }
    }

    override fun disposeWidget(widget: StatusBarWidget) {
        Disposer.dispose(widget)
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}
