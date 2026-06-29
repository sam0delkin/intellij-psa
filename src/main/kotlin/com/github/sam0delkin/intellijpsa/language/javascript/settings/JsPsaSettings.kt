package com.github.sam0delkin.intellijpsa.language.javascript.settings

import com.github.sam0delkin.intellijpsa.language.javascript.model.JsMethodArgumentProviderModel
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Transient
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Service(Service.Level.PROJECT)
@State(
    name = "JsPsaSettings",
    storages = [Storage("psa_js.xml")],
)
class JsPsaSettings : PersistentStateComponent<JsPsaSettings> {
    var enabled: Boolean = false
    var methodArgumentProvidersJson: String? = null

    @get:Transient
    @set:Transient
    var methodArgumentProviders: ArrayList<JsMethodArgumentProviderModel>? = null

    override fun getState(): JsPsaSettings {
        methodArgumentProvidersJson =
            methodArgumentProviders?.let { runCatching { JSON.encodeToString(SERIALIZER, it) }.getOrNull() }
        return this
    }

    override fun loadState(settings: JsPsaSettings) {
        XmlSerializerUtil.copyBean(settings, this)
        methodArgumentProviders =
            methodArgumentProvidersJson?.let { runCatching { ArrayList(JSON.decodeFromString(SERIALIZER, it)) }.getOrNull() }
    }

    private companion object {
        private val JSON = Json { ignoreUnknownKeys = true }
        private val SERIALIZER = ListSerializer(JsMethodArgumentProviderModel.serializer())
    }
}
