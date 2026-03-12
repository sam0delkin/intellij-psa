package com.github.sam0delkin.intellijpsa.model.contributor

import com.github.sam0delkin.intellijpsa.model.psi.PsiElementPatternModel
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class StaticContributorModelTest : BasePlatformTestCase() {
    fun testStaticContributorScopeValues() {
        assertEquals(StaticContributorScope.File, StaticContributorScope.File)
        assertEquals(StaticContributorScope.Project, StaticContributorScope.Project)
    }

    fun testStaticContributorModelDefaultValues() {
        val model = StaticContributorModel()

        assertEquals("", model.name)
        assertNull(model.pathRegex)
        assertEquals(StaticContributorScope.File, model.scope)
        assertNull(model.pattern)
        assertEquals("", model.completionProvider)
    }

    fun testStaticContributorModel() {
        val pattern = PsiElementPatternModel(
            withText = "'test'",
            withType = "STRING_LITERAL"
        )

        val model = StaticContributorModel().apply {
            name = "my_contributor"
            pathRegex = "^/src/"
            scope = StaticContributorScope.Project
            this.pattern = pattern
            completionProvider = "provide_completion"
        }

        assertEquals("my_contributor", model.name)
        assertEquals("^/src/", model.pathRegex)
        assertEquals(StaticContributorScope.Project, model.scope)
        assertNotNull(model.pattern)
        assertEquals("'test'", model.pattern?.withText)
        assertEquals("provide_completion", model.completionProvider)
    }

    fun testStaticContributorModelNullPathRegex() {
        val model = StaticContributorModel().apply {
            name = "global_contributor"
            scope = StaticContributorScope.Project
            completionProvider = "global_completion"
        }

        assertNull(model.pathRegex)
    }
}
