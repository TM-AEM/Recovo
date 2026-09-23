package com.example.core.database.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Logical folder entity for categorizing audio recordings within Recovo.
 */
@Entity(
    tableName = "folders",
    indices = [
        Index(value = ["name"], unique = true)
    ]
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis()
)
