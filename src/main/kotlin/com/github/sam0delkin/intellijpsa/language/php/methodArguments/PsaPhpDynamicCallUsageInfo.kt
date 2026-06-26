package com.github.sam0delkin.intellijpsa.language.php.methodArguments

import com.github.sam0delkin.intellijpsa.language.php.model.MethodArgumentProviderModel
import com.intellij.usageView.UsageInfo
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression

class PsaPhpDynamicCallUsageInfo(
    val array: ArrayCreationExpression,
    val provider: MethodArgumentProviderModel,
) : UsageInfo(array)
