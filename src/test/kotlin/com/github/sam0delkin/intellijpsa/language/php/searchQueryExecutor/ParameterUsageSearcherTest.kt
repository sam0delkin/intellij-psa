package com.github.sam0delkin.intellijpsa.language.php.searchQueryExecutor

import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.openapi.components.service
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.Parameter

class ParameterUsageSearcherTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        val settings = project.service<Settings>()
        settings.pluginEnabled = true
    }

    fun testPositionalArgumentUsage() {
        myFixture.configureByText(
            "test.php",
            """
            <?php
            function foo(${'$'}param) {}
            foo(1);
            """.trimIndent(),
        )

        // Find Parameter by name with $
        val parameter = myFixture.findElementByText("${'$'}param", Parameter::class.java)
        val references = ReferencesSearch.search(parameter).findAll()

        assertTrue("Should find at least one reference", references.isNotEmpty())
    }

    fun testNamedArgumentUsage() {
        myFixture.configureByText(
            "test.php",
            """
            <?php
            function foo(${'$'}param) {}
            foo(param: 1);
            """.trimIndent(),
        )

        val parameter = myFixture.findElementByText("${'$'}param", Parameter::class.java)
        val references = ReferencesSearch.search(parameter).findAll()

        assertTrue("Should find at least one reference for named argument", references.isNotEmpty())
    }

    fun testNamedArgumentWithDifferentName() {
        myFixture.configureByText(
            "test.php",
            """
            <?php
            function foo(${'$'}param, ${'$'}other) {}
            foo(other: 1);
            """.trimIndent(),
        )

        val parameter = myFixture.findElementByText("${'$'}param", Parameter::class.java)
        val references = ReferencesSearch.search(parameter).findAll()

        assertSize(0, references)
    }

    fun testPositionalArgumentAtIndex() {
        myFixture.configureByText(
            "test.php",
            """
            <?php
            function foo(${'$'}first, ${'$'}second) {}
            foo(1, 2);
            """.trimIndent(),
        )

        val parameter = myFixture.findElementByText("${'$'}second", Parameter::class.java)
        val references = ReferencesSearch.search(parameter).findAll()

        assertTrue("Should find reference for second positional argument", references.isNotEmpty())
    }

    fun testMixedNamedAndPositionalArguments() {
        myFixture.configureByText(
            "test.php",
            """
            <?php
            function foo(${'$'}a, ${'$'}b, ${'$'}c) {}
            foo(1, c: 3);
            """.trimIndent(),
        )

        val paramA = myFixture.findElementByText("${'$'}a", Parameter::class.java)
        assertNotNull("Param a should be found. File text: " + myFixture.file.text, paramA)
        assertTrue("Should find reference for a", ReferencesSearch.search(paramA!!).findAll().isNotEmpty())

        val paramB = myFixture.findElementByText("${'$'}b", Parameter::class.java)
        assertNotNull("Param b should be found. File text: " + myFixture.file.text, paramB)
        val refsB = ReferencesSearch.search(paramB!!).findAll()
        if (refsB.isNotEmpty()) {
            fail("Found refs for B: " + refsB.joinToString { it.element.text })
        }

        val paramC = myFixture.findElementByText("${'$'}c", Parameter::class.java)
        assertNotNull("Param c should be found. File text: " + myFixture.file.text, paramC)
        assertTrue("Should find reference for c", ReferencesSearch.search(paramC!!).findAll().isNotEmpty())
    }

    fun testNoReferencesWhenPluginDisabled() {
        val settings = project.service<Settings>()
        settings.pluginEnabled = false

        myFixture.configureByText(
            "test.php",
            """
            <?php
            function foo(${'$'}param) {}
            foo(1);
            """.trimIndent(),
        )

        val parameter = myFixture.findElementByText("${'$'}param", Parameter::class.java)
        val references = ReferencesSearch.search(parameter).findAll()

        assertSize(0, references)
    }
}
