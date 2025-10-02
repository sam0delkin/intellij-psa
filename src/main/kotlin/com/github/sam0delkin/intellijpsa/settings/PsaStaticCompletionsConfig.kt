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

@Service(Service.Level.PROJECT)
@State(
    name = "PsaStaticCompletionsConfig",
    storages = [Storage("psa_static_completions_new.xml")],
)
class PsaStaticCompletionsConfig : PersistentStateComponent<PsaStaticCompletionsConfig> {
    var staticCompletionConfigs: ArrayList<StaticCompletionModel?>? = null
    private var extendedStaticCompletionConfigs: ArrayList<ExtendedStaticCompletionModel>? = null

    @Override
    override fun getState(): PsaStaticCompletionsConfig = this

    override fun loadState(settings: PsaStaticCompletionsConfig) {
        XmlSerializerUtil.copyBean(settings, this)
    }

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
}
