package com.example.timetable.data

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.ln
import kotlin.math.pow

data class CacheClearResult(
    val bytesCleared: Long = 0L,
    val deletedEntryCount: Int = 0,
)

object AppCacheManager {
    fun clearAppCaches(context: Context): CacheClearResult {
        return sequenceOf(
            context.cacheDir,
            context.codeCacheDir,
            context.externalCacheDir,
        )
            .filterNotNull()
            .distinctBy { it.absolutePath }
            .fold(CacheClearResult()) { total, directory ->
                total + clearDirectoryContents(directory)
            }
    }

    fun formatBytes(bytes: Long): String {
        val safeBytes = bytes.coerceAtLeast(0L)
        if (safeBytes < 1024L) return "$safeBytes B"

        val units = listOf("KB", "MB", "GB", "TB")
        val digitGroup = (ln(safeBytes.toDouble()) / ln(1024.0)).toInt().coerceIn(1, units.size)
        val unitValue = 1024.0.pow(digitGroup.toDouble())
        val formatted = safeBytes / unitValue
        return String.format(java.util.Locale.US, "%.1f %s", formatted, units[digitGroup - 1])
    }

    internal fun clearDirectoryContents(directory: File): CacheClearResult {
        if (!directory.exists() || !directory.isDirectory) {
            return CacheClearResult()
        }

        if (Files.isSymbolicLink(directory.toPath())) {
            return CacheClearResult()
        }

        val rootPath = directory.safeCanonicalPath() ?: return CacheClearResult()

        return directory.listFiles().orEmpty()
            .fold(CacheClearResult()) { total, child ->
                total + deletePathRecursively(child, rootPath)
            }
    }

    private fun deletePathRecursively(path: File, rootPath: Path): CacheClearResult {
        if (!path.exists()) {
            return CacheClearResult()
        }

        val filePath = path.toPath()
        if (Files.isSymbolicLink(filePath)) {
            return CacheClearResult()
        }

        val canonicalPath = path.safeCanonicalPath() ?: return CacheClearResult()
        if (!canonicalPath.startsWith(rootPath)) {
            return CacheClearResult()
        }

        if (path.isDirectory) {
            val childResult = path.listFiles().orEmpty()
                .fold(CacheClearResult()) { total, child ->
                    total + deletePathRecursively(child, rootPath)
                }
            return if (path.delete()) {
                childResult.copy(deletedEntryCount = childResult.deletedEntryCount + 1)
            } else {
                childResult
            }
        }

        val fileSize = path.length().coerceAtLeast(0L)
        return if (path.delete()) {
            CacheClearResult(bytesCleared = fileSize, deletedEntryCount = 1)
        } else {
            CacheClearResult()
        }
    }
}

private fun File.safeCanonicalPath(): Path? {
    return runCatching { canonicalFile.toPath() }.getOrNull()
}

private operator fun CacheClearResult.plus(other: CacheClearResult): CacheClearResult {
    return CacheClearResult(
        bytesCleared = this.bytesCleared + other.bytesCleared,
        deletedEntryCount = this.deletedEntryCount + other.deletedEntryCount,
    )
}
