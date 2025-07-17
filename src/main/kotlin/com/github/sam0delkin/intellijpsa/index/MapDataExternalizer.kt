package com.github.sam0delkin.intellijpsa.index

import com.intellij.util.io.DataExternalizer
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException

class MapDataExternalizer : DataExternalizer<Map<String, List<String>>> {
    @Throws(IOException::class)
    override fun save(
        out: DataOutput,
        map: Map<String, List<String>>,
    ) {
        out.writeInt(map.size)
        for ((key, list) in map) {
            out.writeUTF(key)
            out.writeInt(list.size)
            for (value in list) {
                out.writeUTF(value)
            }
        }
    }

    @Throws(IOException::class)
    override fun read(`in`: DataInput): Map<String, List<String>> {
        val mapSize = `in`.readInt()
        val map = mutableMapOf<String, List<String>>()

        for (i in 0..<mapSize) {
            val key = `in`.readUTF()
            val listSize = `in`.readInt()
            val list = mutableListOf<String>()

            for (j in 0..<listSize) {
                list.add(`in`.readUTF())
            }

            map[key] = list
        }

        return map
    }
}
