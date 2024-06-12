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
    var pluginEnabled: Boolean = false
    var debug: Boolean = false

    var phpEnabled: Boolean = false
    @Nullable
    var phpScriptPath: String? = ".psa/psa.php"
    @Nullable
    var phpPathMappings: Array<PathMapping>? = arrayOf()
    @Nullable
    var phpGoToFilter: String? = ""

    var jsEnabled: Boolean = false
    @Nullable
    var jsScriptPath: String? = ".psa/psa.js"
    @Nullable
    var jsPathMappings: Array<PathMapping>? = arrayOf()
    @Nullable
    var jsGoToFilter: String? = ""

    var tsEnabled: Boolean = false
    @Nullable
    var tsScriptPath: String? = ".psa/psa.ts"
    @Nullable
    var tsPathMappings: Array<PathMapping>? = arrayOf()
    @Nullable
    var tsGoToFilter: String? = ""

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

        if (
            language === Language.TS
            && this.tsEnabled
            && null !== this.tsScriptPath
        ) {
            return true
        }

        return false
    }

    fun getLanguageScriptPath(language: Language): String? {
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

        if (
            language === Language.TS
            && this.tsEnabled
            && null !== this.tsScriptPath
        ) {
            return this.tsScriptPath
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

        if (
            language === Language.TS
            && this.tsEnabled
            && null !== this.tsPathMappings
        ) {
            this.tsPathMappings!!.map { el -> resultStr = el.mapToLocal(resultStr) }
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

        if (language === Language.TS) {
            goToFilter = this.tsGoToFilter
        }

        if (null === goToFilter || "" == goToFilter) {
            return true
        }

        return goToFilter.split(",").any { e -> e == str }
    }

    fun setElementFilter(language: Language, filter: String) {
        if (language === Language.PHP) {
            this.phpGoToFilter = filter
        }

        if (language === Language.JS) {
            this.jsGoToFilter = filter
        }

        if (language === Language.TS) {
            this.tsGoToFilter = filter
        }
    }
}