package com.github.sam0delkin.intellijpsa.index

import com.intellij.util.io.DataExternalizer
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException

class ListDataExternalizer : DataExternalizer<List<String>> {
    @Throws(IOException::class)
    override fun save(
        out: DataOutput,
        list: List<String>,
    ) {
        out.writeInt(list.size)
        for (value in list) {
            out.writeUTF(value)
        }
    }

    @Throws(IOException::class)
    override fun read(`in`: DataInput): List<String> {
        val size: Int = `in`.readInt()
        val list: MutableList<String> = mutableListOf()
        for (i in 0..<size) {
            val value: String = `in`.readUTF()
            list.add(value)
        }

        return list
    }
}
