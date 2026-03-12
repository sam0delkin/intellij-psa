package com.github.sam0delkin.intellijpsa.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.PathMappingSettings

class SettingsTest : BasePlatformTestCase() {
    fun testSettingsDefaultValues() {
        val settings = Settings()

        assertFalse(settings.pluginEnabled)
        assertFalse(settings.debug)
        assertTrue(settings.showErrors)
        assertEquals(".psa/psa.php", settings.scriptPath)
        assertEquals(0, settings.pathMappings?.size)
        assertEquals("", settings.goToFilter)
        assertFalse(settings.supportsBatch)
        assertEquals("", settings.supportedLanguages)
        assertEquals(5000, settings.executionTimeout)
        assertEquals(100, settings.maxNestingLevel)
        assertNull(settings.singleFileCodeTemplates)
        assertNull(settings.multipleFileCodeTemplates)
        assertFalse(settings.supportsStaticCompletions)
        assertFalse(settings.resolveReferences)
        assertEquals("", settings.indexFolder)
        assertFalse(settings.useVelocityInIndex)
        assertFalse(settings.annotateUndefinedElements)
        assertNull(settings.targetElementTypes)
        assertNull(settings.staticCompletionsHash)
        assertNull(settings.editorActions)
    }

    fun testSettingsIsLanguageSupported() {
        val settings = Settings().apply {
            pluginEnabled = true
            supportedLanguages = "PHP,JavaScript,TypeScript"
        }

        assertTrue(settings.isLanguageSupported("PHP"))
        assertTrue(settings.isLanguageSupported("JavaScript"))
        assertTrue(settings.isLanguageSupported("TypeScript"))
        assertFalse(settings.isLanguageSupported("Python"))
        assertFalse(settings.isLanguageSupported("Java"))
    }

    fun testSettingsIsLanguageSupportedWhenPluginDisabled() {
        val settings = Settings().apply {
            pluginEnabled = false
            supportedLanguages = "PHP,JavaScript"
        }

        assertFalse(settings.isLanguageSupported("PHP"))
        assertFalse(settings.isLanguageSupported("JavaScript"))
    }

    fun testSettingsIsLanguageSupportedWhenEmpty() {
        val settings = Settings().apply {
            pluginEnabled = true
            supportedLanguages = ""
        }

        assertFalse(settings.isLanguageSupported("PHP"))
    }

    fun testSettingsGetScriptDir() {
        val settings = Settings().apply {
            scriptPath = "/path/to/script.php"
        }

        assertEquals("/path/to", settings.getScriptDir())
    }

    fun testSettingsGetScriptDirNested() {
        val settings = Settings().apply {
            scriptPath = "/home/user/project/.psa/psa.php"
        }

        assertEquals("/home/user/project/.psa", settings.getScriptDir())
    }

    fun testSettingsGetScriptDirNull() {
        val settings = Settings().apply {
            scriptPath = null
        }

        assertNull(settings.getScriptDir())
    }

    fun testSettingsGetScriptDirRootFile() {
        val settings = Settings().apply {
            scriptPath = "psa.php"
        }

        assertEquals("", settings.getScriptDir())
    }

    fun testSettingsReplacePathMappings() {
        val settings = Settings().apply {
            pluginEnabled = true
        }

        val result = settings.replacePathMappings("/remote/path/file.php")

        // Без pathMappings строка не меняется
        assertEquals("/remote/path/file.php", result)
    }

    fun testSettingsReplacePathMappingsWhenPluginDisabled() {
        val settings = Settings().apply {
            pluginEnabled = false
            pathMappings = arrayOf(
                PathMappingSettings.PathMapping("/remote", "/local")
            )
        }

        val result = settings.replacePathMappings("/remote/file.php")

        assertEquals("/remote/file.php", result)
    }

    fun testSettingsReplacePathMappingsEmpty() {
        val settings = Settings().apply {
            pluginEnabled = true
            pathMappings = arrayOf()
        }

        val result = settings.replacePathMappings("/remote/file.php")

        assertEquals("/remote/file.php", result)
    }

    fun testSettingsIsElementTypeMatchingFilter() {
        val settings = Settings().apply {
            goToFilter = "STRING_LITERAL,METHOD_REFERENCE,CLASS_REFERENCE"
        }

        assertTrue(settings.isElementTypeMatchingFilter("STRING_LITERAL"))
        assertTrue(settings.isElementTypeMatchingFilter("METHOD_REFERENCE"))
        assertTrue(settings.isElementTypeMatchingFilter("CLASS_REFERENCE"))
        assertFalse(settings.isElementTypeMatchingFilter("FUNCTION"))
        assertFalse(settings.isElementTypeMatchingFilter("VARIABLE"))
    }

    fun testSettingsIsElementTypeMatchingFilterEmpty() {
        val settings = Settings().apply {
            goToFilter = ""
        }

        assertTrue(settings.isElementTypeMatchingFilter("STRING_LITERAL"))
        assertTrue(settings.isElementTypeMatchingFilter("ANY_TYPE"))
    }

