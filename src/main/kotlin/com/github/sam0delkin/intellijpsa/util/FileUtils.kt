package com.github.sam0delkin.intellijpsa.util

import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

class FileUtils {
    companion object {
        fun writeToTmpFile(pFilename: String, sb: String): String {
            val tempDir = File(System.getProperty("java.io.tmpdir"))
            val tempFile: File = File.createTempFile(pFilename, ".tmp", tempDir)
            val fileWriter = FileWriter(tempFile, true)
            val bw = BufferedWriter(fileWriter)
            bw.write(sb)
            bw.close()

            return tempFile.absolutePath
        }
    }
}