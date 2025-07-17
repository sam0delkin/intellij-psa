package com.github.sam0delkin.intellijpsa.util

import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil

class ExecutionUtils {
    companion object {
        fun executeWithIndicatorAndTimeout(
            commandLine: GeneralCommandLine,
            indicator: ProgressIndicator,
            timeout: Int,
        ): ProcessOutput =
            CapturingProcessHandler(commandLine).runProcessWithProgressIndicator(
                indicator,
                timeout,
                true,
            )

        fun getCommandLine(
            settings: Settings,
            project: Project,
        ): GeneralCommandLine {
            val scriptPath = FileUtil.toCanonicalPath(settings.scriptPath)
            val commandLine =
                if (SystemInfo.isWindows) {
                    GeneralCommandLine("cmd.exe", "/c", project.guessProjectDir()?.path + "\\" + scriptPath)
                } else {
                    GeneralCommandLine(scriptPath)
                }

            return commandLine
        }
    }
}
