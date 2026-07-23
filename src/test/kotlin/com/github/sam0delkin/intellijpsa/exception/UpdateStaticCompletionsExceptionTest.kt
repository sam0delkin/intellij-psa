package com.github.sam0delkin.intellijpsa.exception

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class UpdateStaticCompletionsExceptionTest : BasePlatformTestCase() {
    fun testInstantiation() {
        val exception = UpdateStaticCompletionsException()

        assertNotNull(exception)
        assertNull(exception.message)
    }

    fun testThrowAndCatch() {
        try {
            throw UpdateStaticCompletionsException()
        } catch (e: UpdateStaticCompletionsException) {
            assertNotNull(e)
        }
    }
}
