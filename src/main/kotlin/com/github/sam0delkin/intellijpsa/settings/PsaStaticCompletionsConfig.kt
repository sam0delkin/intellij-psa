package com.github.sam0delkin.intellijpsa.settings

import com.github.sam0delkin.intellijpsa.model.ExtendedStaticCompletionModel
import com.github.sam0delkin.intellijpsa.model.StaticCompletionModel
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.PersistentStateComponentWithModificationTracker
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(
    name = "PSAStaticAutocompleteSettings",
    storages = [Storage("psa_static_completions.xml")],
)
class PsaStaticCompletionsConfig(
    private val project: Project,
) : PersistentStateComponentWithModificationTracker<PsaStaticCompletionsConfig.State> {
    private var myState = State()
    private var staticCompletionConfigs: MutableList<ExtendedStaticCompletionModel>? = null
    private var stateModificationCount = 0L

    data class State(
        var staticCompletionConfigs: MutableList<StaticCompletionModel>? = null,
    )

    override fun getState(): State = myState

    override fun getStateModificationCount(): Long = stateModificationCount

    override fun loadState(state: State) {
        myState =
            state.copy(
                staticCompletionConfigs =
                    state.staticCompletionConfigs,
            )
        runReadAction {
            staticCompletionConfigs =
                myState.staticCompletionConfigs
                    ?.map {
                        ExtendedStaticCompletionModel.createFromModel(it, project)
                    }?.toMutableList()
        }
    }

    fun getStaticCompletionConfigs(): MutableList<ExtendedStaticCompletionModel>? = this.staticCompletionConfigs

    fun updateStaticCompletionConfigs(configs: MutableList<StaticCompletionModel>?) {
        myState.staticCompletionConfigs = configs
        runReadAction {
            staticCompletionConfigs =
                myState.staticCompletionConfigs
                    ?.map {
                        ExtendedStaticCompletionModel.createFromModel(it, project)
                    }?.toMutableList()
        }
        stateModificationCount++
    }
}
