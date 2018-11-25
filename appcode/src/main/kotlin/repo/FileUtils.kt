package repo

import java.io.File
import java.util.ArrayList
import java.nio.file.Paths
import java.nio.file.Files
import java.io.IOException
import java.nio.charset.Charset


open class FileUtils {
    @Throws(IOException::class)
    fun readAllText(filename: String): String {
        val encoded = Files.readAllBytes(Paths.get(filename))
        return String(encoded, Charset.defaultCharset())
    }

    fun listFiles(path: String): Array<String> {
        val files = ArrayList<String>()
        for (fileEntry in File(path).listFiles()!!) {
            files.add(fileEntry.name)
        }
        return files.toTypedArray()
    }

    fun fileExists(filePath: String): Boolean {
        val file = File(filePath)
        return file.exists()
    }

    fun isFolder(folderPath: String): Boolean {
        val file = File(folderPath)
        return file.isDirectory && file.exists()
    }
}
