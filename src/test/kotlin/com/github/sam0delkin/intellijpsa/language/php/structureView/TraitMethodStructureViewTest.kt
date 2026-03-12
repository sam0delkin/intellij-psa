package com.github.sam0delkin.intellijpsa.language.php.structureView

import com.github.sam0delkin.intellijpsa.language.php.settings.PhpPsaSettings
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.PhpClass

class TraitMethodStructureViewTest : BasePlatformTestCase() {
    fun testStructureViewExtension() {
        val settings = project.service<PhpPsaSettings>()
        settings.enabled = true

        myFixture.configureByText(
            "test.php",
            """
            <?php
            trait MyTrait {
                public function traitMethod() {}
            }
            class MyClass {
                use MyTrait;
                public function classMethod() {}
            }
            """.trimIndent(),
        )

        val phpClass = myFixture.findElementByText("class MyClass", PhpClass::class.java)
        val extension = TraitMethodStructureViewExtension()

        val children = extension.getChildren(phpClass)
        assertSize(1, children)

        val traitGroup = children[0]
        assertNotNull(traitGroup)
        assertEquals("MyTrait", traitGroup?.presentation?.presentableText)

        val traitMethods = traitGroup?.getChildren()
        assertNotNull(traitMethods)
        assertSize(1, traitMethods!!)
        assertEquals("traitMethod", traitMethods[0]?.presentation?.presentableText)
    }

    fun testStructureViewExtensionDisabled() {
        val settings = project.service<PhpPsaSettings>()
        settings.enabled = false

        myFixture.configureByText(
            "test.php",
            """
            <?php
            trait MyTrait {
                public function traitMethod() {}
            }
            class MyClass {
                use MyTrait;
            }
            """.trimIndent(),
        )

        val phpClass = myFixture.findElementByText("class MyClass", PhpClass::class.java)
        val extension = TraitMethodStructureViewExtension()

        val children = extension.getChildren(phpClass)
        assertEmpty(children)
    }

    fun testTraitResolver() {
        myFixture.configureByText(
            "test.php",
            """
            <?php
            trait Trait1 {}
            trait Trait2 { use Trait1; }
            class MyClass { use Trait2; }
            """.trimIndent(),
        )

        val phpClass = myFixture.findElementByText("class MyClass", PhpClass::class.java)
        val traits = TraitResolver.collectTraits(phpClass)

        assertSize(2, traits)
        val names = traits.map { it.name }.toSet()
        assertContainsElements(names, "Trait1", "Trait2")
    }
}
