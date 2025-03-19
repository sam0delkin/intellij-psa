package com.github.sam0delkin.intellijpsa.index

import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.guessProjectDir
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.KeyDescriptor
import java.io.DataInput
import java.io.DataOutput

class PsaStaticReferenceIndex : FileBasedIndexExtension<String, List<String>>() {
    override fun getName(): ID<String, List<String>> = ID.create("com.github.sam0delkin.intellijpsa.index.PsaStaticReferenceIndex")

    override fun getIndexer(): DataIndexer<String, List<String>, FileContent> {
        return object : DataIndexer<String, List<String>, FileContent> {
            override fun map(inputData: FileContent): MutableMap<String, List<String>> {
                val manager = inputData.project.service<PsaManager>()
                val settings = manager.getSettings()
                val result = mutableMapOf<String, List<String>>()
                val fileResult = mutableListOf<String>()

                if (!settings.pluginEnabled || null == settings.staticCompletionConfigs) {
                    return result
                }

                for (i in settings.staticCompletionConfigs!!) {
                    if (null == i.patterns) {
                        continue
                    }

                    if (i.completions?.completions == null) {
                        continue
                    }

                    for (completion in i.completions.completions!!) {
                        if (null == completion.link) {
                            continue
                        }

                        val link = completion.link!!.split(':')
                        val path = settings.replacePathMappings(link[0])
                        val url = inputData.project.guessProjectDir().toString() + path

                        if (inputData.file.url != url) {
                            continue
                        }

                        fileResult.add(completion.link!!)
                    }
                }

                return result
            }
        }
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> =
        object : KeyDescriptor<String> {
            override fun getHashCode(value: String?): Int = value.hashCode()

            override fun isEqual(
                val1: String?,
                val2: String?,
            ): Boolean = val1 == val2

            override fun save(
                out: DataOutput,
                value: String?,
            ) {
                if (value != null) {
                    out.writeBytes(value)
                }
            }

            override fun read(`in`: DataInput): String = `in`.readLine()
        }

    override fun getValueExternalizer(): DataExternalizer<List<String>> =
        object : DataExternalizer<List<String>> {
            override fun save(
                out: DataOutput,
                value: List<String>?,
            ) {
                if (value != null) {
                    out.writeBytes(value.joinToString(","))
                }
            }

            override fun read(`in`: DataInput): List<String> = `in`.readLine().split(",")
        }

    override fun getVersion(): Int = 1

    override fun getInputFilter(): FileBasedIndex.InputFilter = FileBasedIndex.InputFilter { file -> true }

    override fun dependsOnFileContent(): Boolean = true
}
