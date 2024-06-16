package com.github.sam0delkin.intellijpsa.settings

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

    @Nullable
    var scriptPath: String? = ".psa/psa.php"
    @Nullable
    var pathMappings: Array<PathMapping>? = arrayOf()
    @Nullable
    var goToFilter: String? = ""
    @Nullable
    var supportedLanguages: String? = ""
    @Nullable
    var pluginVersion: String? = null

    @Nullable
    @Override
    override fun getState(): Settings {
        return this
    }

    override fun loadState(settings: Settings) {
        XmlSerializerUtil.copyBean(settings, this)
    }

    fun isLanguageSupported(language: String): Boolean {
        return this.pluginEnabled && this.supportedLanguages?.split(",")?.contains(language) == true
    }

    fun getScriptDir(): String? {
        val path = this.scriptPath

        if (null === path) {
            return null
        }
        val split = path.split("/")

        return split.slice(IntRange(0, split.size - 2)).joinToString("/")
    }

    fun replacePathMappings(str: String): String {
        var resultStr = str
        if (
            this.pluginEnabled
            && null !== this.pathMappings
        ) {
            this.pathMappings!!.map { el -> resultStr = el.mapToLocal(resultStr) }
        }

        return resultStr
    }

    fun isElementTypeMatchingFilter(str: String): Boolean {
        val goToFilter: String? = this.goToFilter

        if (null === goToFilter || "" == goToFilter) {
            return true
        }

        return goToFilter.split(",").any { e -> e == str }
    }

    fun setElementFilter(filter: String) {
        this.goToFilter = filter
    }
}