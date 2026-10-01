package com.github.sam0delkin.intellijpsa.annotator

import com.github.sam0delkin.intellijpsa.model.StaticCompletionModel
import com.github.sam0delkin.intellijpsa.model.completion.CompletionModel
import com.github.sam0delkin.intellijpsa.model.completion.CompletionsModel
import com.github.sam0delkin.intellijpsa.model.psi.PsiElementPatternModel
import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * The `fileData.values.map { ... }` traversal (the "processed" branch) resolves recorded usage
 * locations as real `file://` URLs, which this light fixture's `temp://` VFS can't provide - it is
 * covered by `PsaReferenceAnnotatorStaticReferenceTest` (real on-disk project) instead. These tests
 * exercise the `!processed` fallback branch.

 */
class PsaReferenceAnnotatorTest : BasePlatformTestCase() {
    private fun enableAnnotator() {
        project.service<PsaManager>().getSettings().apply {
            pluginEnabled = true
            resolveReferences = true
            supportsStaticCompletions = true
            annotateUndefinedElements = true
            goToFilter = ""
            supportedLanguages = "PHP"
        }
    }

    private fun staticCompletion(
        completionText: String,
        title: String? = "My Static",
    ): StaticCompletionModel =
        StaticCompletionModel().apply {
            name = "my_static"
            this.title = title
            patterns = arrayListOf(PsiElementPatternModel(withType = "String"))
            completions =
                CompletionsModel().apply {
                    completions =
                        arrayListOf(
                            CompletionModel().apply {
                                text = completionText
                                link = "/test.php:1:1"
                            },
                        )
                }
        }

    fun testAnnotatesExactMatchAsPsaReferenceWithCompletionTitle() {
        enableAnnotator()
        project.service<PsaManager>().setStaticCompletionConfigs(arrayListOf(staticCompletion("'target'")))
        myFixture.configureByText("test.php", "<?php\n'target';")

        val annotations = myFixture.doHighlighting(HighlightSeverity.INFORMATION)

        val annotation = annotations.find { it.description == "PSA Reference \"My Static\"" }
        assertNotNull(
            "Expected a PSA Reference annotation. Found: ${annotations.joinToString { "${it.severity}: ${it.description}" }}",
            annotation,
        )
        assertEquals(HighlightSeverity.INFORMATION, annotation!!.severity)
    }

