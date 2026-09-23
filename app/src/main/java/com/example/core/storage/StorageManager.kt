package com.example.core.storage

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Storage manager for managing audio recordings on the local filesystem.
 * Uses App-Specific internal storage (context.filesDir/recordings) which:
 * - Requires zero runtime storage permissions on Android 11+ (API 30+).
 * - Is completely scoped and sandboxed.
 * - Prevents tampering and unauthenticated access from other apps.
 */
class StorageManager(
    private val context: Context,
    private val baseDirectory: File = context.filesDir.resolve(RECORDINGS_DIR),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        const val RECORDINGS_DIR = "recordings"
        private const val MIN_REQUIRED_STORAGE_BYTES = 10L * 1024L * 1024L // 10 MB minimum buffer
        private const val MAX_FILENAME_LENGTH = 80
        private val ILLEGAL_FILENAME_CHARS_REGEX = Regex("[/\\\\:*?\"<>|\\x00-\\x1F]")
    }

    init {
        ensureDirectoryExists()
    }

    private fun ensureDirectoryExists(): Boolean {
        return if (!baseDirectory.exists()) {
            baseDirectory.mkdirs()
        } else {
            true
        }
    }

    /**
     * Sanitizes a user-provided filename by removing path traversal characters,
     * slashes, colons, and illegal filesystem characters.
     */
    fun sanitizeFileName(rawName: String): String {
        var sanitized = rawName.replace("..", "")
            .replace(ILLEGAL_FILENAME_CHARS_REGEX, "_")
            .trim()

        if (sanitized.length > MAX_FILENAME_LENGTH) {
            sanitized = sanitized.substring(0, MAX_FILENAME_LENGTH).trim()
        }

        if (sanitized.isEmpty() || sanitized == "_" || sanitized == ".") {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            sanitized = "REC_$timestamp"
        }
        return sanitized
    }

    /**
     * Creates and allocates a new audio recording file safely.
     * Prevents overwriting existing files by appending incremental indexes.
     */
    suspend fun createRecordingFile(
        desiredName: String? = null,
        extension: String = "m4a"
    ): StorageResult<File> = withContext(ioDispatcher) {
        try {
            if (!ensureDirectoryExists()) {
                return@withContext StorageResult.Error(
                    errorType = StorageErrorType.IO_ERROR,
                    message = "Could not create recordings directory"
                )
            }

            val availableBytes = getAvailableStorageBytes()
            if (availableBytes in 1 until MIN_REQUIRED_STORAGE_BYTES) {
                return@withContext StorageResult.Error(
                    errorType = StorageErrorType.INSUFFICIENT_STORAGE,
                    message = "Insufficient storage space ($availableBytes bytes available)"
                )
            }

            val normalizedExt = extension.removePrefix(".").trim().lowercase(Locale.US)
            if (normalizedExt.isEmpty() || normalizedExt.contains("/") || normalizedExt.contains("\\")) {
                return@withContext StorageResult.Error(
                    errorType = StorageErrorType.INVALID_FILENAME,
                    message = "Invalid audio file extension: $extension"
                )
            }

            val baseName = if (desiredName.isNullOrBlank()) {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                "REC_$timestamp"
            } else {
                sanitizeFileName(desiredName)
            }

            var candidate = File(baseDirectory, "$baseName.$normalizedExt")
            var counter = 1
            while (candidate.exists()) {
                candidate = File(baseDirectory, "${baseName}_$counter.$normalizedExt")
                counter++
            }

            // Create new empty file to lock filename safely
            val created = candidate.createNewFile()
            if (!created && !candidate.exists()) {
                return@withContext StorageResult.Error(
                    errorType = StorageErrorType.IO_ERROR,
                    message = "Failed to allocate file on disk: ${candidate.name}"
                )
            }

            StorageResult.Success(candidate)
        } catch (e: SecurityException) {
            StorageResult.Error(
                errorType = StorageErrorType.SECURITY_ERROR,
                message = "Security exception creating file",
                cause = e
            )
        } catch (e: IOException) {
            StorageResult.Error(
                errorType = StorageErrorType.IO_ERROR,
                message = "IO exception creating recording file",
                cause = e
            )
        } catch (e: Exception) {
            StorageResult.Error(
                errorType = StorageErrorType.UNKNOWN,
                message = "Unexpected error creating file",
                cause = e
            )
        }
    }

    /**
     * Checks if a file exists given its path.
     */
    suspend fun fileExists(filePath: String): Boolean = withContext(ioDispatcher) {
        val file = File(filePath)
        file.exists() && file.isFile
    }

    /**
     * Deletes a recording file from disk.
     */
    suspend fun deleteFile(filePath: String): StorageResult<Boolean> = withContext(ioDispatcher) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                return@withContext StorageResult.Error(
                    errorType = StorageErrorType.FILE_NOT_FOUND,
                    message = "File does not exist: $filePath"
                )
            }

            val deleted = file.delete()
            if (deleted) {
                StorageResult.Success(true)
            } else {
                StorageResult.Error(
                    errorType = StorageErrorType.IO_ERROR,
                    message = "Failed to delete file: $filePath"
                )
            }
        } catch (e: SecurityException) {
            StorageResult.Error(
                errorType = StorageErrorType.SECURITY_ERROR,
                message = "Security exception deleting file: $filePath",
                cause = e
            )
        } catch (e: Exception) {
            StorageResult.Error(
                errorType = StorageErrorType.UNKNOWN,
                message = "Unexpected error deleting file: $filePath",
                cause = e
            )
        }
    }

    /**
     * Gets file size in bytes without loading any audio bytes into RAM.
     */
    suspend fun getFileSizeBytes(filePath: String): StorageResult<Long> = withContext(ioDispatcher) {
        val file = File(filePath)
        if (!file.exists()) {
            StorageResult.Error(
                errorType = StorageErrorType.FILE_NOT_FOUND,
                message = "File does not exist: $filePath"
            )
        } else {
            StorageResult.Success(file.length())
        }
    }

    /**
     * Returns available storage bytes in the filesystem hosting the recordings.
     */
    fun getAvailableStorageBytes(): Long {
        return try {
            val usable = baseDirectory.usableSpace
            if (usable > 0L) {
                usable
            } else {
                val stat = StatFs(baseDirectory.path)
                val bytes = stat.availableBytes
                if (bytes > 0L) bytes else Long.MAX_VALUE
            }
        } catch (e: Exception) {
            Long.MAX_VALUE
        }
    }

    /**
     * Lists all audio recording files in the recordings directory without loading content into RAM.
     */
    suspend fun listRecordingFiles(): StorageResult<List<File>> = withContext(ioDispatcher) {
        try {
            if (!ensureDirectoryExists()) {
                return@withContext StorageResult.Success(emptyList())
            }
            val files = baseDirectory.listFiles { file -> file.isFile }?.toList() ?: emptyList()
            StorageResult.Success(files)
        } catch (e: Exception) {
            StorageResult.Error(
                errorType = StorageErrorType.IO_ERROR,
                message = "Error listing recording files",
                cause = e
            )
        }
    }

    fun getRecordingsDirectory(): File = baseDirectory
}
