package com.github.sam0delkin.intellijpsa.settings

import com.github.sam0delkin.intellijpsa.model.ExtendedStaticCompletionModel
import com.github.sam0delkin.intellijpsa.model.StaticCompletionModel
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Transient
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.Json

@Service(Service.Level.PROJECT)
@State(
    name = "PsaStaticCompletionsConfig",
    storages = [Storage("psa_static_completions_new.xml")],
)
class PsaStaticCompletionsConfig : PersistentStateComponent<PsaStaticCompletionsConfig> {
    var staticCompletionConfigsJson: String? = null

    @get:Transient
    @set:Transient
    var staticCompletionConfigs: ArrayList<StaticCompletionModel?>? = null
    private var extendedStaticCompletionConfigs: ArrayList<ExtendedStaticCompletionModel>? = null

    @Override
    override fun getState(): PsaStaticCompletionsConfig {
        staticCompletionConfigsJson = encode(staticCompletionConfigs, StaticCompletionModel.serializer().nullable)
        return this
    }

    override fun loadState(settings: PsaStaticCompletionsConfig) {
        XmlSerializerUtil.copyBean(settings, this)
        staticCompletionConfigs = decode(staticCompletionConfigsJson, StaticCompletionModel.serializer().nullable)
    }

    private fun <T> encode(
        value: List<T>?,
        serializer: KSerializer<T>,
    ): String? = value?.let { runCatching { JSON.encodeToString(ListSerializer(serializer), it) }.getOrNull() }

    private fun <T> decode(
        json: String?,
        serializer: KSerializer<T>,
    ): ArrayList<T>? = json?.let { runCatching { ArrayList(JSON.decodeFromString(ListSerializer(serializer), it)) }.getOrNull() }

    fun getExtendedStaticCompletionConfigs(project: Project): ArrayList<ExtendedStaticCompletionModel> {
        if (null !== this.extendedStaticCompletionConfigs) {
            return this.extendedStaticCompletionConfigs!!
        }

        runReadAction {
            this.extendedStaticCompletionConfigs = this.staticCompletionConfigs
                ?.filter { null !== it }
                ?.map {
                    ExtendedStaticCompletionModel.createFromModel(it!!, project)
                }?.toCollection(ArrayList()) ?: arrayListOf()
        }

        return this.extendedStaticCompletionConfigs ?: arrayListOf()
    }

    fun updateStaticCompletionConfigs(configs: MutableList<StaticCompletionModel>?) {
        val result = arrayListOf<StaticCompletionModel?>()
        result.addAll(configs?.toList() ?: emptyList())

        this.staticCompletionConfigs = result
        this.extendedStaticCompletionConfigs = null
    }

    private companion object {
        private val JSON = Json { ignoreUnknownKeys = true }
    }
}
