package com.github.sam0delkin.intellijpsa.settings

import com.github.sam0delkin.intellijpsa.services.Language
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.PathMappingSettings.PathMapping
import com.intellij.util.xmlb.XmlSerializerUtil
import org.jetbrains.annotations.Nullable

@State(
    name = "PSAAutocompleteSettings",
    storages = [Storage("psa.xml")]
)
class Settings : PersistentStateComponent<Settings> {
    var pluginEnabled: Boolean = false;
    var debug: Boolean = false;

    var phpEnabled: Boolean = false;
    @Nullable
    var phpScriptPath: String? = ".psa/psa.php"
    @Nullable
    var phpPathMappings: Array<PathMapping>? = arrayOf()
    @Nullable
    var phpGoToFilter: String? = ""

    var jsEnabled: Boolean = false;
    @Nullable
    var jsScriptPath: String? = ".psa/psa.js"
    @Nullable
    var jsPathMappings: Array<PathMapping>? = arrayOf()
    @Nullable
    var jsGoToFilter: String? = ""

    @Nullable
    @Override
    override fun getState(): Settings {
        return this
    }

    override fun loadState(settings: Settings) {
        XmlSerializerUtil.copyBean(settings, this)
    }

    fun isLanguageEnabled(language: Language): Boolean {
        if (
            language === Language.PHP
            && this.phpEnabled
            && null !== this.phpScriptPath
        ) {
            return true
        }

        if (
            language === Language.JS
            && this.jsEnabled
            && null !== this.jsScriptPath
        ) {
            return true
        }

        return false
    }

    private fun getLanguageScriptPath(language: Language): String? {
        if (
            language === Language.PHP
            && this.phpEnabled
            && null !== this.phpScriptPath
        ) {
            return this.phpScriptPath
        }

        if (
            language === Language.JS
            && this.jsEnabled
            && null !== this.jsScriptPath
        ) {
            return this.jsScriptPath
        }

        return null
    }

    fun getLanguageScriptDir(language: Language): String? {
        val path = this.getLanguageScriptPath(language)

        if (null === path) {
            return null
        }
        val split = path.split("/")

        return split.slice(IntRange(0, split.size - 1)).joinToString("/")
    }

    fun replacePathMappings(language: Language, str: String): String {
        var resultStr = str
        if (
            language === Language.PHP
            && this.phpEnabled
            && null !== this.phpPathMappings
        ) {
            this.phpPathMappings!!.map { el -> resultStr = el.mapToLocal(resultStr) }
        }

        if (
            language === Language.JS
            && this.jsEnabled
            && null !== this.jsPathMappings
        ) {
            this.jsPathMappings!!.map { el -> resultStr = el.mapToLocal(resultStr) }
        }

        return resultStr
    }

    fun isElementTypeMatchingFilter(language: Language, str: String): Boolean {
        var goToFilter: String? = null

        if (language === Language.PHP) {
            goToFilter = this.phpGoToFilter
        }

        if (language === Language.JS) {
            goToFilter = this.jsGoToFilter
        }

        if (null === goToFilter || "" == goToFilter) {
            return true
        }

        return goToFilter.split(",").any { e -> e == str }
    }
}