package com.github.sam0delkin.intellijpsa.model.psi

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PsiElementModelsTest : BasePlatformTestCase() {
    fun testPsiElementModelChildWithString() {
        val child = PsiElementModelChild(string = "test_value")

        assertEquals("test_value", child.string)
        assertNull(child.model)
        assertNull(child.array)
    }

    fun testPsiElementModelChildWithModel() {
        val model =
            PsiElementModel(
                id = "123",
                elementType = "STRING_LITERAL",
                options = mutableMapOf(),
                elementName = null,
                elementFqn = null,
                elementSignature = null,
                text = "'test'",
                parent = null,
                prev = null,
                next = null,
                textRange = null,
            )
        val child = PsiElementModelChild(model = model)

        assertNotNull(child.model)
        assertEquals("123", child.model?.id)
        assertEquals("STRING_LITERAL", child.model?.elementType)
        assertNull(child.string)
    }

    fun testPsiElementModelChildWithArray() {
        val array = arrayOfNulls<PsiElementModel>(2)
        array[0] =
            PsiElementModel(
                id = "1",
                elementType = "TYPE_1",
                options = mutableMapOf(),
                elementName = null,
                elementFqn = null,
                elementSignature = null,
                text = "text1",
                parent = null,
                prev = null,
                next = null,
                textRange = null,
            )
        array[1] =
            PsiElementModel(
                id = "2",
                elementType = "TYPE_2",
                options = mutableMapOf(),
                elementName = null,
                elementFqn = null,
                elementSignature = null,
                text = "text2",
                parent = null,
                prev = null,
                next = null,
                textRange = null,
            )
        val child = PsiElementModelChild(array = array)

        assertNotNull(child.array)
        assertEquals(2, child.array?.size)
        assertEquals("1", child.array?.get(0)?.id)
        assertEquals("2", child.array?.get(1)?.id)
    }

    fun testPsiElementModelChildEquals() {
        val child1 = PsiElementModelChild(string = "test")
        val child2 = PsiElementModelChild(string = "test")
        val child3 = PsiElementModelChild(string = "other")

        assertEquals(child1, child2)
        assertEquals(child1.hashCode(), child2.hashCode())
        assertTrue(child1 != child3)
    }

    fun testPsiElementModelTextRange() {
        val textRange = PsiElementModelTextRange(startOffset = 10, endOffset = 20)

        assertEquals(10, textRange.startOffset)
        assertEquals(20, textRange.endOffset)
    }

    fun testPsiElementModel() {
        val options =
            mutableMapOf<String, PsiElementModelChild>(
                "option1" to PsiElementModelChild(string = "value1"),
                "option2" to PsiElementModelChild(string = "value2"),
            )

        val textRange = PsiElementModelTextRange(startOffset = 0, endOffset = 10)

        val model =
            PsiElementModel(
                id = "hash123",
                elementType = "STRING_LITERAL",
                options = options,
                elementName = null,
                elementFqn = null,
                elementSignature = arrayListOf("signature1", "signature2"),
                text = "'test string'",
                parent = null,
                prev = null,
                next = null,
                textRange = textRange,
                filePath = "/path/to/file.php",
            )

        assertEquals("hash123", model.id)
        assertEquals("STRING_LITERAL", model.elementType)
        assertEquals(2, model.options.size)
        assertEquals("value1", model.options["option1"]?.string)
        assertEquals(2, model.elementSignature?.size)
        assertEquals("'test string'", model.text)
        assertEquals(0, model.textRange?.startOffset)
        assertEquals(10, model.textRange?.endOffset)
        assertEquals("/path/to/file.php", model.filePath)
    }

    fun testPsiElementModelWithParent() {
        val parentModel =
            PsiElementModel(
                id = "parent_hash",
                elementType = "AssignmentExpression",
                options = mutableMapOf(),
                elementName = null,
                elementFqn = null,
                elementSignature = null,
                text = "\$var = 'value'",
                parent = null,
                prev = null,
                next = null,
                textRange = null,
            )

        val childModel =
            PsiElementModel(
                id = "child_hash",
                elementType = "STRING_LITERAL",
                options = mutableMapOf(),
                elementName = null,
                elementFqn = null,
                elementSignature = null,
                text = "'value'",
                parent = parentModel,
                prev = null,
                next = null,
                textRange = null,
            )

        assertNotNull(childModel.parent)
        assertEquals("parent_hash", childModel.parent?.id)
        assertEquals("AssignmentExpression", childModel.parent?.elementType)
    }

    fun testPsiElementModelEmptyOptions() {
        val model =
            PsiElementModel(
                id = "hash",
                elementType = "TYPE",
                options = mutableMapOf(),
                elementName = null,
                elementFqn = null,
                elementSignature = null,
                text = "text",
                parent = null,
                prev = null,
                next = null,
                textRange = null,
            )

        assertEquals(0, model.options.size)
    }
}
