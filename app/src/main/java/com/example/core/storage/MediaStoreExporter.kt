package com.example.core.storage

import android.net.Uri
import java.io.File

/**
 * Contract for exporting private recordings to public MediaStore.
 * Note: This is an abstraction reserved for future phases and is currently not active.
 */
interface MediaStoreExporter {
    suspend fun exportToMediaStore(
        sourceFile: File,
        displayName: String,
        mimeType: String
    ): StorageResult<Uri>
}
