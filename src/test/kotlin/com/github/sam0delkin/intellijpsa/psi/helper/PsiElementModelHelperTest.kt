package com.github.sam0delkin.intellijpsa.psi.helper

import com.github.sam0delkin.intellijpsa.model.psi.PsiElementModel
import com.github.sam0delkin.intellijpsa.model.psi.PsiElementModelChild
import com.github.sam0delkin.intellijpsa.model.psi.PsiElementPatternModel
import com.intellij.lang.FileASTNode
import com.intellij.lang.LighterASTNode
import com.intellij.lang.TreeBackedLighterAST
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Two pre-existing production bugs in `PsiElementModelHelper` are documented (not fixed - out of
 * scope for this coverage-only task) via tests that assert the current, actually-observed behavior:
 * - `matches(tree: TreeBackedLighterAST, element: LighterASTNode, pattern)`'s `anyChild` branch
 *   (~line 179): the inner lambda checks `matches(tree, currentElement, pattern.anyParent!!)`
 *   instead of `pattern.anyChild!!`. With the natural usage (a pattern that sets `anyChild` but not
 *   `anyParent`), this throws an NPE rather than matching - see
 *   `testMatchesLighterASTAnyChildThrowsDueToProductionBug`.
 * - `matches(element: ASTNode, pattern)`'s `anyParent` branch (~lines 216-230): the loop only ever
 *   returns `false` (on a non-matching ancestor or reaching the root) and has no `return true` path
 *   at all, so it can never report a match even when a matching ancestor genuinely exists - see
 *   `testMatchesAstNodeWithAnyParentAlwaysFalseDueToProductionBug`.
 *
 * A third, environment-level (not code) limitation is also documented rather than worked around:
 * `matches(element: ASTNode, pattern)`'s `withMatcher` branch (~lines 249-266) calls
 * `Velocity.evaluate(...)`, which can never succeed in this project's coverage-instrumented test
 * JVM - see `testMatchesAstNodeWithMatcherThrowsDueToCoverageAgentVelocityConflict` for the full
 * explanation (a genuine conflict between the IntelliJ coverage agent and Apache Velocity's
 * reflection-based static init, confirmed via a direct `Velocity.init()` probe).
 */
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

    fun testMatchesWithAnyPrev() {
        val farPrevModel = createModel("Operator", "=")
        val prevModel = createModel("Whitespace", " ", prev = farPrevModel)
        val model = createModel("STRING_LITERAL", "'test'", prev = prevModel)
        val pattern =
            PsiElementPatternModel(
                withText = "'test'",
                anyPrev = PsiElementPatternModel(withText = "="),
            )

        assertTrue(PsiElementModelHelper.matches(model, pattern))
    }

    fun testMatchesWithAnyPrevNotFound() {
        val prevModel = createModel("Whitespace", " ")
        val model = createModel("STRING_LITERAL", "'test'", prev = prevModel)
        val pattern =
            PsiElementPatternModel(
                withText = "'test'",
                anyPrev = PsiElementPatternModel(withText = "="),
            )

        assertFalse(PsiElementModelHelper.matches(model, pattern))
    }

    fun testMatchesWithAnyPrevNullPrev() {
        val model = createModel("STRING_LITERAL", "'test'")
        val pattern =
            PsiElementPatternModel(
                withText = "'test'",
                anyPrev = PsiElementPatternModel(withText = "="),
            )

        assertFalse(PsiElementModelHelper.matches(model, pattern))
    }

    fun testMatchesWithAnyNext() {
        val farNextModel = createModel("Semicolon", ";")
        val nextModel = createModel("Whitespace", " ", next = farNextModel)
        val model = createModel("STRING_LITERAL", "'test'", next = nextModel)
        val pattern =
            PsiElementPatternModel(
                withText = "'test'",
                anyNext = PsiElementPatternModel(withText = ";"),
            )

        assertTrue(PsiElementModelHelper.matches(model, pattern))
    }

    fun testMatchesWithAnyNextNotFound() {
        val nextModel = createModel("Whitespace", " ")
        val model = createModel("STRING_LITERAL", "'test'", next = nextModel)
        val pattern =
            PsiElementPatternModel(
                withText = "'test'",
                anyNext = PsiElementPatternModel(withText = ";"),
            )

        assertFalse(PsiElementModelHelper.matches(model, pattern))
    }

    fun testMatchesWithAnyNextNullNext() {
        val model = createModel("STRING_LITERAL", "'test'")
        val pattern =
            PsiElementPatternModel(
                withText = "'test'",
                anyNext = PsiElementPatternModel(withText = ";"),
            )

        assertFalse(PsiElementModelHelper.matches(model, pattern))
    }

    // --- matches(tree: TreeBackedLighterAST, element: LighterASTNode, pattern) ---

    private fun lighterTree(): TreeBackedLighterAST {
        myFixture.configureByText("test.php", "<?php 'target';")

        return TreeBackedLighterAST(myFixture.file.node as FileASTNode)
    }

    private fun stringLiteralLighterNode(): LighterASTNode {
        val element = myFixture.findElementByText("'target'", PsiElement::class.java)!!

        return TreeBackedLighterAST.wrap(element.node)
    }

    fun testMatchesLighterASTWithType() {
        val tree = lighterTree()
        val element = stringLiteralLighterNode()
        val matchingPattern = PsiElementPatternModel(withType = element.tokenType.toString())
        val nonMatchingPattern = PsiElementPatternModel(withType = "SomeOtherType")

        assertTrue(PsiElementModelHelper.matches(tree, element, matchingPattern))
        assertFalse(PsiElementModelHelper.matches(tree, element, nonMatchingPattern))
    }

    fun testMatchesLighterASTWithTextOnTokenNode() {
        // withText on this overload only applies when `element is LighterASTTokenNode` - which only
        // holds for nodes obtained via real tree traversal (tree.getChildren/tree.root), not for the
        // generic NodeWrapper that the standalone TreeBackedLighterAST.wrap(astNode) utility always
        // produces regardless of leaf-ness. The "String" composite decomposes into 3 real LighterAST
        // children (open quote, content, close quote); the content token's text is the bare "target".
        val tree = lighterTree()
        val stringElement = stringLiteralLighterNode()
        val contentToken =
            tree.getChildren(stringElement).first {
                it.tokenType.toString() != "left single quote" &&
                    it.tokenType.toString() != "right single quote"
            }

        assertTrue(PsiElementModelHelper.matches(tree, contentToken, PsiElementPatternModel(withText = "target")))
        assertFalse(PsiElementModelHelper.matches(tree, contentToken, PsiElementPatternModel(withText = "other")))
    }

    fun testMatchesLighterASTWithParent() {
        val tree = lighterTree()
        val element = stringLiteralLighterNode()
        val parentType = tree.getParent(element)!!.tokenType.toString()

        assertTrue(
            PsiElementModelHelper.matches(tree, element, PsiElementPatternModel(parent = PsiElementPatternModel(withType = parentType))),
        )
        assertFalse(
            PsiElementModelHelper.matches(
                tree,
                element,
                PsiElementPatternModel(parent = PsiElementPatternModel(withType = "SomeOtherType")),
            ),
        )
    }

    fun testMatchesLighterASTWithParentNullParent() {
        val tree = lighterTree()
        val root = tree.root

        assertFalse(
            PsiElementModelHelper.matches(tree, root, PsiElementPatternModel(parent = PsiElementPatternModel(withType = "Anything"))),
        )
    }

    fun testMatchesLighterASTWithAnyParent() {
        val tree = lighterTree()
        val element = stringLiteralLighterNode()
        val fileType = tree.root.tokenType.toString()

        assertTrue(
            PsiElementModelHelper.matches(tree, element, PsiElementPatternModel(anyParent = PsiElementPatternModel(withType = fileType))),
        )
        assertFalse(
            PsiElementModelHelper.matches(
                tree,
                element,
                PsiElementPatternModel(anyParent = PsiElementPatternModel(withType = "SomeOtherType")),
            ),
        )
    }

    fun testMatchesLighterASTWithAnyParentNullParent() {
        val tree = lighterTree()
        val root = tree.root

        assertFalse(
            PsiElementModelHelper.matches(tree, root, PsiElementPatternModel(anyParent = PsiElementPatternModel(withType = "Anything"))),
        )
    }

    fun testMatchesLighterASTAnyChildEmptyChildren() {
        // The "String" composite element decomposes into 3 LighterAST children (the two quote
        // tokens and the content token) - it is NOT itself a genuinely childless node, so use one
        // of those child tokens (a true leaf) to exercise the isEmpty() early-return branch.
        val tree = lighterTree()
        val stringElement = stringLiteralLighterNode()
        val leafToken = tree.getChildren(stringElement).first()
        assertTrue(tree.getChildren(leafToken).isEmpty())

        assertFalse(
            PsiElementModelHelper.matches(
                tree,
                leafToken,
                PsiElementPatternModel(anyChild = PsiElementPatternModel(withType = "Anything")),
            ),
        )
    }

    fun testMatchesLighterASTAnyChildThrowsDueToProductionBug() {
        val tree = lighterTree()
        val root = tree.root
        assertTrue(tree.getChildren(root).isNotEmpty())

        try {
            PsiElementModelHelper.matches(tree, root, PsiElementPatternModel(anyChild = PsiElementPatternModel(withType = "Anything")))
            fail("Expected an NPE from the pattern.anyParent!! typo bug in the anyChild branch")
        } catch (_: NullPointerException) {
            // Expected - see this file's class doc comment.
        }
    }

    // --- matches(element: ASTNode, pattern) ---

    private fun stringLiteralAstNode(): com.intellij.lang.ASTNode =
        myFixture
            .apply { configureByText("test.php", "<?php 'target';") }
            .findElementByText("'target'", PsiElement::class.java)!!
            .node!!

    fun testMatchesAstNodeWithType() {
        val node = stringLiteralAstNode()

        assertTrue(PsiElementModelHelper.matches(node, PsiElementPatternModel(withType = node.elementType.toString())))
        assertFalse(PsiElementModelHelper.matches(node, PsiElementPatternModel(withType = "SomeOtherType")))
    }

    fun testMatchesAstNodeWithText() {
        val node = stringLiteralAstNode()

        assertTrue(PsiElementModelHelper.matches(node, PsiElementPatternModel(withText = "'target'")))
        assertFalse(PsiElementModelHelper.matches(node, PsiElementPatternModel(withText = "other")))
    }

    fun testMatchesAstNodeWithParent() {
        val node = stringLiteralAstNode()
        val parentType = node.treeParent!!.elementType.toString()

        assertTrue(
            PsiElementModelHelper.matches(node, PsiElementPatternModel(parent = PsiElementPatternModel(withType = parentType))),
        )
        assertFalse(
            PsiElementModelHelper.matches(node, PsiElementPatternModel(parent = PsiElementPatternModel(withType = "SomeOtherType"))),
        )
    }

    fun testMatchesAstNodeWithParentNullParent() {
        val node = stringLiteralAstNode()
        val root = node.psi.containingFile.node!!

        assertFalse(
            PsiElementModelHelper.matches(root, PsiElementPatternModel(parent = PsiElementPatternModel(withType = "Anything"))),
        )
    }

    // Unlike the other two `matches` overloads, this one's `anyParent` loop (PsiElementModelHelper.kt
    // ~lines 216-230) has no `return true` path at all - it returns false either when an ancestor
    // fails to match, or when the walk reaches the root - so it can never return true, even when a
    // matching ancestor genuinely exists. This is a second, independent pre-existing production bug
    // (distinct from the LighterAST anyChild bug documented above); out of scope to fix here.
    fun testMatchesAstNodeWithAnyParentAlwaysFalseDueToProductionBug() {
        val node = stringLiteralAstNode()
        val fileType =
            node.psi.containingFile.node!!
                .elementType
                .toString()

        assertFalse(
            PsiElementModelHelper.matches(node, PsiElementPatternModel(anyParent = PsiElementPatternModel(withType = fileType))),
        )
    }

    fun testMatchesAstNodeWithAnyParentNullParent() {
        val node = stringLiteralAstNode()
        val root = node.psi.containingFile.node!!

        assertFalse(
            PsiElementModelHelper.matches(root, PsiElementPatternModel(anyParent = PsiElementPatternModel(withType = "Anything"))),
        )
    }

    fun testMatchesAstNodeAnyChildEmptyChildren() {
        val node = stringLiteralAstNode()

        assertFalse(
            PsiElementModelHelper.matches(node, PsiElementPatternModel(anyChild = PsiElementPatternModel(withType = "Anything"))),
        )
    }

    fun testMatchesAstNodeAnyChildMatchAndNoMatch() {
        val root = stringLiteralAstNode().psi.containingFile.node!!
        val childType =
            root
                .getChildren(null)
                .first()
                .elementType
                .toString()

        assertTrue(
            PsiElementModelHelper.matches(root, PsiElementPatternModel(anyChild = PsiElementPatternModel(withType = childType))),
        )
        assertFalse(
            PsiElementModelHelper.matches(root, PsiElementPatternModel(anyChild = PsiElementPatternModel(withType = "SomeOtherType"))),
        )
    }

    // Confirmed via a diagnostic probe (Velocity.init(properties) called directly in a test throws
    // the same chain seen below) that Apache Velocity cannot initialize AT ALL in this project's
    // coverage-instrumented test JVM: org.apache.velocity.exception.VelocityException:
    // "Could not initialize property keys deprecation map because DeprecatedRuntimeConstants.
    // __$branchHits$__ field isn't properly named". The IntelliJ coverage agent
    // (intellij-coverage-agent, which backs Kover 0.6.1 here) injects a synthetic `__$branchHits$__`
    // field into every loaded class it instruments, including third-party library classes like
    // Velocity's own `DeprecatedRuntimeConstants` - and Velocity's `DeprecationAwareExtProperties`
    // reflects over every declared field of that class assuming they are all real Velocity
    // constants, so the injected field breaks its static initialization outright
    // (ExceptionInInitializerError on RuntimeSingleton's <clinit>, permanently poisoning that class
    // for the rest of the JVM's lifetime, per standard JVM class-init-failure semantics). Because
    // production code's `withMatcher` handler only catches `Exception` (line ~258), not `Throwable`,
    // the resulting `NoClassDefFoundError` (an `Error`) is never caught - it propagates straight out
    // of `matches()`, so every `withMatcher` test call fails identically regardless of the template
    // content (true/false/1/invalid syntax all hit the same uncaught Error before any template logic
    // runs). This is a genuine coverage-tooling/third-party-library conflict, not something fixable
    // via test data or code under this project's pinned Kover 0.6.1 setup - the ~18-line
    // `withMatcher` branch (PsiElementModelHelper.kt ~lines 249-266) is a documented residual gap.
    fun testMatchesAstNodeWithMatcherThrowsDueToCoverageAgentVelocityConflict() {
        val node = stringLiteralAstNode()

        // The exact Error subtype depends on whether Velocity's RuntimeSingleton has already failed
        // to initialize once before in this JVM: the first-ever attempt throws
        // ExceptionInInitializerError directly, while any later attempt throws the JVM's cached
        // NoClassDefFoundError instead (standard class-init-failure semantics) - assert broadly on
        // Error rather than pin to whichever one happens to occur based on test execution order.
        assertThrows(Error::class.java) {
            PsiElementModelHelper.matches(node, PsiElementPatternModel(withMatcher = "true"))
        }
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
