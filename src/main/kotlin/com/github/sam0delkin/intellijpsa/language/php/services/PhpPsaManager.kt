package com.github.sam0delkin.intellijpsa.language.php.services

import com.github.sam0delkin.intellijpsa.language.php.model.PhpRequestType
import com.github.sam0delkin.intellijpsa.language.php.settings.PhpPsaSettings
import com.github.sam0delkin.intellijpsa.model.typeProvider.TypeProvidersModel
import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.github.sam0delkin.intellijpsa.services.server.ServerManager
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.github.sam0delkin.intellijpsa.util.ExecutionUtils
import com.intellij.execution.process.ProcessOutput
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
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
import kotlinx.serialization.json.Json

@Service(Service.Level.PROJECT)
class PhpPsaManager(
    private val project: Project,
) : Disposable {
    // Anchor for resources (e.g. PhpPsaExtension's XDebug message-bus connection) that must
    // not outlive this plugin's classloader. Light services are disposed by the platform both
    // on project close and on plugin unload, unlike plain `psaExtension` EP instances, which
    // are never disposed automatically.
    override fun dispose() {}

    fun getTypeProviders(
        settings: Settings,
        project: Project,
        debug: Boolean? = null,
        progressIndicator: ProgressIndicator? = null,
    ): TypeProvidersModel? {
        val innerDebug = if (null !== debug) debug else settings.debug
        val psaManager = project.service<PsaManager>()
        val indicator = progressIndicator ?: ProgressIndicatorProvider.getGlobalProgressIndicator() ?: EmptyProgressIndicator()

        try {
            val stdout: String =
                if (settings.isServerModeActive()) {
                    val response =
                        project.service<ServerManager>().sendRequest(
                            settings,
                            PhpRequestType.GetTypeProviders.toString(),
                            null,
                            innerDebug,
                            null,
                            null,
                        )

                    if (null !== response.error) {
                        throw Exception(response.error)
                    }

                    response.result?.toString() ?: throw Exception("Failed to get type providers")
                } else {
                    var result: ProcessOutput? = null
                    val commandLine = ExecutionUtils.getCommandLine(settings, project)
                    commandLine.environment["PSA_TYPE"] = PhpRequestType.GetTypeProviders.toString()
                    commandLine.environment["PSA_DEBUG"] = if (innerDebug) "1" else "0"
                    ExecutionUtils.setWorkDirectoryIfExists(commandLine, project)

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

                    if (null === result) {
                        throw Exception("Failed to get type providers")
                    }

                    if (result.isCancelled) {
                        throw ProcessCanceledException()
                    }

                    if (0 != result.exitCode) {
                        throw Exception(result.stdout + "\n" + result.stderr)
                    }

                    result.stdout
                }

            psaManager.lastResultSucceed = true
            psaManager.lastResultMessage = ""

            return runReadAction {
                val json = Json.decodeFromString<TypeProvidersModel>(stdout)

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