    fun testAnnotatesNonMatchingElementWithDefinedCompletionsAsUndefinedWithTitle() {
        enableAnnotator()
        project.service<PsaManager>().setStaticCompletionConfigs(arrayListOf(staticCompletion("'something_else'")))
        myFixture.configureByText("test.php", "<?php\n'target';")

        val annotations = myFixture.doHighlighting(HighlightSeverity.INFORMATION)

        val annotation =
            annotations.find { it.description == "Undefined PSA Reference for completion: \"My Static\"" }
        assertNotNull(
            "Expected an Undefined PSA Reference annotation. Found: ${
                annotations.joinToString { "${it.severity}: ${it.description}" }
            }",
            annotation,
        )
        assertEquals(HighlightSeverity.WEAK_WARNING, annotation!!.severity)
    }

    // `title` is optional in the static completion schema. Regression test: an untitled config used
    // to crash GoTo (and so this annotator) with an NPE as soon as an entry matched.
    fun testAnnotatesExactMatchAsPsaReferenceWithoutCompletionTitle() {
        enableAnnotator()
        project.service<PsaManager>().setStaticCompletionConfigs(arrayListOf(staticCompletion("'target'", title = null)))
        myFixture.configureByText("test.php", "<?php\n'target';")

        val annotations = myFixture.doHighlighting(HighlightSeverity.INFORMATION)

        val annotation = annotations.single { it.description?.contains("PSA Reference") == true }
        assertEquals("PSA Reference", annotation.description)
        assertEquals(HighlightSeverity.INFORMATION, annotation.severity)
    }

    fun testAnnotatesNonMatchingElementAsUndefinedWithoutCompletionTitle() {
        enableAnnotator()
        project.service<PsaManager>().setStaticCompletionConfigs(
            arrayListOf(staticCompletion("'something_else'", title = null)),
        )
        myFixture.configureByText("test.php", "<?php\n'target';")

        val annotations = myFixture.doHighlighting(HighlightSeverity.INFORMATION)

        val annotation = annotations.single { it.description?.contains("PSA Reference") == true }
        assertEquals("Undefined PSA Reference", annotation.description)
        assertEquals(HighlightSeverity.WEAK_WARNING, annotation.severity)
    }

    fun testNoAnnotationWhenElementMatchesNoStaticCompletionPattern() {
        enableAnnotator()
        project.service<PsaManager>().setStaticCompletionConfigs(
            arrayListOf(
                StaticCompletionModel().apply {
                    name = "my_static"
                    title = "My Static"
                    patterns = arrayListOf(PsiElementPatternModel(withType = "SomeOtherType"))
                    completions = CompletionsModel().apply { completions = arrayListOf() }
                },
            ),
        )
        myFixture.configureByText("test.php", "<?php\n'target';")

        val annotations = myFixture.doHighlighting(HighlightSeverity.INFORMATION)

        assertTrue(annotations.none { it.description?.contains("PSA Reference") == true })
    }

    fun testNoAnnotationWhenPluginDisabled() {
        enableAnnotator()
        project.service<PsaManager>().getSettings().pluginEnabled = false
        project.service<PsaManager>().setStaticCompletionConfigs(arrayListOf(staticCompletion("'target'")))
        myFixture.configureByText("test.php", "<?php\n'target';")

        val annotations = myFixture.doHighlighting(HighlightSeverity.INFORMATION)

        assertTrue(annotations.none { it.description?.contains("PSA Reference") == true })
    }

    fun testNoAnnotationWhenResolveReferencesDisabled() {
        enableAnnotator()
        project.service<PsaManager>().getSettings().resolveReferences = false
        project.service<PsaManager>().setStaticCompletionConfigs(arrayListOf(staticCompletion("'target'")))
        myFixture.configureByText("test.php", "<?php\n'target';")

        val annotations = myFixture.doHighlighting(HighlightSeverity.INFORMATION)

        assertTrue(annotations.none { it.description?.contains("PSA Reference") == true })
    }

    fun testNoAnnotationWhenSupportsStaticCompletionsDisabled() {
        enableAnnotator()
        project.service<PsaManager>().getSettings().supportsStaticCompletions = false
        project.service<PsaManager>().setStaticCompletionConfigs(arrayListOf(staticCompletion("'target'")))
        myFixture.configureByText("test.php", "<?php\n'target';")

        val annotations = myFixture.doHighlighting(HighlightSeverity.INFORMATION)

        assertTrue(annotations.none { it.description?.contains("PSA Reference") == true })
    }

    fun testNoAnnotationWhenAnnotateUndefinedElementsDisabled() {
        enableAnnotator()
        project.service<PsaManager>().getSettings().annotateUndefinedElements = false
        project.service<PsaManager>().setStaticCompletionConfigs(arrayListOf(staticCompletion("'target'")))
        myFixture.configureByText("test.php", "<?php\n'target';")

        val annotations = myFixture.doHighlighting(HighlightSeverity.INFORMATION)

        assertTrue(annotations.none { it.description?.contains("PSA Reference") == true })
    }

    fun testNoAnnotationWhenNoStaticCompletionConfigs() {
        enableAnnotator()
        project.service<PsaManager>().setStaticCompletionConfigs(arrayListOf())
        myFixture.configureByText("test.php", "<?php\n'target';")

        val annotations = myFixture.doHighlighting(HighlightSeverity.INFORMATION)

        assertTrue(annotations.none { it.description?.contains("PSA Reference") == true })
    }

    // `!completions.isNullOrEmpty()` (line ~113) has two distinct "false"-producing states for a
    // nullable array: `completions == null` (already covered above, when no pattern matches at all -
    // `getGotoDeclarationTargets` returns null outright) and `completions` non-null but genuinely
    // EMPTY (size 0) - a pattern matches, so `getGotoDeclarationTargets` returns a real (non-null)
    // array, but the matched static completion itself has zero completion items, so that array ends
    // up empty rather than null. Only the latter was previously unexercised.
    fun testNoAnnotationWhenMatchedStaticCompletionHasNoCompletionItems() {
        enableAnnotator()
        project.service<PsaManager>().setStaticCompletionConfigs(
            arrayListOf(
                StaticCompletionModel().apply {
                    name = "my_static"
                    title = "My Static"
                    patterns = arrayListOf(PsiElementPatternModel(withType = "String"))
                    completions = CompletionsModel().apply { completions = arrayListOf() }
                },
            ),
        )
        myFixture.configureByText("test.php", "<?php\n'target';")

        val annotations = myFixture.doHighlighting(HighlightSeverity.INFORMATION)

        assertTrue(annotations.none { it.description?.contains("PSA Reference") == true })
    }

    fun testNoAnnotationWhenElementTypeNotInGoToFilter() {
        enableAnnotator()
        project.service<PsaManager>().getSettings().goToFilter = "SomeOtherType"
        project.service<PsaManager>().setStaticCompletionConfigs(arrayListOf(staticCompletion("'target'")))
        myFixture.configureByText("test.php", "<?php\n'target';")

        val annotations = myFixture.doHighlighting(HighlightSeverity.INFORMATION)

        assertTrue(annotations.none { it.description?.contains("PSA Reference") == true })
    }
}
