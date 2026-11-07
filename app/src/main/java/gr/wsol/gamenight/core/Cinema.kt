package gr.wsol.gamenight.core

import android.content.Context
import gr.wsol.gamenight.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 2.0: Cinema look image pack management.
 * Packs are downloaded on demand from GitHub into [c.filesDir]/cinematic/.
 * Each file's sha256 and size are strictly verified against index.json.
 */
object Cinema {
    const val REPO_RAW = "https://raw.githubusercontent.com/jemangr/Gamenight-GR"
    val BASE_URL get() = "$REPO_RAW/${BuildConfig.PACK_BRANCH}/packs/cinematic"
    val INDEX_URL get() = "$BASE_URL/index.json"

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    data class FileEntry(val path: String, val bytes: Long, val sha256: String)

    @Serializable
    data class Index(val version: Int = 1, val files: List<FileEntry> = emptyList())

    fun dir(baseDir: File): File = File(baseDir, "cinematic").apply { mkdirs() }
    fun dir(c: Context): File = dir(c.filesDir)

    fun localIndexFile(baseDir: File): File = File(baseDir, "cinematic/index.json")
    fun localIndexFile(c: Context): File = localIndexFile(c.filesDir)

    fun readLocalIndex(baseDir: File): Index? {
        val f = localIndexFile(baseDir)
        if (!f.exists()) return null
        return runCatching { json.decodeFromString(Index.serializer(), f.readText()) }.getOrNull()
    }
    fun readLocalIndex(c: Context): Index? = readLocalIndex(c.filesDir)

    fun isInstalled(baseDir: File): Boolean {
        val idx = readLocalIndex(baseDir) ?: return false
        if (idx.files.isEmpty()) return false
        val base = dir(baseDir)
        return idx.files.all { File(base, it.path).exists() }
    }
    fun isInstalled(c: Context): Boolean = isInstalled(c.filesDir)

    fun installedSize(baseDir: File): Long {
        val base = dir(baseDir)
        if (!base.exists()) return 0L
        return base.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
    fun installedSize(c: Context): Long = installedSize(c.filesDir)

    fun delete(baseDir: File) {
        val base = dir(baseDir)
        if (base.exists()) {
            base.deleteRecursively()
        }
        if (Store.settings.look == 3) {
            Store.settings = Store.settings.copy(look = 0)
        }
    }
    fun delete(c: Context) = delete(c.filesDir)

    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            var r: Int
            while (input.read(buf).also { r = it } != -1) {
                digest.update(buf, 0, r)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun resolveImageFile(c: Context, path: String): File? {
        val f = File(c.filesDir, "cinematic/$path")
        if (f.exists()) return f
        val dev1 = File("../packs/cinematic/$path")
        if (dev1.exists()) return dev1
        val dev2 = File("packs/cinematic/$path")
        if (dev2.exists()) return dev2
        return null
    }

    suspend fun fetchIndex(): Index? = withContext(Dispatchers.IO) {
        runCatching {
            val text = get(INDEX_URL)
            json.decodeFromString(Index.serializer(), text)
        }.getOrNull()
    }

    suspend fun hasUpdate(c: Context): Boolean = withContext(Dispatchers.IO) {
        val local = readLocalIndex(c) ?: return@withContext false
        val remote = fetchIndex() ?: return@withContext false
        remote.version > local.version
    }

    suspend fun download(c: Context, onProgress: (done: Int, total: Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val remoteIndex = fetchIndex() ?: return@withContext false
        if (remoteIndex.files.isEmpty()) return@withContext false

        val tmpDir = File(c.filesDir, "cinematic_tmp")
        if (tmpDir.exists()) tmpDir.deleteRecursively()
        tmpDir.mkdirs()

        try {
            val total = remoteIndex.files.size
            remoteIndex.files.forEachIndexed { i, entry ->
                val targetFile = File(tmpDir, entry.path)
                targetFile.parentFile?.mkdirs()

                val url = "$BASE_URL/${entry.path}"
                downloadToFile(url, targetFile)

                if (targetFile.length() != entry.bytes) {
                    error("Size mismatch for ${entry.path}: expected ${entry.bytes}, got ${targetFile.length()}")
                }

                val sha = computeSha256(targetFile)
                if (!sha.equals(entry.sha256, ignoreCase = true)) {
                    error("SHA-256 mismatch for ${entry.path}: expected ${entry.sha256}, got $sha")
                }

                onProgress(i + 1, total)
            }

            File(tmpDir, "index.json").writeText(json.encodeToString(Index.serializer(), remoteIndex))

            val targetDir = dir(c)
            if (targetDir.exists()) targetDir.deleteRecursively()
            if (!tmpDir.renameTo(targetDir)) {
                tmpDir.copyRecursively(targetDir, overwrite = true)
                tmpDir.deleteRecursively()
            }
            true
        } catch (_: Throwable) {
            tmpDir.deleteRecursively()
            false
        }
    }

    private fun get(urlStr: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadToFile(urlStr: String, file: File) {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            conn.inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
        } finally {
            conn.disconnect()
        }
    }
}
