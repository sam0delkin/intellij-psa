package com.github.sam0delkin.intellijpsa.language.php.settings

import com.github.sam0delkin.intellijpsa.model.typeProvider.TypeProviderModel
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.PROJECT)
@State(
    name = "PhpPsaSettings",
    storages = [Storage("psa_php.xml")],
)
class PhpPsaSettings : PersistentStateComponent<PhpPsaSettings> {
    var enabled: Boolean = false
    var debugTypeProvider: Boolean = false
    var supportsTypeProviders: Boolean = false
    var typeProviders: ArrayList<TypeProviderModel>? = null

    @Override
    override fun getState(): PhpPsaSettings = this

    override fun loadState(settings: PhpPsaSettings) {
        XmlSerializerUtil.copyBean(settings, this)
    }
}
