package com.github.sam0delkin.intellijpsa.statusBar

import com.github.sam0delkin.intellijpsa.icons.Icons
import com.github.sam0delkin.intellijpsa.services.CompletionService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.popup.PopupFactoryImpl.ActionGroupPopup
import com.intellij.util.Consumer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.Icon


class PsaStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String {
        return "psa.statusBar.widget_factory"
    }

    override fun getDisplayName(): String {
        return "Project Specific Autocomplete"
    }

    override fun isAvailable(project: Project): Boolean {
        val completionService = project.service<CompletionService>()
        val settings = completionService.getSettings()

        return settings.pluginEnabled
    }

    override fun createWidget(project: Project): StatusBarWidget {
        val completionService = project.service<CompletionService>()
        val settings = completionService.getSettings()
        var timer: Timer? = null

        return object : StatusBarWidget, StatusBarWidget.IconPresentation {

            override fun dispose() {
                if (null !== timer) {
                    timer!!.cancel()
                }
            }

            override fun ID(): String {
                return "psa.status_bar"
            }

            override fun install(statusBar: StatusBar) {
                if (null !== timer) {
                    timer!!.cancel()
                }
                timer = Timer()
                timer!!.schedule(object : TimerTask() {
                    override fun run() {
                        statusBar.updateWidget("psa.status_bar")
                    }
                }, 100, 100)
            }

            fun createPopup(context: DataContext): ListPopup {
                val actionGroup = DefaultActionGroup("PSA", false)
                actionGroup.add(object: AnAction("Update Info") {
                    override fun actionPerformed(e: AnActionEvent) {
                        if (null !== settings.scriptPath) {
                            val thread = Thread {
                                try {
                                    val info = completionService.getInfo(settings, project, settings.scriptPath!!)
                                    var filter: List<String> = listOf()
                                    var languages: List<String> = listOf()
                                    var templateCount = 0

                                    if (info.containsKey("goto_element_filter")) {
                                        filter =
                                            (info.get("goto_element_filter") as JsonArray).map { i -> i.jsonPrimitive.content }
                                    }

                                    if (info.containsKey("supported_languages")) {
                                        languages =
                                            (info.get("supported_languages") as JsonArray).map { i -> i.jsonPrimitive.content }
                                    }

                                    if (info.containsKey("templates")) {
                                        val templates = info.get("templates") as JsonArray
                                        templateCount = templates.size
                                    }
                                    completionService.updateInfo(settings, info)
                                    val languagesString =
                                        "<ul>" + languages.map { i -> "<li>$i</li>" }.joinToString("") + "</ul>"
                                    val filterString =
                                        "<ul>" + filter.map { i -> "<li>$i</li>" }.joinToString("") + "</ul>"
                                    NotificationGroupManager.getInstance()
                                        .getNotificationGroup("PSA Notification")
                                        .createNotification(
                                            "Successfully retrieved info: <br />" +
                                                    "Supported Languages: $languagesString<br />" +
                                                    "GoTo Element Filter: $filterString<br />" +
                                                    "Template Count: ${templateCount}", NotificationType.INFORMATION
                                        )
                                        .notify(project)
                                    completionService.lastResultSucceed = true
                                    completionService.lastResultMessage = ""
                                } catch (e: Exception) {
                                    completionService.lastResultSucceed = false
                                    completionService.lastResultMessage = e.message.toString()
                                    NotificationGroupManager.getInstance()
                                        .getNotificationGroup("PSA Notification")
                                        .createNotification(completionService.lastResultMessage, NotificationType.ERROR)
                                        .notify(project)
                                }
                            }
                            thread.start()
                        }
                    }

                })
                actionGroup.add(object: AnAction("Show Last Error") {
                    override fun actionPerformed(e: AnActionEvent) {
                        if ("" !== completionService.lastResultMessage) {
                            NotificationGroupManager.getInstance()
                                .getNotificationGroup("PSA Notification")
                                .createNotification(completionService.lastResultMessage, NotificationType.ERROR)
                                .notify(project)
                        }
                    }

                })
                return ActionGroupPopup(
                    "Project Specific Autocomplete",
                    actionGroup,
                    context,
                    false,
                    false,
                    false,
                    true,
                    null,
                    -1,
                    null,
                    null
                )
            }

            override fun getTooltipText(): String {
                if (completionService.lastResultSucceed) {
                    return "PSA: Working"
                }

                return "PSA: Error"
            }

            override fun getClickConsumer(): Consumer<MouseEvent> {
                val dataContext: DataContext = SimpleDataContext.builder()
                    .add(CommonDataKeys.PROJECT, project)
                    .add(PlatformDataKeys.CONTEXT_COMPONENT, IdeFocusManager.getInstance(project).focusOwner)
                    .build()

                return Consumer<MouseEvent> { createPopup(dataContext).showInBestPositionFor(dataContext) }
            }

            override fun getIcon(): Icon {
                if (completionService.lastResultSucceed) {
                    return Icons.PluginActiveIcon
                }

                return Icons.PluginErrorIcon
            }

            override fun getPresentation(): StatusBarWidget.WidgetPresentation {
                return this
            }
        }
    }

    override fun disposeWidget(widget: StatusBarWidget) {
        Disposer.dispose(widget)
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean {
        return true
    }
}