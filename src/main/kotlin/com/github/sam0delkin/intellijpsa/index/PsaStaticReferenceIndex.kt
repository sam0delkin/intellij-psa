package com.github.sam0delkin.intellijpsa.index

import com.github.sam0delkin.intellijpsa.psi.helper.PsiElementModelHelper
import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.github.sam0delkin.intellijpsa.util.PsiUtils
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.lang.LighterASTNode
import com.intellij.lang.LighterASTTokenNode
import com.intellij.lang.TreeBackedLighterAST
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.tree.RecursiveLighterASTNodeWalkingVisitor
import com.intellij.psi.util.elementType
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.indexing.PsiDependentFileContent
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import com.jetbrains.rd.util.string.printToString
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.Velocity
import java.io.StringWriter

val INDEX_ID: ID<String, Map<String, List<String>>> = ID.create("com.github.sam0delkin.intellijpsa.index.PsaStaticReferenceIndex")

class PsaStaticReferenceIndex : FileBasedIndexExtension<String, Map<String, List<String>>>() {
    override fun getName(): ID<String, Map<String, List<String>>> = INDEX_ID

    override fun getIndexer(): DataIndexer<String, Map<String, List<String>>, FileContent> {
        return object : DataIndexer<String, Map<String, List<String>>, FileContent> {
            override fun map(inputData: FileContent): MutableMap<String, Map<String, List<String>>> {
                val manager = inputData.project.service<PsaManager>()
                val settings = manager.getSettings()
                val fileResult = mutableMapOf<String, Map<String, List<String>>>()
                val currentResult = mutableMapOf<String, MutableMap<String, MutableList<String>>>()
                if (null == settings.targetElementTypes) {
                    settings.targetElementTypes = arrayListOf()
                }

                val staticCompletionsConfigs = manager.getStaticCompletionConfigs()

                if (!settings.pluginEnabled || null == staticCompletionsConfigs || !settings.resolveReferences) {
                    return fileResult
                }

                val language = inputData.psiFile.language
                var languageString = language.id

                if (language.baseLanguage !== null && !settings.isLanguageSupported(languageString)) {
                    languageString = language.baseLanguage!!.id
                }

                if (!settings.isLanguageSupported(languageString)) {
                    return fileResult
                }

                val projectDir = inputData.project.guessProjectDir() ?: return fileResult

                if (inputData.file.path.indexOf(projectDir.path) < 0) {
                    return fileResult
                }

                if (null !== settings.indexFolder &&
                    "" != settings.indexFolder &&
                    inputData.file.path.indexOf(projectDir.path + "/" + settings.indexFolder!!) < 0
                ) {
                    return fileResult
                }

                val psiDependantFileContent = inputData as PsiDependentFileContent
                val lighterAst = psiDependantFileContent.lighterAST as TreeBackedLighterAST

                val visitor =
                    object : RecursiveLighterASTNodeWalkingVisitor(lighterAst) {
                        override fun visitNode(element: LighterASTNode) {
                            super.visitNode(element)

                            if (element !is LighterASTTokenNode) {
                                return
                            }

                            if (!settings.goToFilter!!.contains(element.tokenType.printToString())) {
                                return
                            }

                            for (staticCompletion in staticCompletionsConfigs) {
                                if (null === staticCompletion.patterns) {
                                    continue
                                }

                                if (staticCompletion.completions == null) {
                                    continue
                                }

                                var matchAnyPattern = false
                                for (pattern in staticCompletion.patterns!!) {
                                    if (PsiElementModelHelper.matches(lighterAst, element, pattern)) {
                                        matchAnyPattern = true

                                        break
                                    }
                                }

                                if (!matchAnyPattern) {
                                    continue
                                }

                                val text = PsiUtils.normalizeElementText(element.text.toString())
                                var filtered =
                                    staticCompletion.completions!!.completions!!.filter {
                                        text == it.text
                                    }
                                if (filtered.isEmpty() && settings.useVelocityInIndex && staticCompletion.matcher != null) {
                                    val context = VelocityContext()
                                    context.put(
                                        "model",
                                        object {
                                            val type = element.tokenType.printToString()
                                            val text = element.text
                                        },
                                    )
                                    val writer = StringWriter()
                                    filtered =
                                        staticCompletion.completions!!.completions!!.filter {
                                            context.put("completion", it)

                                            try {
                                                Velocity.evaluate(context, writer, "", staticCompletion.matcher)
                                            } catch (e: Exception) {
                                                return@filter false
                                            }

                                            val result = writer.buffer.toString()
                                            writer.flush()

                                            result == "true" || result == "1"
                                        }
                                }

                                if (filtered.isEmpty()) {
                                    continue
                                }

                                filtered.filter { null != it.link }.map { it.link!! }.map {
                                    val key = PsiUtils.getLighterASTTokenNodeLink(inputData.file, element)
                                    val psiElement = PsiUtils.processLink(it, "", inputData.project)

                                    if (null != psiElement) {
                                        val elementType = psiElement.elementType.printToString()
                                        if (!settings.targetElementTypes!!.contains(elementType)) {
                                            settings.targetElementTypes!!.add(elementType)
                                        }

                                        val filePath =
                                            psiElement
                                                .getOriginalPsiElement()
                                                .containingFile.virtualFile.url
                                        val elementUrl =
                                            filePath + ":" +
                                                psiElement.getOriginalPsiElement().textOffset

                                        currentResult.put(filePath, mutableMapOf())
                                        if (!currentResult.containsKey(elementUrl)) {
                                            currentResult.put(elementUrl, mutableMapOf())
                                        }

                                        if (!currentResult[elementUrl]!!.containsKey(staticCompletion.name)) {
                                            currentResult[elementUrl]!!.put(
                                                staticCompletion.name,
                                                mutableListOf(),
                                            )
                                        }

                                        currentResult[elementUrl]!![staticCompletion.name]!!.add(key)
                                    }
                                }
                            }
                        }
                    }

                visitor.visitNode(lighterAst.root)

                currentResult.map { (k, v) -> fileResult.put(k, v) }

                if (!DumbService.isDumb(inputData.project)) {
                    DaemonCodeAnalyzer.getInstance(inputData.project).restart(inputData.psiFile)
                }

                return fileResult
            }
        }
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getValueExternalizer(): DataExternalizer<Map<String, List<String>>> = MapDataExternalizer()

    override fun getVersion(): Int = 2

    override fun getInputFilter(): FileBasedIndex.InputFilter {
        return object : FileBasedIndex.InputFilter {
            override fun acceptInput(file: VirtualFile): Boolean {
                return ProjectManager
                    .getInstance()
                    .openProjects
                    .map {
                        val psaManager = it.service<PsaManager>()
                        val settings = psaManager.getSettings()
                        val fileManager = PsiManager.getInstance(it)

                        if (!settings.pluginEnabled) {
                            return false
                        }

                        val psiFile = fileManager.findFile(file) ?: return false
                        val language = psiFile.language
                        var languageString = language.id
                        if (language.baseLanguage !== null && !settings.isLanguageSupported(languageString)) {
                            languageString = language.baseLanguage!!.id
                        }

                        if (!settings.isLanguageSupported(languageString)) {
                            return false
                        }

                        return true
                    }.any { it }
            }
        }
    }

    override fun dependsOnFileContent(): Boolean = true
}
