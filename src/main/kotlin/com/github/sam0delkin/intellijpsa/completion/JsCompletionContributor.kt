package com.github.sam0delkin.intellijpsa.completion

import com.github.sam0delkin.intellijpsa.services.Language

class JsCompletionContributor() {
    class Completion : AbstractCompletionContributor.Completion() {
        override fun getLanguage(): Language {
            return Language.JS
        }
    }

    class GotoDeclaration : AbstractCompletionContributor.GotoDeclaration() {
        override fun getLanguage(): Language {
            return Language.JS
        }

    }
}