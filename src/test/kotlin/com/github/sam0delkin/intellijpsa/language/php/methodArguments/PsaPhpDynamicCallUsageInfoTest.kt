package com.github.sam0delkin.intellijpsa.language.php.methodArguments

import com.github.sam0delkin.intellijpsa.language.php.model.MethodArgumentProviderModel
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression

class PsaPhpDynamicCallUsageInfoTest : BasePlatformTestCase() {
    fun testExposesArrayAndProvider() {
        myFixture.configureByText(
            "Call.php",
            """
            <?php
            class MyService {
                public function doWork(${'$'}x) {}
            }
            ${'$'}svc = new MyService();
            ${'$'}svc->doWork([1]);
            """.trimIndent(),
        )

        val array =
            PsiTreeUtil
                .findChildrenOfType(myFixture.file, ArrayCreationExpression::class.java)
                .first()
        val provider =
            MethodArgumentProviderModel().apply {
                `class` = "MyService"
                method = "doWork"
                argumentsArgumentIndex = 0
            }

        val usageInfo = PsaPhpDynamicCallUsageInfo(array, provider)

        assertSame(array, usageInfo.array)
        assertSame(provider, usageInfo.provider)
        assertEquals(array.text, usageInfo.element?.text)
    }

    fun testIsUsageInfo() {
        myFixture.configureByText(
            "Call.php",
            """
            <?php
            [1, 2, 3];
            """.trimIndent(),
        )

        val array =
            PsiTreeUtil
                .findChildrenOfType(myFixture.file, ArrayCreationExpression::class.java)
                .first()
        val provider = MethodArgumentProviderModel()

        val usageInfo = PsaPhpDynamicCallUsageInfo(array, provider)

        assertInstanceOf(usageInfo, com.intellij.usageView.UsageInfo::class.java)
    }
}
