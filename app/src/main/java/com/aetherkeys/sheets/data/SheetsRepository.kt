package com.aetherkeys.sheets.data

import com.aetherkeys.sheets.model.LibraryData
import com.aetherkeys.sheets.model.Sheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * One ".aek" file per sheet inside [dir] (the existing Sheets folder).
 * A .aek is a zip: format.json + sheet.json. Writes are atomic (temp file, then rename).
 * Nothing is ever deleted except by an explicit delete(id) call from the user.
 * Library data (settings, setlists) lives in ".library.json" in the same folder.
 */
class SheetsRepository(private val dir: File) {
    private val lock = Mutex()

    private fun fileFor(id: String) = File(dir, "$id.aek")
    private val libraryFile get() = File(dir, ".library.json")

    suspend fun listSheets(): List<Sheet> = withContext(Dispatchers.IO) {
        dir.mkdirs()
        dir.listFiles { f -> f.isFile && f.extension == "aek" }.orEmpty()
            .mapNotNull { f -> runCatching { readAek(f) }.getOrNull() }
    }

    suspend fun load(id: String): Sheet? = withContext(Dispatchers.IO) {
        val f = fileFor(id)
        if (f.exists()) runCatching { readAek(f) }.getOrNull() else null
    }

    suspend fun save(sheet: Sheet) = withContext(Dispatchers.IO) {
        lock.withLock {
            dir.mkdirs()
            val tmp = File(dir, ".${sheet.id}.tmp")
            writeAek(tmp, sheet)
            atomicReplace(tmp, fileFor(sheet.id))
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        lock.withLock { fileFor(id).delete() }
        Unit
    }

    suspend fun loadLibrary(): LibraryData = withContext(Dispatchers.IO) {
        if (!libraryFile.exists()) LibraryData()
        else runCatching { SheetJson.decodeLibrary(libraryFile.readText()) }.getOrDefault(LibraryData())
    }

    suspend fun saveLibrary(data: LibraryData) = withContext(Dispatchers.IO) {
        lock.withLock {
            dir.mkdirs()
            val tmp = File(dir, ".library.tmp")
            tmp.writeText(SheetJson.encodeLibrary(data))
            atomicReplace(tmp, libraryFile)
        }
    }

    private fun atomicReplace(tmp: File, target: File) {
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        }
    }

    private fun writeAek(file: File, sheet: Sheet) {
        ZipOutputStream(FileOutputStream(file).buffered()).use { z ->
            z.putNextEntry(ZipEntry("format.json"))
            z.write("""{"format":"aek","version":${SheetJson.VERSION}}""".toByteArray(Charsets.UTF_8))
            z.closeEntry()
            z.putNextEntry(ZipEntry("sheet.json"))
            z.write(SheetJson.encode(sheet).toByteArray(Charsets.UTF_8))
            z.closeEntry()
        }
    }

    private fun readAek(file: File): Sheet? =
        ZipInputStream(file.inputStream().buffered()).use { z ->
            var e = z.nextEntry
            while (e != null) {
                if (e.name == "sheet.json") return@use SheetJson.decode(z.readBytes().toString(Charsets.UTF_8))
                e = z.nextEntry
            }
            null
        }
}
