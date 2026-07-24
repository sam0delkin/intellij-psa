package com.github.sam0delkin.intellijpsa.index

import com.github.sam0delkin.intellijpsa.model.StaticCompletionModel
import com.github.sam0delkin.intellijpsa.model.completion.CompletionModel
import com.github.sam0delkin.intellijpsa.model.completion.CompletionsModel
import com.github.sam0delkin.intellijpsa.model.psi.PsiElementPatternModel
import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.indexing.FileBasedIndex

/**
 * Coverage for [PsaStaticReferenceIndex], the `FileBasedIndexExtension` that pre-indexes
 * static-completion usage locations (see `PsaLineMarkerProviderTest.indexMatchingStaticCompletion()`
 * for the proven pattern of driving this exact index from a test: configure settings + a
 * static-completion pattern, add/configure the source files, then
 * `FileBasedIndex.getInstance().ensureUpToDate(INDEX_ID, project, GlobalSearchScope.allScope(project))`
 * to force synchronous indexing).
 *
 * Four residual gaps, deliberately not chased further here:
 *
 * 1. `inputData.project.guessProjectDir() ?: return fileResult` (~line 62) never observes the null
 *    branch: exactly like `PsaReferenceContributor`'s equivalent `project.guessProjectDir()` null
 *    check, a `BasePlatformTestCase` light-fixture project always has a guessable project dir once
 *    any file exists in it (that's what lets every other `PsiUtils.processLink` call in this suite
 *    resolve against the light fixture's own "temp://" root), so there is no reachable fixture state
 *    that makes it null without fabricating a synthetic `Project` - not attempted here.
 *
 * 2. The `useVelocityInIndex` fallback body (~lines 119-141, calling `Velocity.evaluate(...)`) is
 *    deliberately never *entered* by any test below - only its three guard sub-conditions
 *    (`filtered.isEmpty()`, `useVelocityInIndex`, `matcher != null`) are exercised, each forced to
 *    make the overall `&&` false via a different one of the three so the block itself never runs.
 *    This mirrors `PsiElementModelHelperTest`'s documented finding (see that file's class doc and
 *    `testMatchesAstNodeWithMatcherThrowsDueToCoverageAgentVelocityConflict`): Apache Velocity cannot
 *    initialize AT ALL in this project's coverage-instrumented test JVM (the intellij-coverage-agent's
 *    injected `__$branchHits$__` field breaks `DeprecatedRuntimeConstants`'s reflection-based static
 *    init), and `PsaStaticReferenceIndex`'s own `catch (_: Exception)` around `Velocity.evaluate(...)`
 *    is - like the code `PsiElementModelHelperTest` documents - too narrow to catch the resulting
 *    `Error`. Deliberately entering that block here would risk crashing `koverXmlReport`'s
 *    instrumented run rather than just leaving a line "missed", so it is left as the same kind of
 *    documented residual gap instead.
 *
 * 3. `if (staticCompletion.completions == null) continue` (~line 96-97) is dead code, not merely
 *    hard to reach - see the note directly above `testMapSkipsWhenNoPatternMatchesElement` below for
 *    the full explanation (a config with null `completions` crashes far upstream, in
 *    `ExtendedStaticCompletionModel.createFromModel`, before this guard could ever run).
 *
 * 4. `if (!settings.isLanguageSupported(languageString)) return fileResult` (~line 58-59) is also not
 *    covered - see the note directly above `testMapReturnsEmptyWhenFileOutsideConfiguredIndexFolder`
 *    below for what was tried and why it was abandoned (no coverage gain, and a real risk of
 *    destabilizing the shared `FileBasedIndex` state for unrelated later tests).
 */
class PsaStaticReferenceIndexTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        // Settings and PsaManager's static-completion cache are project-level light services that
        // persist across test methods (and across test classes sharing the same light-fixture
        // project) - reset every field this class touches or assumes a default for, so state from
        // another test can't leak in regardless of execution order.
        project.service<Settings>().apply {
            pluginEnabled = false
            resolveReferences = false
            goToFilter = ""
            targetElementTypes = null
            supportsStaticCompletions = false
            supportedLanguages = ""
            indexFolder = ""
            useVelocityInIndex = false
        }
        project.service<PsaManager>().setStaticCompletionConfigs(arrayListOf())
    }

    override fun tearDown() {
        try {
            // Settings and PsaManager's static-completion cache are project-level light services
            // that persist past this class's own test methods, into whichever test class runs next
            // in the same light-fixture project - reset everything back to safe, inert defaults so
            // a later, unrelated test class's own indexing (which may not itself reset every one of
            // these fields) never runs against a leftover enabled-plugin/matching-pattern
            // combination from this class.
            project.service<Settings>().apply {
                pluginEnabled = false
                resolveReferences = false
                goToFilter = ""
                targetElementTypes = null
                supportsStaticCompletions = false
                supportedLanguages = ""
                indexFolder = ""
                useVelocityInIndex = false
            }
            project.service<PsaManager>().setStaticCompletionConfigs(arrayListOf())
        } finally {
            super.tearDown()
        }
    }

    // Settings enabling the indexer's happy-path guard clauses: the visitor only looks at "single
    // quoted string" tokens (matching a PHP string literal's inner text token, the same raw
    // LighterAST token type `PsaLineMarkerProviderTest` uses for the same reason - distinct from the
    // composite PSI "String" element type used elsewhere in this plugin).
    private fun baseSettings(): Settings {
        val settings = project.service<PsaManager>().getSettings()
        settings.apply {
            pluginEnabled = true
            resolveReferences = true
            goToFilter = "single quoted string"
            supportedLanguages = "PHP"
            indexFolder = ""
            useVelocityInIndex = false
            targetElementTypes = null
        }

        return settings
    }

    private fun matchingStaticCompletion(): StaticCompletionModel =
        StaticCompletionModel().apply {
            name = "my_static"
            title = "My Static"
            patterns = arrayListOf(PsiElementPatternModel(withType = "single quoted string"))
            completions =
                CompletionsModel().apply {
                    completions =
                        arrayListOf(
                            CompletionModel().apply {
                                text = "target"
                                link = "/target.php:1:1"
                            },
                        )
                }
        }

    @Suppress("UnstableApiUsage")
    private fun indexAndGetFileData(fileName: String): Map<String, Map<String, List<String>>> {
        val file = myFixture.configureByText(fileName, "<?php\n'target';").virtualFile
        FileBasedIndex.getInstance().ensureUpToDate(INDEX_ID, project, GlobalSearchScope.allScope(project))

        return FileBasedIndex.getInstance().getFileData(INDEX_ID, file, project)
    }

    fun testMapReturnsRealDataWhenEverythingMatches() {
        baseSettings()
        project.service<PsaManager>().setStaticCompletionConfigs(arrayListOf(matchingStaticCompletion()))
        myFixture.addFileToProject("target.php", "<?php\nclass Target {}\n")

        val fileData = indexAndGetFileData("happy_path.php")

        assertTrue("expected a real indexed entry for the matching usage", fileData.isNotEmpty())
    }

    // NOTE: the indexer's own internal "is this language supported" guard (~line 58-59) is NOT
    // covered here, deliberately - and not merely because it's hard to reach. `getInputFilter()`'s
    // *own*, near-identical language check normally excludes an unsupported-language file from the
    // index outright, so `map()` never runs for it. An earlier version of this test tried to work
    // around that by indexing a file once while the language WAS supported (admitting it into the
    // index's tracked file set), then flipping the language to unsupported and forcing a targeted
    // per-file reindex via `FileBasedIndex.getInstance().requestReindex(file)` (the same API
    // `PsaFileChangeListener`/`PsaLineMarkerProvider` themselves call in production) before calling
    // `ensureUpToDate` again. That neither reached line 59 (confirmed via an isolated Kover run: the
    // line stayed fully missed even with this test present) NOR was safe to keep: it left the shared,
    // JVM-wide `FileBasedIndex` state for this index fragile enough that, on more than one full-suite
    // run, some entirely unrelated later test (different test each time - `PsaElementDocumentationProviderTest`
    // once, `PsaElementTest` another time) failed during its own `setUp()` with `ServiceNotReadyException:
    // index ... has status REQUIRES_REBUILD`. Since it bought no real coverage while risking exactly
    // the kind of "real regression" this task's own conventions warn against, it was removed rather
    // than chased further.

    fun testMapReturnsEmptyWhenFileOutsideConfiguredIndexFolder() {
        val settings = baseSettings()
        settings.indexFolder = "only_this_dir"
        project.service<PsaManager>().setStaticCompletionConfigs(arrayListOf(matchingStaticCompletion()))
        myFixture.addFileToProject("target.php", "<?php\nclass Target {}\n")

        val fileData = indexAndGetFileData("outside_index_folder.php")

        assertTrue(fileData.isEmpty())
    }

    fun testMapSkipsStaticCompletionWithNullPatterns() {
        baseSettings()
        project.service<PsaManager>().setStaticCompletionConfigs(
            arrayListOf(
                StaticCompletionModel().apply {
                    name = "no_patterns"
                    title = "No Patterns"
                    patterns = null
                    completions =
                        CompletionsModel().apply {
                            completions =
                                arrayListOf(
                                    CompletionModel().apply {
                                        text = "target"
                                        link = "/target.php:1:1"
                                    },
                                )
                        }
                },
            ),
        )
        myFixture.addFileToProject("target.php", "<?php\nclass Target {}\n")

        val fileData = indexAndGetFileData("null_patterns.php")

        assertTrue(fileData.isEmpty())
    }

    // NOTE: `staticCompletion.completions == null` (~line 96-97) is NOT covered here, deliberately.
    // Confirmed experimentally (an earlier version of this test set `completions = null` on a config
    // and passed it through `PsaManager.setStaticCompletionConfigs`/`getStaticCompletionConfigs`):
    // `ExtendedStaticCompletionModel.createFromModel` (`ExtendedInfoModel.kt` line ~28) unconditionally
    // does `model.completions!!`, so a config with a null `completions` field throws an NPE the
    // moment `getStaticCompletionConfigs()` is called - which happens at the very top of this
    // indexer's own `map()` (line ~45), before the per-token visitor (and this guard) ever runs. That
    // NPE doesn't just fail the one test either: it corrupts `FileBasedIndex`'s shared, JVM-wide build
    // state (observed as the index getting stuck in a `REQUIRES_REBUILD` status that broke an
    // unrelated, later-running test in this same class with a `ServiceNotReadyException`), so it is
    // not safe to provoke even in isolation. This guard is therefore dead code reachable only via
    // that upstream NPE - a real bug (reported, not fixed, per this session's convention), not a gap
    // a fixture could close.

    fun testMapSkipsWhenNoPatternMatchesElement() {
        baseSettings()
        project.service<PsaManager>().setStaticCompletionConfigs(
            arrayListOf(
                StaticCompletionModel().apply {
                    name = "wrong_type"
                    title = "Wrong Type"
                    patterns = arrayListOf(PsiElementPatternModel(withType = "some other token type"))
                    completions =
                        CompletionsModel().apply {
                            completions =
                                arrayListOf(
                                    CompletionModel().apply {
                                        text = "target"
                                        link = "/target.php:1:1"
                                    },
                                )
                        }
                },
            ),
        )
        myFixture.addFileToProject("target.php", "<?php\nclass Target {}\n")

        val fileData = indexAndGetFileData("no_pattern_match.php")

        assertTrue(fileData.isEmpty())
    }

    fun testMapSkipsWhenFilteredCompletionsStayEmptyWithoutVelocity() {
        baseSettings()
        project.service<PsaManager>().setStaticCompletionConfigs(
            arrayListOf(
                StaticCompletionModel().apply {
                    name = "no_text_match"
                    title = "No Text Match"
                    patterns = arrayListOf(PsiElementPatternModel(withType = "single quoted string"))
                    completions =
                        CompletionsModel().apply {
                            completions =
                                arrayListOf(
                                    CompletionModel().apply {
                                        text = "not_target"
                                        link = "/target.php:1:1"
                                    },
                                )
                        }
                },
            ),
        )
        myFixture.addFileToProject("target.php", "<?php\nclass Target {}\n")

        val fileData = indexAndGetFileData("no_direct_match.php")

        assertTrue(fileData.isEmpty())
    }

    // Forces the `filtered.isEmpty() && settings.useVelocityInIndex && staticCompletion.matcher !=
    // null` condition false via its THIRD operand (matcher == null) specifically, so the Velocity
    // fallback body itself is never entered (see this file's class doc for why) while still
    // confirming useVelocityInIndex=true alone, without a matcher, does not change the outcome.
    fun testMapSkipsVelocityFallbackWhenMatcherIsNull() {
        val settings = baseSettings()
        settings.useVelocityInIndex = true
        project.service<PsaManager>().setStaticCompletionConfigs(
            arrayListOf(
                StaticCompletionModel().apply {
                    name = "no_matcher"
                    title = "No Matcher"
                    matcher = null
                    patterns = arrayListOf(PsiElementPatternModel(withType = "single quoted string"))
                    completions =
                        CompletionsModel().apply {
                            completions =
                                arrayListOf(
                                    CompletionModel().apply {
                                        text = "not_target"
                                        link = "/target.php:1:1"
                                    },
                                )
                        }
                },
            ),
        )
        myFixture.addFileToProject("target.php", "<?php\nclass Target {}\n")

        val fileData = indexAndGetFileData("velocity_no_matcher.php")

        assertTrue(fileData.isEmpty())
    }

    // Forces the same condition false via its FIRST operand (filtered already non-empty from a
    // direct text match) while useVelocityInIndex=true - proving the direct-match short-circuit
    // still wins and the Velocity block is skipped even when it's enabled. Also exercises:
    //  - line ~156's "targetElementTypes was null -> initialize it" branch (every other test in this
    //    file, and the shared `indexMatchingStaticCompletion()` helper in `PsaLineMarkerProviderTest`,
    //    leaves it non-null beforehand).
    //  - the `filtered.filter { null != it.link }` sub-branch (~line 149) for BOTH outcomes: one
    //    matching completion has no link (excluded) and one does (included).
    fun testMapUsesDirectTextMatchAndTracksNewTargetElementType() {
        val settings = baseSettings()
        settings.useVelocityInIndex = true
        settings.targetElementTypes = null
        project.service<PsaManager>().setStaticCompletionConfigs(
            arrayListOf(
                StaticCompletionModel().apply {
                    name = "direct_match"
                    title = "Direct Match"
                    patterns = arrayListOf(PsiElementPatternModel(withType = "single quoted string"))
                    completions =
                        CompletionsModel().apply {
                            completions =
                                arrayListOf(
                                    CompletionModel().apply {
                                        text = "target"
                                        link = null
                                    },
                                    CompletionModel().apply {
                                        text = "target"
                                        link = "/target.php:1:1"
                                    },
                                )
                        }
                },
            ),
        )
        myFixture.addFileToProject("target.php", "<?php\nclass Target {}\n")

        val fileData = indexAndGetFileData("direct_match_tracks_type.php")

        assertTrue("expected the linked completion to produce a real index entry", fileData.isNotEmpty())
        assertTrue(
            "expected the indexer to have recorded the resolved target element's type",
            settings.targetElementTypes?.isNotEmpty() == true,
        )
    }

    fun testAcceptInputFalseWhenPluginDisabled() {
        project.service<Settings>().pluginEnabled = false
        val file = myFixture.configureByText("accept_input_disabled.php", "<?php\n'target';").virtualFile

        assertFalse(PsaStaticReferenceIndex().inputFilter.acceptInput(file))
    }

    fun testAcceptInputFalseWhenPsiFileNotFound() {
        project.service<Settings>().apply {
            pluginEnabled = true
            supportedLanguages = "PHP"
        }
        myFixture.configureByText("accept_input_no_psi.php", "<?php\n'target';")
        val directory: VirtualFile = myFixture.file.virtualFile.parent!!
        assertTrue("expected a directory VirtualFile for this test to be meaningful", directory.isDirectory)

        assertFalse(PsaStaticReferenceIndex().inputFilter.acceptInput(directory))
    }

    fun testAcceptInputTrueWhenAllConditionsMet() {
        project.service<Settings>().apply {
            pluginEnabled = true
            supportedLanguages = "PHP"
        }
        val file = myFixture.configureByText("accept_input_ok.php", "<?php\n'target';").virtualFile

        assertTrue(PsaStaticReferenceIndex().inputFilter.acceptInput(file))
    }
}
