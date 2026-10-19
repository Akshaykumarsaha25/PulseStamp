package com.pulsestamp.app.export

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.pulsestamp.app.model.ExportedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** GPX files in Downloads/PulseStamp: save, list, delete, share, hand to Insta360. */
class ExportStore(private val context: Context) {

    private val resolver get() = context.contentResolver

    /** Android 9 writes straight to the public Downloads folder, which needs the storage permission. */
    val needsStoragePermission: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    suspend fun save(fileName: String, bytes: ByteArray): ExportedFile = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveMediaStore(fileName, bytes) else saveLegacy(fileName, bytes)
    }

    private fun saveMediaStore(fileName: String, bytes: ByteArray): ExportedFile {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, MIME)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Android didn't create the file")
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IllegalStateException("Android didn't open the file for writing")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            // Never leave a half-written pending row behind
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return ExportedFile(uri.toString(), fileName, System.currentTimeMillis(), bytes.size.toLong())
    }

    @Suppress("DEPRECATION")
    private fun legacyDir() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)

    private fun saveLegacy(fileName: String, bytes: ByteArray): ExportedFile {
        val dir = legacyDir().apply { mkdirs() }
        val file = File(dir, fileName)
        try {
            file.writeBytes(bytes)
        } catch (e: Exception) {
            file.delete()
            throw e
        }
        return ExportedFile(Uri.fromFile(file).toString(), fileName, file.lastModified(), file.length())
    }

    /** The app's own files, newest first. */
    suspend fun list(): List<ExportedFile> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) listMediaStore() else listLegacy()
    }

    private fun listMediaStore(): List<ExportedFile> {
        val out = mutableListOf<ExportedFile>()
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.DATE_ADDED,
            MediaStore.Downloads.SIZE,
        )
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Downloads.RELATIVE_PATH} LIKE ? AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?",
            arrayOf("${Environment.DIRECTORY_DOWNLOADS}/$FOLDER%", "%.gpx"),
            "${MediaStore.Downloads.DATE_ADDED} DESC",
        )?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val name = c.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
            val added = c.getColumnIndexOrThrow(MediaStore.Downloads.DATE_ADDED)
            val size = c.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
            while (c.moveToNext()) {
                val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(id))
                out += ExportedFile(uri.toString(), c.getString(name), c.getLong(added) * 1000, c.getLong(size))
            }
        }
        return out
    }

    private fun listLegacy(): List<ExportedFile> =
        (legacyDir().listFiles { f -> f.isFile && f.name.endsWith(".gpx") } ?: emptyArray())
            .sortedByDescending { it.lastModified() }
            .map { ExportedFile(Uri.fromFile(it).toString(), it.name, it.lastModified(), it.length()) }

    suspend fun delete(file: ExportedFile): Boolean = withContext(Dispatchers.IO) {
        val uri = Uri.parse(file.uri)
        runCatching {
            if (uri.scheme == "file") File(uri.path!!).delete() else resolver.delete(uri, null, null) > 0
        }.getOrDefault(false)
    }

    fun shareIntent(file: ExportedFile): Intent {
        val uri = Uri.parse(file.uri).let {
            if (it.scheme == "file") FileProvider.getUriForFile(context, "${context.packageName}.files", File(it.path!!)) else it
        }
        val send = Intent(Intent.ACTION_SEND)
            .setType(MIME)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, file.name)
    }

    /** Launch intent for the Insta360 app, or null when it isn't installed. */
    fun insta360Intent(): Intent? = INSTA360_PACKAGES.firstNotNullOfOrNull {
        context.packageManager.getLaunchIntentForPackage(it)
    }

    companion object {
        const val FOLDER = "PulseStamp"
        const val MIME = "application/gpx+xml"
        val INSTA360_PACKAGES = listOf("com.arashivision.insta360akiko", "com.arashivision.insta360one")
    }
}

/** Which workout each exported file came from, kept in app-private storage (backup is off). */
class ExportMarks(context: Context) {
    private val prefs = context.getSharedPreferences("export_marks", Context.MODE_PRIVATE)

    fun all(): Map<String, String> =
        prefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()

    fun mark(workoutId: String, fileName: String) = prefs.edit().putString(workoutId, fileName).apply()

    /** Drop marks whose file no longer exists (deleted here or outside the app). */
    fun keepOnly(existingNames: Set<String>) {
        val stale = all().filterValues { it !in existingNames }.keys
        if (stale.isNotEmpty()) prefs.edit().apply { stale.forEach { remove(it) } }.apply()
    }
}
