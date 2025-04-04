package com.github.sam0delkin.intellijpsa.action.template

import com.github.sam0delkin.intellijpsa.icons.Icons.PluginIcon
import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeView
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDirectory

class PsaFileTemplateActionGroup :
    ActionGroup(
        "PSA Templates",
        "Project Specific Autocomplete templates returned by your completion script",
        PluginIcon,
    ) {
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val psaManager = e?.project?.service<PsaManager>() ?: return arrayOf()
        val settings = psaManager.getSettings()
        if (null === settings.singleFileCodeTemplates || settings.singleFileCodeTemplates!!.isEmpty()) {
            return arrayOf()
        }
        val actions = ArrayList<AnAction>()
        val dataContext: DataContext = e.dataContext
        val view: IdeView = LangDataKeys.IDE_VIEW.getData(dataContext) ?: return arrayOf()

        val directories: Array<PsiDirectory> = view.directories
        if (directories.isEmpty()) {
            return arrayOf()
        }
        val directoryPath =
            '/' +
                VfsUtil
                    .getRelativePath(
                        directories[0].virtualFile,
                        e.project!!.guessProjectDir()!!,
                        '/',
                    ).toString() + '/'

        for (template in settings.singleFileCodeTemplates!!) {
            if (null !== template.pathRegex && !directoryPath.matches(Regex(template.pathRegex!!))) {
                continue
            }

            val action =
                SingleFileTemplateAction(
                    "Create " + template.title,
                    "",
                    AllIcons.FileTypes.Custom,
                )
            action.templateName = template.name
            action.templatePresentation.text = template.title
            action.templatePresentation.icon = AllIcons.FileTypes.Custom
            actions.add(action)
        }

        for (template in settings.multipleFileCodeTemplates!!) {
            if (null !== template.pathRegex && !directoryPath.matches(Regex(template.pathRegex!!))) {
                continue
            }

            val action =
                MultipleFileTemplateAction(
                    "Create " + template.title,
                    "",
                    AllIcons.FileTypes.Custom,
                )
            action.templateName = template.name
            action.templatePresentation.text = template.title
            action.templatePresentation.icon = AllIcons.FileTypes.Custom
            actions.add(action)
        }

        return actions.toTypedArray()
    }
}
