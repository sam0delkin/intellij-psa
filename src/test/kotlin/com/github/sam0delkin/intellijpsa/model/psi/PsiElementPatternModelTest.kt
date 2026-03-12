package com.github.sam0delkin.intellijpsa.model.psi

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PsiElementPatternModelTest : BasePlatformTestCase() {
    fun testPsiElementPatternModelConstructor() {
        val pattern =
            PsiElementPatternModel(
                withText = "'test'",
                withType = "STRING_LITERAL",
            )

        assertEquals("'test'", pattern.withText)
        assertEquals("STRING_LITERAL", pattern.withType)
    }

    fun testPsiElementPatternModelWithParent() {
        val parentPattern =
            PsiElementPatternModel(
                withText = "assignment",
                withType = "AssignmentExpression",
            )

        val pattern =
            PsiElementPatternModel(
                withText = "'test'",
                withType = "STRING_LITERAL",
                parent = parentPattern,
            )

        assertNotNull(pattern.parent)
        assertEquals("assignment", pattern.parent?.withText)
        assertEquals("AssignmentExpression", pattern.parent?.withType)
    }

    fun testPsiElementPatternModelWithAnyParent() {
        val anyParentPattern =
            PsiElementPatternModel(
                withType = "Function",
            )

        val pattern =
            PsiElementPatternModel(
                withText = "'test'",
                anyParent = anyParentPattern,
            )

        assertNotNull(pattern.anyParent)
        assertEquals("Function", pattern.anyParent?.withType)
    }

    fun testPsiElementPatternModelWithMatcher() {
        val pattern =
            PsiElementPatternModel(
                withMatcher = "element_type == 'STRING_LITERAL' && text.startsWith(\"'\")",
            )

        assertEquals("element_type == 'STRING_LITERAL' && text.startsWith(\"'\")", pattern.withMatcher)
    }

    fun testPsiElementPatternModelWithOptions() {
        val options = mapOf("option1" to "value1", "option2" to "value2")

        val pattern =
            PsiElementPatternModel(
                withText = "test",
                withOptions = options,
            )

        assertNotNull(pattern.withOptions)
        assertEquals(2, pattern.withOptions?.size)
        assertEquals("value1", pattern.withOptions?.get("option1"))
        assertEquals("value2", pattern.withOptions?.get("option2"))
    }

    fun testPsiElementPatternModelDefaultValues() {
        val pattern = PsiElementPatternModel()

        assertNull(pattern.withText)
        assertNull(pattern.withType)
        assertNull(pattern.withOptions)
        assertNull(pattern.parent)
        assertNull(pattern.anyParent)
        assertNull(pattern.prev)
        assertNull(pattern.anyPrev)
        assertNull(pattern.next)
        assertNull(pattern.anyNext)
        assertNull(pattern.anyChild)
        assertNull(pattern.withMatcher)
    }

    fun testPsiElementPatternModelComplexHierarchy() {
        val pattern =
            PsiElementPatternModel(
                withText = "'completion'",
                withType = "STRING_LITERAL",
                parent =
                    PsiElementPatternModel(
                        withType = "AssignmentExpression",
                        parent =
                            PsiElementPatternModel(
                                withType = "Statement",
                                anyParent =
                                    PsiElementPatternModel(
                                        withType = "Function",
                                    ),
                            ),
                    ),
            )

        assertNotNull(pattern.parent)
        assertEquals("AssignmentExpression", pattern.parent?.withType)
        assertNotNull(pattern.parent?.parent)
        assertEquals("Statement", pattern.parent?.parent?.withType)
        assertNotNull(pattern.parent?.parent?.anyParent)
        assertEquals(
            "Function",
            pattern.parent
                ?.parent
                ?.anyParent
                ?.withType,
        )
    }
}
