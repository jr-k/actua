package com.azimulkabir.actua.data.budget

import android.content.Context
import androidx.annotation.StringRes
import com.azimulkabir.actua.data.preferences.withAppLanguage
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

sealed class BudgetFileException(message: String) : Exception(message) {
    data object InvalidArchive : BudgetFileException("The budget is not a valid ZIP archive")
    data object MissingDatabase : BudgetFileException("The budget archive is missing db.sqlite")
    data object MissingMetadata : BudgetFileException("The budget archive is missing metadata.json")
    data object InvalidMetadata : BudgetFileException("The budget metadata is invalid")
    data object IncompatibleBudget : BudgetFileException("This backup belongs to a different budget")
    class InvalidBudgetData(reason: String) : BudgetFileException(reason)
    class UnsafeArchive(reason: String) : BudgetFileException("Unsafe budget archive: $reason")
}

/**
 * Owns Actual-compatible budget directories and ZIP archives. It deliberately
 * does not know about Compose or the prototype database.
 */
class BudgetFileManager(context: Context) {
    private val root = File(context.applicationContext.filesDir, "Budgets")
    private val localizedContext = context.applicationContext.withAppLanguage()

    init {
        check(root.mkdirs() || root.isDirectory) { "Unable to create the budgets directory" }
    }

    fun budgetDirectory(budgetId: String): File = File(root, safeBudgetId(budgetId))
    fun databaseFile(budgetId: String): File = File(budgetDirectory(budgetId), DATABASE_NAME)
    fun metadataFile(budgetId: String): File = File(budgetDirectory(budgetId), METADATA_NAME)
    internal fun localizedString(@StringRes id: Int): String = localizedContext.getString(id)
    fun backupsDirectory(budgetId: String): File = File(budgetDirectory(budgetId), "backups").also {
        check(it.mkdirs() || it.isDirectory) { "Unable to create the backups directory" }
    }
    fun latestDatabaseFile(budgetId: String): File = File(budgetDirectory(budgetId), "db.latest.sqlite")
    fun latestMetadataFile(budgetId: String): File = File(budgetDirectory(budgetId), "metadata.latest.json")

    fun createBudget(name: String): BudgetMetadata {
        val sanitized = name.map { if (it.isLetterOrDigit() && it.code < 128) it else '-' }.joinToString("")
        val base = "$sanitized-${UUID.randomUUID().toString().take(7)}"
        var id = base
        var suffix = 0
        while (budgetDirectory(id).exists()) id = "$base${++suffix}"
        val directory = budgetDirectory(id)
        try {
            check(directory.mkdirs()) { "Unable to create budget directory" }
            BlankBudgetFactory.create(databaseFile(id))
            metadataFile(id).writeText(JSONObject().put("id", id).put("budgetName", name).toString())
            return BudgetMetadata.fromJson(JSONObject(metadataFile(id).readText()))
        } catch (error: Exception) {
            directory.deleteRecursively()
            throw error
        }
    }

