package com.github.sam0delkin.intellijpsa.services.server

import com.github.sam0delkin.intellijpsa.model.RequestType
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.github.sam0delkin.intellijpsa.util.ExecutionUtils
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.Alarm
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ExecutionException as JavaExecutionException

@Service(Service.Level.PROJECT)
class ServerManager(
    private val project: Project,
) : Disposable {
    private val startLock = Any()
    private val writeLock = Any()
    private val bufferLock = Any()

    @Volatile
    private var processHandler: KillableProcessHandler? = null
    private val stdoutBuffer = StringBuilder()
    private val pending = ConcurrentHashMap<String, CompletableFuture<ServerResponseEnvelope>>()
    private val idCounter = AtomicLong(0)
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var state: ServerState = ServerState.STOPPED

    @Volatile
    private var restartWarningShown = false

    @Volatile
    private var retryCount: Int = 0
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

    private val fileChangeAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

    internal var maxRetries = 5
    internal var initialBackoffMs = 1000L
    internal var maxBackoffMs = 30000L
    internal var fileChangeDebounceMs = 1000L
    internal var killTimeoutMs = 500L

    fun status(): ServerState = state

    fun consumeRestartWarning(): Boolean {
        if (state == ServerState.RUNNING) {
            return false
        }

        synchronized(startLock) {
            if (restartWarningShown) {
                return false
            }

            restartWarningShown = true
            return true
        }
    }

    fun scheduleRestartOnFileChange(settings: Settings) {
        if (!settings.isServerModeActive()) {
            return
        }

        fileChangeAlarm.cancelAllRequests()
        fileChangeAlarm.addRequest({ restart(settings) }, fileChangeDebounceMs)
    }

    fun start(settings: Settings): KillableProcessHandler {
        synchronized(startLock) {
            retryCount = 0
            restartWarningShown = false
            alarm.cancelAllRequests()
        }

        return ensureStarted(settings)
    }

    fun sendRequest(
        settings: Settings,
        type: String,
        language: String?,
        debug: Boolean,
        offset: Int?,
        context: JsonElement?,
    ): ServerResponse {
        val handler =
            try {
                ensureStarted(settings)
            } catch (e: Throwable) {
                state = ServerState.FAILED
                return ServerResponse(null, "Failed to start server process: ${e.message}")
            }

        val id = "req-${idCounter.incrementAndGet()}"
        val future = CompletableFuture<ServerResponseEnvelope>()
        pending[id] = future

        try {
            val line = json.encodeToString(ServerRequestEnvelope(id, type, language, debug, offset, context))
            synchronized(writeLock) {
                val input = handler.processInput
                input.write((line + "\n").toByteArray(StandardCharsets.UTF_8))
                input.flush()
            }
        } catch (e: Throwable) {
            pending.remove(id)
            return ServerResponse(null, "Failed to write to server stdin: ${e.message}")
        }

        val response =
            try {
                val envelope = future.get(settings.executionTimeout.toLong(), TimeUnit.MILLISECONDS)
                ServerResponse(envelope.result, envelope.error)
            } catch (_: TimeoutException) {
                ServerResponse(null, "Server request timed out after ${settings.executionTimeout}ms")
            } catch (e: JavaExecutionException) {
                ServerResponse(null, e.cause?.message ?: "Unexpected server error")
            } finally {
                pending.remove(id)
            }

        return response
    }

    private fun ensureStarted(settings: Settings): KillableProcessHandler =
        synchronized(startLock) {
            val existing = processHandler
            if (existing != null && !existing.isProcessTerminated) {
                return@synchronized existing
            }

            synchronized(bufferLock) { stdoutBuffer.setLength(0) }

            val commandLine = ExecutionUtils.getCommandLine(settings, project)
            commandLine.environment["PSA_TYPE"] = RequestType.StartServer.toString()
            ExecutionUtils.setWorkDirectoryIfExists(commandLine, project)
            val handler = KillableProcessHandler(commandLine)

            handler.addProcessListener(
                object : ProcessAdapter() {
                    override fun onTextAvailable(
                        event: ProcessEvent,
                        outputType: Key<*>,
                    ) {
                        if (outputType !== ProcessOutputTypes.STDOUT) return
                        if (event.processHandler !== processHandler) return
                        onStdoutChunk(event.text)
                    }

                    override fun processTerminated(event: ProcessEvent) {
                        if (event.processHandler !== processHandler) return
                        failAllPending("Server process terminated unexpectedly (exit code ${event.exitCode})")
                        processHandler = null
                        scheduleAutoRestart(settings)
                    }
                },
            )
            handler.startNotify()
            processHandler = handler
            state = ServerState.STARTING
            confirmHealthy(settings, handler)

            handler
        }

    private fun confirmHealthy(
        settings: Settings,
        handler: KillableProcessHandler,
    ) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val response = sendRequest(settings, RequestType.Info.toString(), null, settings.debug, null, null)

            synchronized(startLock) {
                if (processHandler !== handler) {
                    return@synchronized
                }

                if (response.error == null) {
                    state = ServerState.RUNNING
                } else {
                    processHandler = null
                    if (!handler.isProcessTerminated) {
                        handler.killProcess()
                        handler.waitFor(killTimeoutMs)
                    }
                    scheduleAutoRestart(settings)
                }
            }
        }
    }

    private fun scheduleAutoRestart(settings: Settings) {
        synchronized(startLock) {
            if (retryCount >= maxRetries) {
                state = ServerState.FAILED
                return
            }

            state = ServerState.RETRYING
            restartWarningShown = false
            val delay = minOf(initialBackoffMs * (1L shl retryCount), maxBackoffMs)
            retryCount++

            alarm.addRequest({
                try {
                    ensureStarted(settings)
                } catch (_: Throwable) {
                    scheduleAutoRestart(settings)
                }
            }, delay)
        }
    }

    private fun onStdoutChunk(chunk: String) {
        val lines = mutableListOf<String>()
        synchronized(bufferLock) {
            stdoutBuffer.append(chunk)
            var idx = stdoutBuffer.indexOf("\n")
            while (idx >= 0) {
                lines.add(stdoutBuffer.substring(0, idx))
                stdoutBuffer.delete(0, idx + 1)
                idx = stdoutBuffer.indexOf("\n")
            }
        }
        for (line in lines) handleLine(line)
    }

    private fun handleLine(line: String) {
        if (line.isBlank()) return
        try {
            val envelope = json.decodeFromString<ServerResponseEnvelope>(line)
            pending.remove(envelope.id)?.complete(envelope)
        } catch (_: Throwable) {
        }
    }

    private fun failAllPending(message: String) {
        val ids = pending.keys.toList()
        for (id in ids) {
            pending.remove(id)?.completeExceptionally(IllegalStateException(message))
        }
    }

    fun restart(settings: Settings? = null) {
        state = ServerState.RESTARTING
        restartWarningShown = false

        if (ApplicationManager.getApplication().isDispatchThread) {
            ApplicationManager.getApplication().executeOnPooledThread { performRestart(settings) }
            return
        }

        performRestart(settings)
    }

    private fun performRestart(settings: Settings?) {
        synchronized(startLock) {
            alarm.cancelAllRequests()
            fileChangeAlarm.cancelAllRequests()
            val handler = processHandler
            processHandler = null
            retryCount = 0
            failAllPending("Server process restarted")
            synchronized(bufferLock) { stdoutBuffer.setLength(0) }
            if (handler != null && !handler.isProcessTerminated) {
                handler.killProcess()
                handler.waitFor(killTimeoutMs)
            }
        }

        if (settings != null && settings.isServerModeActive()) {
            try {
                start(settings)
            } catch (_: Throwable) {
                state = ServerState.FAILED
            }
        } else {
            state = ServerState.STOPPED
        }
    }

    override fun dispose() = restart()
}
