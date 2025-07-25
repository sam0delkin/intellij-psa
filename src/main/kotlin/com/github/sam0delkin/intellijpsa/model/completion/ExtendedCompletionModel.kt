package com.github.sam0delkin.intellijpsa.model.completion

import com.github.sam0delkin.intellijpsa.exception.UpdateStaticCompletionsException
import com.github.sam0delkin.intellijpsa.icons.Icons
import com.github.sam0delkin.intellijpsa.util.PsiUtils
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import kotlinx.serialization.Serializable

@Serializable
class ExtendedCompletionModel : CompletionModel() {
    var reference: SmartPsiElementPointer<PsiElement>? = null
    var completionName: String? = null

    companion object {
        fun create(
            completionModel: CompletionModel,
            project: Project,
        ): ExtendedCompletionModel =
            ExtendedCompletionModel().apply {
                this.text = completionModel.text
                this.presentableText = completionModel.presentableText
                this.tailText = completionModel.tailText
                this.type = completionModel.type
                this.priority = completionModel.priority
                this.link = completionModel.link
                this.bold = completionModel.bold
                val goToElementList = this.toGoToElement(project)
                if (null !== goToElementList) {
                    this.reference = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(goToElementList)
                }
            }
    }

    fun toCompletionLookupElement(): LookupElement {
        var priority = 0.0
        var element = LookupElementBuilder.create(this.text ?: "")
        element = element.withIcon(Icons.PluginIcon)

        if (true == this.bold) {
            element = element.bold()
            priority = 100.0
        }

        if (null !== this.presentableText) {
            element = element.withPresentableText(this.presentableText!!)
        }

        if (this.tailText != null) {
            element = element.withTailText(this.tailText)
        }

        if (this.type != null) {
            element = element.withTypeText(this.type)
        }

        if (this.priority != null) {
            priority = this.priority!!
        }

        return PrioritizedLookupElement.withPriority(element, priority)
    }

    fun toGoToElement(project: Project): PsiElement? {
        if (null != this.reference) {
            return this.reference!!.element ?: throw UpdateStaticCompletionsException()
        }

        val linkData = this.link ?: ""
        var text = this.text ?: linkData
        if (this.presentableText != null) {
            text = this.presentableText!!
        }

        return PsiUtils.processLink(linkData, text, project, true, this.completionName)
    }
}
