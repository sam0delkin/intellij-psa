package com.github.sam0delkin.intellijpsa.index

import com.intellij.util.io.DataExternalizer
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException

class MapDataExternalizer : DataExternalizer<Map<String, String>> {
    @Throws(IOException::class)
    override fun save(
        out: DataOutput,
        map: Map<String, String>,
    ) {
        out.writeInt(map.size)
        for ((key, value) in map) {
            out.writeUTF(key)
            out.writeUTF(value)
        }
    }

    @Throws(IOException::class)
    override fun read(`in`: DataInput): Map<String, String> {
        val size: Int = `in`.readInt()
        val map: MutableMap<String, String> = HashMap(size)
        for (i in 0..<size) {
            val key: String = `in`.readUTF()
            val value: String = `in`.readUTF()
            map[key] = value
        }

        return map
    }
}