    fun testSettingsIsElementTypeMatchingFilterNull() {
        val settings = Settings().apply {
            goToFilter = null
        }

        assertTrue(settings.isElementTypeMatchingFilter("STRING_LITERAL"))
    }

    fun testSettingsStateCopy() {
        val settings1 = Settings().apply {
            pluginEnabled = true
            debug = true
            showErrors = false
            scriptPath = "/custom/path/psa.php"
            supportedLanguages = "PHP"
            executionTimeout = 10000
            maxNestingLevel = 50
        }

        val settings2 = Settings()
        settings2.loadState(settings1)

        assertEquals(settings1.pluginEnabled, settings2.pluginEnabled)
        assertEquals(settings1.debug, settings2.debug)
        assertEquals(settings1.showErrors, settings2.showErrors)
        assertEquals(settings1.scriptPath, settings2.scriptPath)
        assertEquals(settings1.supportedLanguages, settings2.supportedLanguages)
        assertEquals(settings1.executionTimeout, settings2.executionTimeout)
        assertEquals(settings1.maxNestingLevel, settings2.maxNestingLevel)
    }

    fun testSettingsGetState() {
        val settings = Settings().apply {
            pluginEnabled = true
            debug = true
        }

        val state = settings.state

        assertEquals(settings, state)
    }

    fun testTemplateFormField() {
        val field = TemplateFormField().apply {
            name = "className"
            title = "Class Name"
            type = TemplateFormFieldType.Text
            focused = true
            options = arrayListOf("Option1", "Option2")
        }

        assertEquals("className", field.name)
        assertEquals("Class Name", field.title)
        assertEquals(TemplateFormFieldType.Text, field.type)
        assertTrue(field.focused == true)
        assertEquals(2, field.options?.size)
    }

    fun testTemplateFormFieldEquals() {
        val field1 = TemplateFormField().apply {
            name = "className"
            title = "Class Name"
            type = TemplateFormFieldType.Text
            focused = true
            options = arrayListOf("Option1", "Option2")
        }

        val field2 = TemplateFormField().apply {
            name = "className"
            title = "Class Name"
            type = TemplateFormFieldType.Text
            focused = true
            options = arrayListOf("Option1", "Option2")
        }

        val field3 = TemplateFormField().apply {
            name = "className"
            title = "Class Name"
            type = TemplateFormFieldType.Text
            focused = true
            options = arrayListOf("Option1")
        }

        assertEquals(field1, field2)
        assertEquals(field1.hashCode(), field2.hashCode())
        assertTrue(field1 != field3)
    }

    fun testTemplateFormFieldToString() {
        val field = TemplateFormField().apply {
            name = "testName"
            title = "Test Title"
            type = TemplateFormFieldType.Select
            options = arrayListOf("A", "B")
        }

        val str = field.toString()

        assertTrue(str.contains("name=testName"))
        assertTrue(str.contains("title=Test Title"))
        assertTrue(str.contains("type=Select"))
    }

    fun testSingleFileCodeTemplate() {
        val template = SingleFileCodeTemplate().apply {
            pathRegex = "^/src/"
            name = "my_template"
            title = "My Template"
            formFields = arrayListOf(
                TemplateFormField().apply {
                    name = "className"
                    title = "Class Name"
                    type = TemplateFormFieldType.Text
                }
            )
        }

        assertEquals("^/src/", template.pathRegex)
        assertEquals("my_template", template.name)
        assertEquals("My Template", template.title)
        assertEquals(1, template.formFields?.size)
    }

    fun testSingleFileCodeTemplateEquals() {
        val template1 = SingleFileCodeTemplate().apply {
            pathRegex = "^/src/"
            name = "template"
            title = "Template"
            formFields = arrayListOf()
        }

        val template2 = SingleFileCodeTemplate().apply {
            pathRegex = "^/src/"
            name = "template"
            title = "Template"
            formFields = arrayListOf()
        }

        val template3 = SingleFileCodeTemplate().apply {
            pathRegex = "^/app/"
            name = "template"
            title = "Template"
            formFields = arrayListOf()
        }

        assertEquals(template1, template2)
        assertEquals(template1.hashCode(), template2.hashCode())
        assertTrue(template1 != template3)
    }

    fun testMultipleFileCodeTemplate() {
        val template = MultipleFileCodeTemplate().apply {
            pathRegex = "^/src/"
            name = "multi_template"
            title = "Multi File Template"
            formFields = arrayListOf()
            fileCount = 3
        }

        assertEquals("^/src/", template.pathRegex)
        assertEquals("multi_template", template.name)
        assertEquals("Multi File Template", template.title)
        assertEquals(3, template.fileCount)
    }

    fun testTemplateFormFieldTypeValues() {
        assertEquals(TemplateFormFieldType.Text, TemplateFormFieldType.Text)
        assertEquals(TemplateFormFieldType.RichText, TemplateFormFieldType.RichText)
        assertEquals(TemplateFormFieldType.Checkbox, TemplateFormFieldType.Checkbox)
        assertEquals(TemplateFormFieldType.Select, TemplateFormFieldType.Select)
        assertEquals(TemplateFormFieldType.Collection, TemplateFormFieldType.Collection)
    }
}
