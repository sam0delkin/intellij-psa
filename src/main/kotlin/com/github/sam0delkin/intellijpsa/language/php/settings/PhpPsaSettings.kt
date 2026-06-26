package com.github.sam0delkin.intellijpsa.language.php.settings

import com.github.sam0delkin.intellijpsa.language.php.model.MethodArgumentProviderModel
import com.github.sam0delkin.intellijpsa.model.typeProvider.TypeProviderModel
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Transient
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Service(Service.Level.PROJECT)
@State(
    name = "PhpPsaSettings",
    storages = [Storage("psa_php.xml")],
)
class PhpPsaSettings : PersistentStateComponent<PhpPsaSettings> {
    var enabled: Boolean = false
    var debugTypeProvider: Boolean = false
    var supportsTypeProviders: Boolean = false
    var toStringValueFormatter: String? = null
    var typeProvidersJson: String? = null
    var methodArgumentProvidersJson: String? = null

    @get:Transient
    @set:Transient
    var typeProviders: ArrayList<TypeProviderModel>? = null

    @get:Transient
    @set:Transient
    var methodArgumentProviders: ArrayList<MethodArgumentProviderModel>? = null

    override fun getState(): PhpPsaSettings {
        typeProvidersJson = encode(typeProviders, TypeProviderModel.serializer())
        methodArgumentProvidersJson = encode(methodArgumentProviders, MethodArgumentProviderModel.serializer())
        return this
    }

    override fun loadState(settings: PhpPsaSettings) {
        XmlSerializerUtil.copyBean(settings, this)
        typeProviders = decode(typeProvidersJson, TypeProviderModel.serializer())
        methodArgumentProviders = decode(methodArgumentProvidersJson, MethodArgumentProviderModel.serializer())
    }

    private fun <T> encode(
        value: List<T>?,
        serializer: KSerializer<T>,
    ): String? = value?.let { runCatching { JSON.encodeToString(ListSerializer(serializer), it) }.getOrNull() }

    private fun <T> decode(
        json: String?,
        serializer: KSerializer<T>,
    ): ArrayList<T>? = json?.let { runCatching { ArrayList(JSON.decodeFromString(ListSerializer(serializer), it)) }.getOrNull() }

    private companion object {
        private val JSON = Json { ignoreUnknownKeys = true }
    }
}
