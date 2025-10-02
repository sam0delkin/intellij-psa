package com.github.sam0delkin.intellijpsa.language.php.xdebugger.value.presentation

import com.jetbrains.php.debug.common.PhpCompactValuePresentation

class PsaPhpValuePresentation(
    var value: String,
    type: String?,
) : PhpCompactValuePresentation(value, type) {
    override fun renderValue(renderer: XValueTextRenderer) {
        renderer.renderValue(value)
    }
}
