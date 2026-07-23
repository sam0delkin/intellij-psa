package com.github.sam0delkin.intellijpsa.settings

import com.github.sam0delkin.intellijpsa.model.StaticCompletionModel
import com.github.sam0delkin.intellijpsa.model.completion.CompletionsModel
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PsaStaticCompletionsConfigTest : BasePlatformTestCase() {
    private fun sampleModel(): StaticCompletionModel =
        StaticCompletionModel().apply {
            name = "my_completion"
            title = "My Completion"
            completions = CompletionsModel().apply { completions = arrayListOf() }
        }

    fun testGetStateAndLoadStateRoundTrip() {
        val config = PsaStaticCompletionsConfig()
        config.staticCompletionConfigs = arrayListOf(sampleModel())

        val state = config.state
        assertNotNull(state.staticCompletionConfigsJson)

        val restored = PsaStaticCompletionsConfig()
        restored.loadState(state)

        assertEquals(1, restored.staticCompletionConfigs?.size)
        assertEquals("my_completion", restored.staticCompletionConfigs?.get(0)?.name)
    }

    fun testEmptyStateRoundTrip() {
        val config = PsaStaticCompletionsConfig()

        val state = config.state
        val restored = PsaStaticCompletionsConfig()
        restored.loadState(state)

        assertNull(restored.staticCompletionConfigs)
    }

    fun testUpdateStaticCompletionConfigsResetsExtendedCache() {
        val config = PsaStaticCompletionsConfig()
        config.updateStaticCompletionConfigs(mutableListOf(sampleModel()))

        val extended = config.getExtendedStaticCompletionConfigs(project)
        assertEquals(1, extended.size)

        // Calling again should return the cached instance without recomputation.
        val extendedAgain = config.getExtendedStaticCompletionConfigs(project)
        assertSame(extended, extendedAgain)

        config.updateStaticCompletionConfigs(mutableListOf())
        val extendedAfterUpdate = config.getExtendedStaticCompletionConfigs(project)
        assertTrue(extendedAfterUpdate.isEmpty())
    }

    fun testGetExtendedStaticCompletionConfigsWithNoConfigsIsEmpty() {
        val config = PsaStaticCompletionsConfig()

        val extended = config.getExtendedStaticCompletionConfigs(project)

        assertTrue(extended.isEmpty())
    }
}
