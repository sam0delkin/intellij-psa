package com.github.sam0delkin.intellijpsa.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.PathMappingSettings.PathMapping
import com.intellij.util.xmlb.XmlSerializerUtil

enum class TemplateFormFieldType {
    Text, RichText, Checkbox, Select, Collection
}

class TemplateFormField {
    var name: String? = null
    var title: String? = null
    var type: TemplateFormFieldType? = null
    var options: ArrayList<String>? = null

    override fun toString(): String {
        return "TemplateFormField(name=$name, title=$title, type=$type, options=$options)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TemplateFormField

        if (name != other.name) return false
        if (title != other.title) return false
        if (type != other.type) return false
        if (options?.joinToString(",") != other.options?.joinToString(",")) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name?.hashCode() ?: 0
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + (type?.hashCode() ?: 0)
        result = 31 * result + (options?.hashCode() ?: 0)
        return result
    }
}

class SingleFileCodeTemplate {
    var pathRegex: String? = null
    var name: String? = null
    var title: String? = null
        var formFields: ArrayList<TemplateFormField>? = null

    override fun toString(): String {
        return "SingleFileCodeTemplate(filePath=$pathRegex, name=$name, title=$title, formFields=$formFields)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SingleFileCodeTemplate

        if (pathRegex != other.pathRegex) return false
        if (name != other.name) return false
        if (title != other.title) return false
        if (formFields != other.formFields) return false

        return true
    }

    override fun hashCode(): Int {
        var result = pathRegex?.hashCode() ?: 0
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + (formFields?.hashCode() ?: 0)
        return result
    }
}

@State(
    name = "PSAAutocompleteSettings",
    storages = [Storage("psa.xml")]
)
class Settings : PersistentStateComponent<Settings> {
    var pluginEnabled: Boolean = false
    var debug: Boolean = false
    var scriptPath: String? = ".psa/psa.php"
    var indexingConcurrency: Int = Runtime.getRuntime().availableProcessors()
    var elementPaths: HashMap<String, Boolean> = HashMap()
    var elementTypes: HashMap<String, Boolean> = HashMap()
    var pathMappings: Array<PathMapping>? = arrayOf()
    var goToFilter: String? = ""
    var supportsBatch: Boolean = false
    var supportedLanguages: String? = ""
    var singleFileCodeTemplates: ArrayList<SingleFileCodeTemplate>? = null
    var executionTimeout: Int = 5000

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
}