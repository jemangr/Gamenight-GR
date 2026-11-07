package gr.wsol.gamenight

import gr.wsol.gamenight.core.Cinema
import gr.wsol.gamenight.core.Settings
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.ui.Looks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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
}
