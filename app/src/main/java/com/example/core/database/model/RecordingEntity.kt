package com.example.core.database.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing an audio recording and its metadata.
 * Note: Audio binary content is stored in the filesystem, NOT in the database.
 */
@Entity(
    tableName = "recordings",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["folderId"]),
        Index(value = ["createdAt"]),
        Index(value = ["displayName"])
    ]
)
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val displayName: String,
    val filePath: String,
    val mimeType: String,
    val format: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val sampleRate: Int,
    val bitRate: Int,
    val channelCount: Int,
    val folderId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis()
)
