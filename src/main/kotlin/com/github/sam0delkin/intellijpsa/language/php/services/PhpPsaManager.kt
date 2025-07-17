package com.github.sam0delkin.intellijpsa.language.php.services

import com.github.sam0delkin.intellijpsa.language.php.model.PhpRequestType
import com.github.sam0delkin.intellijpsa.language.php.settings.PhpPsaSettings
import com.github.sam0delkin.intellijpsa.model.typeProvider.TypeProvidersModel
import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.github.sam0delkin.intellijpsa.util.ExecutionUtils
import com.intellij.execution.process.ProcessOutput
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Service(Service.Level.PROJECT)
class PhpPsaManager(
    private val project: Project,
) {
    fun getTypeProviders(
        settings: Settings,
        project: Project,
        debug: Boolean? = null,
        progressIndicator: ProgressIndicator? = null,
    ): TypeProvidersModel? {
        var result: ProcessOutput?
        val innerDebug = if (null !== debug) debug else settings.debug
        val commandLine = ExecutionUtils.getCommandLine(settings, project)
        val psaManager = project.service<PsaManager>()

        commandLine.environment["PSA_TYPE"] = PhpRequestType.GetTypeProviders.toString()
        commandLine.environment["PSA_DEBUG"] = if (innerDebug) "1" else "0"
        commandLine.setWorkDirectory(project.guessProjectDir()?.path)
        val indicator = progressIndicator ?: ProgressIndicatorProvider.getGlobalProgressIndicator() ?: EmptyProgressIndicator()

        try {
            ApplicationUtil.runWithCheckCanceled(
                {
                    result =
                        ExecutionUtils.executeWithIndicatorAndTimeout(
                            commandLine,
                            indicator,
                            settings.executionTimeout,
                        )
                },
                indicator,
            )

            result =
                ExecutionUtils.executeWithIndicatorAndTimeout(
                    commandLine,
                    indicator,
                    settings.executionTimeout,
                )

            if (result!!.isCancelled) {
                throw ProcessCanceledException()
            }

            if (0 != result!!.exitCode) {
                throw Exception(result!!.stdout + "\n" + result!!.stderr)
            }

            psaManager.lastResultSucceed = true
            psaManager.lastResultMessage = ""

            return runReadAction {
                val json = Json.decodeFromString<TypeProvidersModel>(result!!.stdout)

                return@runReadAction json
            }
        } catch (e: Throwable) {
            psaManager.lastResultSucceed = false
            psaManager.lastResultMessage = e.message ?: "Unexpected Error"
            if (settings.debug || settings.showErrors) {
                NotificationGroupManager
                    .getInstance()
                    .getNotificationGroup("PSA Notification")
                    .createNotification(
                        "Failed to update type providers <br/>" + e.message,
                        NotificationType.ERROR,
                    ).notify(project)
            }
        }

        return null
    }

    fun updateTypeProviders(
        settings: Settings,
        phpSettings: PhpPsaSettings,
        project: Project,
        debug: Boolean? = null,
    ) {
        if (!phpSettings.supportsTypeProviders) {
            return
        }

        if (null == settings.scriptPath) {
            return
        }

        var previousIndicator: ProgressIndicator? = null

        ProgressManager.getInstance().run(
            object : Backgroundable(project, "PSA: Updating type providers ...") {
                override fun run(indicator: ProgressIndicator) {
                    if (previousIndicator != null) {
                        previousIndicator!!.cancel()
                    }

                    previousIndicator = indicator

                    if (indicator.isCanceled) {
                        return
                    }

                    val providers = getTypeProviders(settings, project, debug, indicator)

                    if (indicator.isCanceled) {
                        return
                    }

                    phpSettings.typeProviders = providers?.providers
                }
            },
        )
    }

    fun getSettings(): PhpPsaSettings = this.project.service()
}
