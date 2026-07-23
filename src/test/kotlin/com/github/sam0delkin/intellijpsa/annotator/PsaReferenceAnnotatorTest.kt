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
 * The `fileData.values.map { ... }` traversal (lines ~49-85, the "processed" branch) resolves each
 * recorded reference-occurrence location via `PsiUtils.processLink("file://$result", ...,
 * appendProjectDir = false)`, which reconstructs an absolute-filesystem "file://" URL from the
 * light-fixture's "temp://" virtual path (e.g. "file:///src/test.php") - there is no real file on
 * disk at that path, so `VirtualFileManager.findFileByUrl` always returns null and `processed` can
 * never become true under `BasePlatformTestCase`. This is the same VFS protocol mismatch documented
 * in `PsaLineMarkerProviderTest.kt` for the equivalent lookup in `PsaLineMarkerProvider.kt`. The
 * `!processed` fallback branch below it, however, resolves completion links via the DEFAULT
 * `appendProjectDir = true` path (through `guessProjectDir()`'s "temp://" URL), which works fine in
 * this harness and is what these tests exercise.
 *
 * A second, code-level (not environmental) dead zone lives inside that same `!processed` fallback:
 * the "matched but no completion title" branches (lines ~108-111 and ~121-124 - the `else` arm of
 * each `if (null != completionTitle)` check) can never be reached without first crashing.
 * `AnyCompletionContributor.GotoDeclaration.getGotoDeclarationTargets` only ever calls
 * `sourceElement.putUserData(ANNOTATOR_COMPLETION_TITLE, config.title)` from inside the exact same
 * static-completion-pattern-match branch that is the *only* way (for the `offset < 0` calls this
 * annotator makes) its result can end up non-empty - and moments later, unconditionally, does
 * `it.completionName = config.title!!` over that same (by-then-known-non-empty) completions list.
 * So whenever `targets`/`completions` below is genuinely non-empty, `config.title` must already have
 * been non-null (else that `!!` would have thrown a `KotlinNullPointerException` out of
 * `getGotoDeclarationTargets` first) - meaning `element.getUserData(ANNOTATOR_COMPLETION_TITLE)` can
 * never observe null at that point. This is a real (if practically harmless, since every static
 * completion in this codebase's own usage sets a non-null `title`) latent `!!`-assertion coupling,
 * not something a fixture could route around - reported rather than worked around per this session's
 * "don't fix, report" convention for incidental findings.
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

    private fun staticCompletion(completionText: String): StaticCompletionModel =
        StaticCompletionModel().apply {
            name = "my_static"
            title = "My Static"
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
