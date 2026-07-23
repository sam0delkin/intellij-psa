package com.github.sam0delkin.intellijpsa.model.completion

import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ExtendedCompletionModelTest : BasePlatformTestCase() {
    fun testCreateCopiesFieldsFromCompletionModel() {
        val completionModel =
            CompletionModel().apply {
                text = "MyCompletion"
                presentableText = "Presentable"
                tailText = "Tail"
                type = "MyType"
                priority = 42.0
                bold = true
            }

        val extended = ExtendedCompletionModel.create(completionModel, project)

        assertEquals("MyCompletion", extended.text)
        assertEquals("Presentable", extended.presentableText)
        assertEquals("Tail", extended.tailText)
        assertEquals("MyType", extended.type)
        assertEquals(42.0, extended.priority)
        assertEquals(true, extended.bold)
    }

    fun testCreateResolvesReferenceFromLink() {
        myFixture.addFileToProject("target.php", "<?php\nclass Target {}\n")
        val completionModel =
            CompletionModel().apply {
                text = "target"
                link = "/target.php:1:1"
            }

        val extended = ExtendedCompletionModel.create(completionModel, project)

        assertNotNull(extended.reference)
    }

    fun testCreateWithUnresolvableLinkLeavesReferenceNull() {
        val completionModel = CompletionModel().apply { text = "orphan" }

        val extended = ExtendedCompletionModel.create(completionModel, project)

        assertNull(extended.reference)
    }

    fun testToGoToElementUsesReferenceWhenPresent() {
        myFixture.addFileToProject("target.php", "<?php\nclass Target {}\n")
        val completionModel =
            CompletionModel().apply {
                text = "target"
                link = "/target.php:1:1"
            }
        val extended = ExtendedCompletionModel.create(completionModel, project)
        assertNotNull(extended.reference)

        val resolved = extended.toGoToElement(project)

        assertNotNull(resolved)
    }

    fun testToGoToElementFallsBackToLinkWhenNoReference() {
        myFixture.addFileToProject("target.php", "<?php\nclass Target {}\n")
        val extended =
            ExtendedCompletionModel().apply {
                text = "target"
                link = "/target.php:1:1"
            }

        val resolved = extended.toGoToElement(project)

        assertNotNull(resolved)
    }

    fun testToGoToElementReturnsNullForUnresolvableLink() {
        val extended = ExtendedCompletionModel().apply { text = "orphan" }

        assertNull(extended.toGoToElement(project))
    }

    fun testToCompletionLookupElementBoldSetsDefaultPriority() {
        val extended =
            ExtendedCompletionModel().apply {
                text = "Foo"
                bold = true
            }

        val lookupElement = extended.toCompletionLookupElement()

        assertEquals(100.0, (lookupElement as PrioritizedLookupElement<*>).priority, 0.0)
    }

    fun testToCompletionLookupElementExplicitPriorityOverridesBoldDefault() {
        val extended =
            ExtendedCompletionModel().apply {
                text = "Foo"
                bold = true
                priority = 50.0
            }

        val lookupElement = extended.toCompletionLookupElement()

        assertEquals(50.0, (lookupElement as PrioritizedLookupElement<*>).priority, 0.0)
    }

    fun testToCompletionLookupElementDefaultPriorityIsZero() {
        val extended = ExtendedCompletionModel().apply { text = "Foo" }

        val lookupElement = extended.toCompletionLookupElement()

        assertEquals(0.0, (lookupElement as PrioritizedLookupElement<*>).priority, 0.0)
    }

    fun testToCompletionLookupElementRendersPresentation() {
        val extended =
            ExtendedCompletionModel().apply {
                text = "Foo"
                presentableText = "Presentable Foo"
                tailText = " tail"
                type = "MyType"
                bold = true
            }

        val lookupElement = extended.toCompletionLookupElement()
        val presentation = LookupElementPresentation()
        lookupElement.renderElement(presentation)

        assertEquals("Presentable Foo", presentation.itemText)
        assertEquals(" tail", presentation.tailText)
        assertEquals("MyType", presentation.typeText)
        assertTrue(presentation.isItemTextBold)
        assertNotNull(presentation.icon)
    }

    fun testToCompletionLookupElementUsesTextWhenNoPresentableText() {
        val extended = ExtendedCompletionModel().apply { text = "Foo" }

        val lookupElement = extended.toCompletionLookupElement()

        assertEquals("Foo", lookupElement.lookupString)
    }
}
