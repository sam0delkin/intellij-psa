package com.github.sam0delkin.intellijpsa

import com.github.sam0delkin.intellijpsa.services.ProjectService
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import java.util.Arrays
import java.util.Optional

class PSAStartupActivity: ProjectActivity {
    @Nullable
    fun getPluginById(@NotNull id: String?): IdeaPluginDescriptor? {
        val pluginId = PluginId.getId(id!!)
        return Arrays.stream(PluginManagerCore.plugins)
            .filter { descriptor -> pluginId == descriptor.getPluginId() }
            .findFirst()
            .orElse(null)
    }

    override suspend fun execute(project: Project) {
        val plugin = getPluginById("com.github.sam0delkin.intellijpsa");
        if (null === plugin) {
            return
        }

        val settings = project.service<ProjectService>().getSettings()
        val pluginVersion = plugin.version;
        val changeNotes = Optional.ofNullable(plugin.changeNotes).orElse("");

        if (pluginVersion != settings.pluginVersion) {
            settings.pluginVersion = pluginVersion
            NotificationGroupManager.getInstance()
                .getNotificationGroup("PSA Notification")
                .createNotification(changeNotes, NotificationType.INFORMATION)
                .setTitle("Project Specific Autocomplete Update")
                .notify(project);
        }
    }
}