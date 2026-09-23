package com.example.core.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class StorageManagerTest {

    private lateinit var context: Context
    private lateinit var testDir: File
    private lateinit var storageManager: StorageManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testDir = File(context.cacheDir, "test_recordings_${System.currentTimeMillis()}")
        testDir.mkdirs()
        storageManager = StorageManager(context = context, baseDirectory = testDir)
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    @Test
    fun sanitizeFileName_removesPathTraversalAndIllegalChars() {
        val dangerous = "../../etc/passwd:test*file?.m4a"
        val sanitized = storageManager.sanitizeFileName(dangerous)
        assertFalse(sanitized.contains(".."))
        assertFalse(sanitized.contains("/"))
        assertFalse(sanitized.contains("\\"))
        assertFalse(sanitized.contains(":"))
        assertFalse(sanitized.contains("*"))
        assertFalse(sanitized.contains("?"))
    }

    @Test
    fun sanitizeFileName_fallbackForEmptyOrBlank() {
        val sanitized = storageManager.sanitizeFileName("   ")
        assertTrue(sanitized.startsWith("REC_"))
    }

    @Test
    fun createRecordingFile_createsPhysicalFileOnDisk() = runTest {
        val result = storageManager.createRecordingFile(desiredName = "Test_Voice", extension = "m4a")
        assertTrue(result is StorageResult.Success)
        val file = (result as StorageResult.Success).data
        assertTrue(file.exists())
        assertTrue(file.isFile)
        assertEquals("Test_Voice.m4a", file.name)
    }

    @Test
    fun createRecordingFile_avoidsCollisionWhenFileExists() = runTest {
        val firstResult = storageManager.createRecordingFile(desiredName = "DuplicateName", extension = "wav")
        assertTrue(firstResult is StorageResult.Success)
        val firstFile = (firstResult as StorageResult.Success).data
        assertEquals("DuplicateName.wav", firstFile.name)

        val secondResult = storageManager.createRecordingFile(desiredName = "DuplicateName", extension = "wav")
        assertTrue(secondResult is StorageResult.Success)
        val secondFile = (secondResult as StorageResult.Success).data
        assertEquals("DuplicateName_1.wav", secondFile.name)
        assertTrue(secondFile.exists())
    }

    @Test
    fun deleteFile_deletesExistingFile() = runTest {
        val fileResult = storageManager.createRecordingFile(desiredName = "ToDelete", extension = "m4a")
        val file = (fileResult as StorageResult.Success).data
        assertTrue(file.exists())

        val deleteResult = storageManager.deleteFile(file.absolutePath)
        assertTrue(deleteResult is StorageResult.Success)
        assertFalse(file.exists())
    }

    @Test
    fun deleteFile_returnsFileNotFoundForMissingFile() = runTest {
        val nonExistentPath = File(testDir, "non_existent.m4a").absolutePath
        val deleteResult = storageManager.deleteFile(nonExistentPath)
        assertTrue(deleteResult is StorageResult.Error)
        val error = deleteResult as StorageResult.Error
        assertEquals(StorageErrorType.FILE_NOT_FOUND, error.errorType)
    }

    @Test
    fun getFileSizeBytes_calculatesLengthWithoutLoadingRam() = runTest {
        val fileResult = storageManager.createRecordingFile(desiredName = "SizedFile", extension = "m4a")
        val file = (fileResult as StorageResult.Success).data
        file.writeBytes(ByteArray(1024)) // 1KB sample payload

        val sizeResult = storageManager.getFileSizeBytes(file.absolutePath)
        assertTrue(sizeResult is StorageResult.Success)
        assertEquals(1024L, (sizeResult as StorageResult.Success).data)
    }

    @Test
    fun listRecordingFiles_returnsAllFiles() = runTest {
        storageManager.createRecordingFile(desiredName = "File1", extension = "m4a")
        storageManager.createRecordingFile(desiredName = "File2", extension = "m4a")

        val listResult = storageManager.listRecordingFiles()
        assertTrue(listResult is StorageResult.Success)
        val files = (listResult as StorageResult.Success).data
        assertEquals(2, files.size)
    }
}
