package com.github.sam0delkin.intellijpsa.psi.helper

import com.github.sam0delkin.intellijpsa.model.psi.PsiElementModel
import com.github.sam0delkin.intellijpsa.model.psi.PsiElementModelChild
import com.github.sam0delkin.intellijpsa.model.psi.PsiElementPatternModel
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PsiElementModelHelperTest : BasePlatformTestCase() {
    fun testMatchesWithText() {
        val model = createModel("STRING_LITERAL", "'test'")
        val pattern = PsiElementPatternModel(withText = "'test'")

        assertTrue(PsiElementModelHelper.matches(model, pattern))
    }

    fun testMatchesWithTextNotMatching() {
        val model = createModel("STRING_LITERAL", "'test'")
        val pattern = PsiElementPatternModel(withText = "'other'")

        assertFalse(PsiElementModelHelper.matches(model, pattern))
    }

    fun testMatchesWithType() {
        val model = createModel("STRING_LITERAL", "'test'")
        val pattern = PsiElementPatternModel(withType = "STRING_LITERAL")

        assertTrue(PsiElementModelHelper.matches(model, pattern))
    }

    fun testMatchesWithTypeNotMatching() {
        val model = createModel("STRING_LITERAL", "'test'")
        val pattern = PsiElementPatternModel(withType = "METHOD_REFERENCE")

        assertFalse(PsiElementModelHelper.matches(model, pattern))
    }

    fun testMatchesWithTextAndType() {
        val model = createModel("STRING_LITERAL", "'test'")
        val pattern =
            PsiElementPatternModel(
                withText = "'test'",
                withType = "STRING_LITERAL",
            )

        assertTrue(PsiElementModelHelper.matches(model, pattern))
    }

    fun testMatchesWithParent() {
        val parentModel = createModel("AssignmentExpression", "\$var = 'test'")
        val childModel = createModel("STRING_LITERAL", "'test'", parentModel)
        val pattern =
            PsiElementPatternModel(
                withText = "'test'",
                parent = PsiElementPatternModel(withType = "AssignmentExpression"),
            )

        assertTrue(PsiElementModelHelper.matches(childModel, pattern))
    }

    fun testMatchesWithParentNotMatching() {
        val parentModel = createModel("Function", "function test()")
        val childModel = createModel("STRING_LITERAL", "'test'", parentModel)
        val pattern =
            PsiElementPatternModel(
                withText = "'test'",
                parent = PsiElementPatternModel(withType = "AssignmentExpression"),
            )

        assertFalse(PsiElementModelHelper.matches(childModel, pattern))
    }

    fun testMatchesWithAnyParent() {
        val grandParentModel = createModel("Function", "function test()")
        val parentModel = createModel("Statement", "statement", grandParentModel)
        val childModel = createModel("STRING_LITERAL", "'test'", parentModel)
        val pattern =
            PsiElementPatternModel(
                withText = "'test'",
                anyParent = PsiElementPatternModel(withType = "Function"),
            )

        assertTrue(PsiElementModelHelper.matches(childModel, pattern))
    }

    fun testMatchesWithAnyParentNotFound() {
        val grandParentModel = createModel("Class", "class MyClass")
        val parentModel = createModel("Method", "method", grandParentModel)
        val childModel = createModel("STRING_LITERAL", "'test'", parentModel)
        val pattern =
            PsiElementPatternModel(
                withText = "'test'",
                anyParent = PsiElementPatternModel(withType = "Function"),
            )

        assertFalse(PsiElementModelHelper.matches(childModel, pattern))
    }

    fun testMatchesWithPrev() {
        val prevModel = createModel("Operator", "=")
        val model = createModel("STRING_LITERAL", "'test'", prev = prevModel)
        val pattern =
            PsiElementPatternModel(
                withText = "'test'",
                prev = PsiElementPatternModel(withText = "="),
            )

        assertTrue(PsiElementModelHelper.matches(model, pattern))
    }

    fun testMatchesWithPrevNotMatching() {
        val prevModel = createModel("Operator", "+")
        val model = createModel("STRING_LITERAL", "'test'", prev = prevModel)
        val pattern =
            PsiElementPatternModel(
                withText = "'test'",
                prev = PsiElementPatternModel(withText = "="),
            )

        assertFalse(PsiElementModelHelper.matches(model, pattern))
    }

    fun testMatchesWithNext() {
        val nextModel = createModel("Semicolon", ";")
        val model = createModel("STRING_LITERAL", "'test'", next = nextModel)
        val pattern =
            PsiElementPatternModel(
                withText = "'test'",
                next = PsiElementPatternModel(withText = ";"),
            )

        assertTrue(PsiElementModelHelper.matches(model, pattern))
    }

    fun testMatchesWithNextNotMatching() {
        val nextModel = createModel("Comma", ",")
        val model = createModel("STRING_LITERAL", "'test'", next = nextModel)
        val pattern =
            PsiElementPatternModel(
                withText = "'test'",
                next = PsiElementPatternModel(withText = ";"),
            )

        assertFalse(PsiElementModelHelper.matches(model, pattern))
    }

    fun testMatchesWithOptions() {
        val options =
            mutableMapOf(
                "option1" to PsiElementModelChild(string = "value1"),
            )
        val model =
            PsiElementModel(
                id = "1",
                elementType = "STRING_LITERAL",
                options = options,
                elementName = null,
                elementFqn = null,
                elementSignature = null,
                text = "test",
                parent = null,
                prev = null,
                next = null,
                textRange = null,
            )
        val pattern =
            PsiElementPatternModel(
                withText = "test",
                withOptions = mapOf("options.option1.string" to "value1"),
            )

        assertTrue(PsiElementModelHelper.matches(model, pattern, checkOptions = true))
    }

    fun testMatchesWithOptionsNotMatching() {
        val options =
            mutableMapOf<String, PsiElementModelChild>(
                "option1" to PsiElementModelChild(string = "value1"),
            )
        val model =
            PsiElementModel(
                id = "1",
                elementType = "STRING_LITERAL",
                options = options,
                elementName = null,
                elementFqn = null,
                elementSignature = null,
                text = "'test'",
                parent = null,
                prev = null,
                next = null,
                textRange = null,
            )
        val pattern =
            PsiElementPatternModel(
                withText = "'test'",
                withOptions = mapOf("option1" to "value2"),
            )

        assertFalse(PsiElementModelHelper.matches(model, pattern, checkOptions = true))
    }

    fun testMatchesWithOptionsDisabled() {
        val options =
            mutableMapOf<String, PsiElementModelChild>(
                "option1" to PsiElementModelChild(string = "value1"),
            )
        val model =
            PsiElementModel(
                id = "1",
                elementType = "STRING_LITERAL",
                options = options,
                elementName = null,
                elementFqn = null,
                elementSignature = null,
                text = "'test'",
                parent = null,
                prev = null,
                next = null,
                textRange = null,
            )
        val pattern =
            PsiElementPatternModel(
                withText = "'test'",
                withOptions = mapOf("option1" to "value2"),
            )

        assertTrue(PsiElementModelHelper.matches(model, pattern, checkOptions = false))
    }

    fun testMatchesWithNullParent() {
        val model = createModel("STRING_LITERAL", "'test'")
        val pattern =
            PsiElementPatternModel(
                withText = "'test'",
                parent = PsiElementPatternModel(withType = "AssignmentExpression"),
            )

        assertFalse(PsiElementModelHelper.matches(model, pattern))
    }

    fun testMatchesWithNullPrev() {
        val model = createModel("STRING_LITERAL", "'test'")
        val pattern =
            PsiElementPatternModel(
                withText = "'test'",
                prev = PsiElementPatternModel(withText = "="),
            )

        assertFalse(PsiElementModelHelper.matches(model, pattern))
    }

    fun testMatchesWithNullNext() {
        val model = createModel("STRING_LITERAL", "'test'")
        val pattern =
            PsiElementPatternModel(
                withText = "'test'",
                next = PsiElementPatternModel(withText = ";"),
            )

        assertFalse(PsiElementModelHelper.matches(model, pattern))
    }

    fun testToPattern() {
        val parentModel = createModel("AssignmentExpression", "\$var = 'test'")
        val childModel = createModel("STRING_LITERAL", "'test'", parentModel)

        val pattern = PsiElementModelHelper.toPattern(childModel)

        assertNotNull(pattern)
        assertEquals("STRING_LITERAL", pattern.withType)
        assertEquals("'test'", pattern.withText)
        assertNotNull(pattern.parent)
        assertEquals("AssignmentExpression", pattern.parent?.withType)
        assertNull(pattern.anyParent)
        assertNull(pattern.prev)
        assertNull(pattern.next)
    }

    fun testToPatternWithHierarchy() {
        val grandParentModel = createModel("Function", "function test()")
        val parentModel = createModel("Statement", "statement", grandParentModel)
        val childModel = createModel("STRING_LITERAL", "'test'", parentModel)

        val pattern = PsiElementModelHelper.toPattern(childModel)

        assertNotNull(pattern)
        assertNotNull(pattern.parent)
        assertNotNull(pattern.parent?.parent)
        assertEquals("Statement", pattern.parent?.withType)
        assertEquals("Function", pattern.parent?.parent?.withType)
    }

    private fun createModel(
        elementType: String,
        text: String,
        parent: PsiElementModel? = null,
        prev: PsiElementModel? = null,
        next: PsiElementModel? = null,
    ): PsiElementModel =
        PsiElementModel(
            id = "test_hash",
            elementType = elementType,
            options = mutableMapOf(),
            elementName = null,
            elementFqn = null,
            elementSignature = null,
            text = text,
            parent = parent,
            prev = prev,
            next = next,
            textRange = null,
        )
}
