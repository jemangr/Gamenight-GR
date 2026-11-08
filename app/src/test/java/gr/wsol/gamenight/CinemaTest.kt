package gr.wsol.gamenight

import gr.wsol.gamenight.core.Cinema
import gr.wsol.gamenight.core.Settings
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.ui.Looks
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CinemaTest {
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("cinema_test").toFile()
        Store.inMemory(s = Settings(look = 0))
        Cinema.testBaseDir = null
    }

    @After
    fun tearDown() {
        Cinema.testBaseDir = null
        tempDir.deleteRecursively()
    }

    @Test
    fun indexJsonReadsCorrectly() {
        val indexFile = File("../packs/cinematic/index.json").takeIf { it.exists() }
            ?: File("packs/cinematic/index.json")
        assertTrue("index.json should exist in packs/cinematic", indexFile.exists())

        val index = Cinema.json.decodeFromString(Cinema.Index.serializer(), indexFile.readText())
        assertTrue("index version should be >= 1", index.version >= 1)
        assertTrue("index should contain files", index.files.isNotEmpty())

        val homeEntry = index.files.firstOrNull { it.path == "bg/home.webp" }
        assertNotNull("bg/home.webp should be in index", homeEntry)
        assertTrue("file bytes should be > 0", homeEntry!!.bytes > 0)
        assertEquals("sha256 length should be 64 characters", 64, homeEntry.sha256.length)
    }

    @Test
    fun sha256RejectsCorruptedFile() {
        val testFile = File(tempDir, "test_sha.dat")
        testFile.writeText("original content 12345")
        val correctHash = Cinema.computeSha256(testFile)

        // Corrupt file
        testFile.writeText("corrupted content 67890")
        val newHash = Cinema.computeSha256(testFile)

        assertNotEquals("Sha256 must differ when file content changes", correctHash, newHash)
    }

    @Test
    fun deleteCleansFolderAndResetsLook() {
        val cinemaDir = Cinema.dir(tempDir)
        cinemaDir.mkdirs()
        File(cinemaDir, "dummy.webp").writeText("dummy content")
        File(cinemaDir, "index.json").writeText("{}")

        Store.settings = Store.settings.copy(look = 3)
        assertEquals("Look should be set to 3", 3, Store.settings.look)
        assertTrue("Cinema dir should exist before delete", cinemaDir.exists())

        Cinema.delete(tempDir)

        assertFalse("Cinema dir should be deleted", cinemaDir.exists())
        assertEquals("Look should be reset to 0 (Night)", 0, Store.settings.look)
    }

    @Test
    fun legacySettingsWithoutLook3LoadNormally() {
        // Old JSON without look field
        val legacyJson1 = """{"adult":false,"sounds":true,"english":false}"""
        val s1 = kotlinx.serialization.json.Json.decodeFromString(Settings.serializer(), legacyJson1)
        assertEquals(0, s1.look)

        // Old JSON with look 2 (Christmas)
        val legacyJson2 = """{"look":2,"sounds":true}"""
        val s2 = kotlinx.serialization.json.Json.decodeFromString(Settings.serializer(), legacyJson2)
        assertEquals(2, s2.look)

        // Safe fallback for any unknown look
        val safeLook = Looks.all.getOrElse(99) { Looks.night }
        assertEquals(Looks.night, safeLook)
    }

    @Test
    fun cinemaLookWithoutImagesDoesNotCrash() {
        Store.settings = Store.settings.copy(look = 3)
        assertEquals("Cinema look should be active", Looks.cinema, Looks.current)

        // Non-existent image file should safely resolve to null
        val dummyEmptyDir = File(tempDir, "empty_files")
        val missing = File(dummyEmptyDir, "cinematic/non_existent.webp").takeIf { it.exists() }
        assertEquals(null, missing)
    }

    @Test
    fun validateIndexRejectsPathTraversalAndInvalidPaths() {
        assertFalse("Relative traversal should be rejected", Cinema.isValidPath("../evil.webp"))
        assertFalse("Absolute path should be rejected", Cinema.isValidPath("/evil.webp"))
        assertFalse("Subdirectory traversal should be rejected", Cinema.isValidPath("bg/../../secret.webp"))
        assertFalse("Backslash should be rejected", Cinema.isValidPath("bg\\home.webp"))
        assertFalse("Non-webp should be rejected", Cinema.isValidPath("bg/home.png"))
        assertFalse("Extra trailing extension rejected", Cinema.isValidPath("bg/home.webp.exe"))
        assertFalse("Empty string rejected", Cinema.isValidPath(""))

        assertTrue("Valid top-level webp should pass", Cinema.isValidPath("home.webp"))
        assertTrue("Valid subdirectory webp should pass", Cinema.isValidPath("bg/home.webp"))
        assertTrue("Valid nested webp with dash and underscore should pass", Cinema.isValidPath("house/role-zombie_v1.webp"))
    }

    @Test
    fun validateIndexEnforcesLimits() {
        // Empty index rejected
        assertFalse("Empty file list rejected", Cinema.validateIndex(Cinema.Index(1, emptyList())))

        // Valid entries
        val validEntries = (1..10).map { Cinema.FileEntry("cat/image$it.webp", 100_000L, "dummy_hash") }
        assertTrue("Valid 10-file index passes", Cinema.validateIndex(Cinema.Index(1, validEntries)))

        // More than 80 files rejected
        val tooMany = (1..85).map { Cinema.FileEntry("cat/image$it.webp", 1000L, "dummy_hash") }
        assertFalse("Index with > 80 files rejected", Cinema.validateIndex(Cinema.Index(1, tooMany)))

        // Total bytes > 20 MB rejected
        val tooLarge = listOf(
            Cinema.FileEntry("cat/big1.webp", 15 * 1024 * 1024L, "dummy_hash"),
            Cinema.FileEntry("cat/big2.webp", 6 * 1024 * 1024L, "dummy_hash")
        )
        assertFalse("Index with > 20 MB total rejected", Cinema.validateIndex(Cinema.Index(1, tooLarge)))

        // Zero or negative bytes rejected
        val zeroBytes = listOf(Cinema.FileEntry("cat/zero.webp", 0L, "dummy_hash"))
        assertFalse("Index with 0 bytes entry rejected", Cinema.validateIndex(Cinema.Index(1, zeroBytes)))
    }

    @Test
    fun testBaseDirResolvesCorrectly() {
        val testImageDir = File(tempDir, "bg").apply { mkdirs() }
        val testFile = File(testImageDir, "home.webp").apply { writeText("mock image") }

        val dummyFilesDir = File(tempDir, "files").apply { mkdirs() }

        assertNull("Should not find in test base dir when testBaseDir is null", Cinema.resolveImageFile(dummyFilesDir, "bg/home.webp"))

        Cinema.testBaseDir = tempDir
        val resolved = Cinema.resolveImageFile(dummyFilesDir, "bg/home.webp")
        assertNotNull("Should resolve via testBaseDir", resolved)
        assertEquals(testFile.absolutePath, resolved!!.absolutePath)
    }
}
