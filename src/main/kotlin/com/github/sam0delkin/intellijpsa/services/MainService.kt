package com.github.sam0delkin.intellijpsa.services

import com.github.sam0delkin.intellijpsa.MyBundle
import com.github.sam0delkin.intellijpsa.completion.PsiElementModel
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.util.ExecUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.elementType
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpReferenceBase
import com.jetbrains.php.lang.psi.elements.ClassConstantReference
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.StatementWithArguments
import com.jetbrains.php.lang.psi.elements.Variable
import com.jetbrains.php.lang.psi.elements.impl.MethodImpl
import com.jetbrains.php.lang.psi.elements.impl.PhpClassImpl
import com.jetbrains.php.run.PhpExecutionUtil
import com.jetbrains.rd.util.string.printToString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.util.concurrent.Callable

class MainService {

    init {
        println(MyBundle.message("applicationService"))
    }
}