    fun uploadArchive(budgetId: String): ByteArray {
        val original = JSONObject(metadataFile(budgetId).readText())
        val temporary = File.createTempFile("actua-upload-", ".json", root)
        return try {
            temporary.writeText(original.put("resetClock", true).toString())
            ByteArrayOutputStream().use { output ->
                ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                    zip.writeFile(DATABASE_NAME, databaseFile(budgetId))
                    zip.writeFile(METADATA_NAME, temporary)
                }
                output.toByteArray()
            }
        } finally {
            temporary.delete()
        }
    }

    fun saveCloudRegistration(budgetId: String, cloudFileId: String, groupId: String) {
        val file = metadataFile(budgetId)
        val json = JSONObject(file.readText())
            .put("cloudFileId", cloudFileId)
            .put("groupId", groupId)
            .put("lastUploaded", java.time.LocalDate.now().toString())
        file.writeText(json.toString())
    }

    fun deleteBudget(budgetId: String) {
        val directory = budgetDirectory(budgetId)
        require(directory.isDirectory) { "Budget no longer exists on this device" }
        check(directory.deleteRecursively()) { "Unable to remove budget from this device" }
    }

    fun listLocalBudgets(): List<BudgetMetadata> = root.listFiles()
        .orEmpty()
        .asSequence()
        .filter(File::isDirectory)
        .mapNotNull { directory ->
            runCatching {
                BudgetMetadata.fromJson(JSONObject(File(directory, METADATA_NAME).readText()))
            }.getOrNull()
        }
        .sortedBy { it.budgetName?.lowercase() ?: it.id }
        .toList()

    /**
     * Imports the same two-root-entry archive Actual and Actuali use. Cloud
     * identity is supplied by the server response, while unknown metadata keys
     * are preserved verbatim.
     */
    fun importBudget(
        archive: InputStream,
        cloudFileId: String? = null,
        groupId: String? = null,
    ): BudgetMetadata {
        val staging = File(root, ".import-${UUID.randomUUID()}")
        check(staging.mkdirs()) { "Unable to create import staging directory" }
        try {
            val extracted = extractArchive(archive, staging)
            ActualBudgetDatabase.validate(extracted.database)
            BudgetOpenProbe.validate(extracted.database)

            val json = JSONObject(extracted.metadata.readText())
            val original = BudgetMetadata.fromJson(json)
            if (cloudFileId != null) json.put("cloudFileId", cloudFileId)
            if (groupId != null) json.put("groupId", groupId)
            extracted.metadata.writeText(json.toString())
            val updated = BudgetMetadata.fromJson(json)

            installLiveFiles(original.id, extracted)
            return updated
        } finally {
            if (staging.exists()) staging.deleteRecursively()
        }
    }

    fun writeArchive(budgetId: String, output: OutputStream) {
        val database = databaseFile(budgetId)
        val metadata = metadataFile(budgetId)
        require(database.isFile) { "Budget database does not exist" }
        require(metadata.isFile) { "Budget metadata does not exist" }
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            zip.writeFile(DATABASE_NAME, database)
            zip.writeFile(METADATA_NAME, metadata)
        }
    }

    internal fun writeArchive(database: File, metadata: File, destination: File) {
        require(database.isFile && metadata.isFile)
        val temporary = File(destination.parentFile, "${destination.name}.tmp")
        temporary.delete()
        try {
            FileOutputStream(temporary).use { output ->
                ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                    zip.writeFile(DATABASE_NAME, database)
                    zip.writeFile(METADATA_NAME, metadata)
                }
            }
            destination.delete()
            check(temporary.renameTo(destination)) { "Unable to install backup archive" }
        } finally {
            temporary.delete()
        }
    }

    internal fun extractBackup(archive: File): Pair<File, BudgetMetadata> {
        require(archive.isFile)
        val staging = File(root, ".restore-${UUID.randomUUID()}")
        check(staging.mkdirs())
        try {
            val extracted = FileInputStream(archive).use { extractArchive(it, staging) }
            ActualBudgetDatabase.validate(extracted.database)
            val metadata = BudgetMetadata.fromJson(JSONObject(extracted.metadata.readText()))
            val detached = File.createTempFile("actua-restore-", ".sqlite", root)
            check(extracted.database.renameTo(detached))
            return detached to metadata
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun installLiveFiles(budgetId: String, extracted: ExtractedBudget) {
        val destination = budgetDirectory(budgetId)
        check(destination.mkdirs() || destination.isDirectory) { "Unable to create the budget directory" }

        latestDatabaseFile(budgetId).delete()
        latestMetadataFile(budgetId).delete()
        File(databaseFile(budgetId).path + "-wal").delete()
        File(databaseFile(budgetId).path + "-shm").delete()

        val oldDatabase = File(destination, ".db.previous-${UUID.randomUUID()}")
        val oldMetadata = File(destination, ".metadata.previous-${UUID.randomUUID()}")
        val liveDatabase = databaseFile(budgetId)
        val liveMetadata = metadataFile(budgetId)
        try {
            if (liveDatabase.exists()) check(liveDatabase.renameTo(oldDatabase))
            if (liveMetadata.exists()) check(liveMetadata.renameTo(oldMetadata))
            check(extracted.database.renameTo(liveDatabase)) { "Unable to install db.sqlite" }
            check(extracted.metadata.renameTo(liveMetadata)) { "Unable to install metadata.json" }
            oldDatabase.delete()
            oldMetadata.delete()
        } catch (error: Exception) {
            liveDatabase.delete()
            liveMetadata.delete()
            if (oldDatabase.exists()) oldDatabase.renameTo(liveDatabase)
            if (oldMetadata.exists()) oldMetadata.renameTo(liveMetadata)
            throw error
        }
    }

    private fun extractArchive(input: InputStream, staging: File): ExtractedBudget {
        var database: File? = null
        var metadata: File? = null
        val seen = mutableSetOf<String>()
        val requiredEntries = mutableSetOf<String>()
        var totalBytes = 0L
        try {
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    BudgetArchivePolicy.validateEntryName(entry.name, entry.size)
                    if (!seen.add(entry.name.lowercase())) {
                        throw BudgetFileException.UnsafeArchive("duplicate entry ${entry.name}")
                    }
                    if (entry.isDirectory) continue
                    val baseName = entry.name.substringAfterLast('/')
                    if (baseName in setOf(DATABASE_NAME, METADATA_NAME) && !requiredEntries.add(baseName)) {
                        throw BudgetFileException.UnsafeArchive("duplicate entry $baseName")
                    }
                    val target = when (baseName) {
                        DATABASE_NAME -> File(staging, DATABASE_NAME).also { database = it }
                        METADATA_NAME -> File(staging, METADATA_NAME).also { metadata = it }
                        else -> null
                    }
                    if (target != null) {
                        FileOutputStream(target).use { output ->
                            totalBytes = copyCapped(zip, output, totalBytes)
                        }
                    } else {
                        totalBytes = copyCapped(zip, DISCARD_OUTPUT, totalBytes)
                    }
                    zip.closeEntry()
                }
            }
        } catch (error: BudgetFileException) {
            throw error
        } catch (error: Exception) {
            throw BudgetFileException.InvalidArchive
        }
        return ExtractedBudget(
            database = database ?: throw BudgetFileException.MissingDatabase,
            metadata = metadata ?: throw BudgetFileException.MissingMetadata,
        )
    }

    private fun copyCapped(input: InputStream, output: OutputStream, initial: Long): Long {
        var total = initial
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > BudgetArchivePolicy.MAX_ARCHIVE_BYTES) {
                throw BudgetFileException.UnsafeArchive("uncompressed data exceeds 500 MB")
            }
            output.write(buffer, 0, count)
        }
        return total
    }

    private fun safeBudgetId(id: String): String {
        require(id.isNotBlank() && id != "." && id != ".." && '/' !in id && '\\' !in id) {
            "Invalid budget id"
        }
        return id
    }

    private fun ZipOutputStream.writeFile(name: String, file: File) {
        putNextEntry(ZipEntry(name))
        FileInputStream(file).use { it.copyTo(this) }
        closeEntry()
    }

    private data class ExtractedBudget(val database: File, val metadata: File)

    companion object {
        private const val DATABASE_NAME = "db.sqlite"
        private const val METADATA_NAME = "metadata.json"
        private val DISCARD_OUTPUT = object : OutputStream() {
            override fun write(value: Int) = Unit
            override fun write(buffer: ByteArray, offset: Int, length: Int) = Unit
        }
    }
}
