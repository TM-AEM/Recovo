package com.example.core.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files

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

    @Test
    fun sanitizeFileName_handlesNestedTraversalAndUnicode() {
        val nested = "....//....//passwd"
        val sanitizedNested = storageManager.sanitizeFileName(nested)
        assertFalse(sanitizedNested.contains(".."))
        assertFalse(sanitizedNested.contains("/"))

        val arabicTitle = "تسجيل المقابلة الصوتية"
        val sanitizedArabic = storageManager.sanitizeFileName(arabicTitle)
        assertEquals(arabicTitle, sanitizedArabic)

        val trimmedEdgeChars = "___...VoiceNote...___"
        val sanitizedEdge = storageManager.sanitizeFileName(trimmedEdgeChars)
        assertEquals("VoiceNote", sanitizedEdge)
    }

    @Test
    fun deleteFile_rejectsDeletionOutsideRecordingsDirectory() = runTest {
        val outsideFile = File(context.cacheDir, "outside_file.txt").apply { writeText("sensitive data") }
        val result = storageManager.deleteFile(outsideFile.absolutePath)
        assertTrue(result is StorageResult.Error)
        assertEquals(StorageErrorType.SECURITY_ERROR, (result as StorageResult.Error).errorType)
        assertTrue(outsideFile.exists())
        outsideFile.delete()
    }

    @Test
    fun getFileSizeBytes_rejectsAccessOutsideRecordingsDirectory() = runTest {
        val outsideFile = File(context.cacheDir, "outside_file2.txt").apply { writeText("sensitive data") }
        val result = storageManager.getFileSizeBytes(outsideFile.absolutePath)
        assertTrue(result is StorageResult.Error)
        assertEquals(StorageErrorType.SECURITY_ERROR, (result as StorageResult.Error).errorType)
        outsideFile.delete()
    }

    @Test
    fun cleanOrphanedZeroByteFiles_excludesActiveFileAndPreservesNonzeroFiles() = runTest {
        val activeEmpty = File(testDir, "active_recording.m4a").apply { createNewFile() }
        val orphanEmpty = File(testDir, "orphan_empty.m4a").apply { createNewFile() }
        val validNonzero = File(testDir, "valid_audio.m4a").apply { writeBytes(ByteArray(512)) }

        val cleanResult = storageManager.cleanOrphanedZeroByteFiles(excludeFile = activeEmpty)
        assertTrue(cleanResult is StorageResult.Success)
        assertEquals(1, (cleanResult as StorageResult.Success).data)

        assertTrue("Active empty file must be preserved", activeEmpty.exists())
        assertFalse("Orphaned empty file must be deleted", orphanEmpty.exists())
        assertTrue("Nonzero valid audio file must be preserved", validNonzero.exists())
    }

    // --- Phase 31: storage path-boundary hardening regression coverage ---

    @Test
    fun deleteFile_allowsValidFileDirectlyInsideRecordingsDirectory() = runTest {
        val fileResult = storageManager.createRecordingFile(desiredName = "InsideValid", extension = "m4a")
        val file = (fileResult as StorageResult.Success).data
        file.writeBytes(ByteArray(128))
        assertTrue(file.exists())

        val deleteResult = storageManager.deleteFile(file.absolutePath)
        assertTrue(deleteResult is StorageResult.Success)
        assertFalse(file.exists())
    }

    @Test
    fun deleteFile_returnsFileNotFoundForMissingFileInsideRecordingsDirectory() = runTest {
        val missing = File(testDir, "missing_inside.m4a")
        val result = storageManager.deleteFile(missing.absolutePath)
        assertTrue(result is StorageResult.Error)
        assertEquals(StorageErrorType.FILE_NOT_FOUND, (result as StorageResult.Error).errorType)
    }

    @Test
    fun deleteFile_rejectsUnrelatedFileOutsideRecordingsDirectory() = runTest {
        val outsideFile = File(context.cacheDir, "unrelated_phase31.txt")
            .apply { writeText("do-not-delete") }
        try {
            val result = storageManager.deleteFile(outsideFile.absolutePath)
            assertTrue(result is StorageResult.Error)
            assertEquals(StorageErrorType.SECURITY_ERROR, (result as StorageResult.Error).errorType)
            assertTrue(outsideFile.exists())
            assertEquals("do-not-delete", outsideFile.readText())
        } finally {
            outsideFile.delete()
        }
    }

    @Test
    fun deleteFile_rejectsSiblingDirectorySharingRecordingsPrefix() = runTest {
        // Sibling directory whose name starts with the recordings directory name:
        // "${testDir.name}_sibling". A textual prefix check would wrongly accept it.
        val siblingDir = File(testDir.parentFile, "${testDir.name}_sibling").apply { mkdirs() }
        val siblingFile = File(siblingDir, "sibling_secret.m4a").apply { writeBytes(ByteArray(64)) }
        try {
            val result = storageManager.deleteFile(siblingFile.absolutePath)
            assertTrue(result is StorageResult.Error)
            assertEquals(StorageErrorType.SECURITY_ERROR, (result as StorageResult.Error).errorType)
            assertTrue("Sibling file must be preserved", siblingFile.exists())
            assertEquals(64L, siblingFile.length())
        } finally {
            siblingDir.deleteRecursively()
        }
    }

    @Test
    fun deleteFile_rejectsSiblingPathWithoutAlteringContents() = runTest {
        val siblingDir = File(testDir.parentFile, "${testDir.name}_sibling").apply { mkdirs() }
        val siblingFile = File(siblingDir, "content.m4a")
        val original = ByteArray(256) { it.toByte() }
        siblingFile.writeBytes(original)
        try {
            storageManager.deleteFile(siblingFile.absolutePath)
            assertTrue(siblingFile.exists())
            assertArrayEquals(original, siblingFile.readBytes())
        } finally {
            siblingDir.deleteRecursively()
        }
    }

    @Test
    fun getFileSizeBytes_rejectsSiblingDirectorySharingRecordingsPrefix() = runTest {
        val siblingDir = File(testDir.parentFile, "${testDir.name}_sibling").apply { mkdirs() }
        val siblingFile = File(siblingDir, "sibling_size.m4a").apply { writeBytes(ByteArray(321)) }
        try {
            val result = storageManager.getFileSizeBytes(siblingFile.absolutePath)
            assertTrue(result is StorageResult.Error)
            assertEquals(StorageErrorType.SECURITY_ERROR, (result as StorageResult.Error).errorType)
            assertTrue("Sibling file must be preserved", siblingFile.exists())
        } finally {
            siblingDir.deleteRecursively()
        }
    }

    @Test
    fun deleteFile_rejectsSymlinkInsideRecordingsPointingOutside() = runTest {
        val outsideTarget = File(context.cacheDir, "symlink_outside_target_phase31.txt")
            .apply { writeText("outside-content") }
        val link = File(testDir, "escaping_link.txt")
        val symlinkSupported = try {
            Files.createSymbolicLink(link.toPath(), outsideTarget.toPath())
            true
        } catch (e: Exception) {
            // Some sandboxed test filesystems disallow symlink creation; documented limitation.
            false
        }
        assumeTrue("Symlinks unsupported in this environment", symlinkSupported)
        try {
            val deleteResult = storageManager.deleteFile(link.absolutePath)
            assertTrue(deleteResult is StorageResult.Error)
            assertEquals(StorageErrorType.SECURITY_ERROR, (deleteResult as StorageResult.Error).errorType)
            assertTrue("Symlink target outside recordings must be preserved", outsideTarget.exists())
            assertEquals("outside-content", outsideTarget.readText())

            val sizeResult = storageManager.getFileSizeBytes(link.absolutePath)
            assertTrue(sizeResult is StorageResult.Error)
            assertEquals(StorageErrorType.SECURITY_ERROR, (sizeResult as StorageResult.Error).errorType)
        } finally {
            link.delete()
            outsideTarget.delete()
        }
    }
}
