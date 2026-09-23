package com.example.core.database.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Cross-reference table for many-to-many relationship between Recordings and Tags.
 */
@Entity(
    tableName = "recording_tag_cross_ref",
    primaryKeys = ["recordingId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = RecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordingId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["recordingId"]),
        Index(value = ["tagId"])
    ]
)
data class RecordingTagCrossRef(
    val recordingId: Long,
    val tagId: Long
)
