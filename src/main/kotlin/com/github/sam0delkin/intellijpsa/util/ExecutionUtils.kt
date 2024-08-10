package com.github.sam0delkin.intellijpsa.util

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.progress.ProgressIndicator

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
    }
}
