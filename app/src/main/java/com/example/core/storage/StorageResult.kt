package com.example.core.storage

sealed class StorageResult<out T> {
    data class Success<out T>(val data: T) : StorageResult<T>()
    data class Error(
        val errorType: StorageErrorType,
        val message: String,
        val cause: Throwable? = null
    ) : StorageResult<Nothing>()
}

enum class StorageErrorType {
    FILE_NOT_FOUND,
    INSUFFICIENT_STORAGE,
    INVALID_FILENAME,
    IO_ERROR,
    SECURITY_ERROR,
    UNKNOWN
}
