package com.azimulkabir.actua.data.budget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

data class BackupDestinationState(
    val uri: Uri?,
    val name: String?,
    val lastMirroredMillis: Long,
    val error: String?,
)

enum class BackupDestinationError(val persistedValue: String) {
    CANNOT_CREATE_DIRECTORY("cannot_create_directory"),
    CANNOT_CREATE_BACKUP_FILE("cannot_create_backup_file"),
    CANNOT_WRITE_BACKUP("cannot_write_backup"),
}

/** Mirrors private backups to a user-selected Storage Access Framework folder. */
class BackupDestinationManager(context: Context) {
    private val app = context.applicationContext
    private val resolver = app.contentResolver
    private val preferences = app.getSharedPreferences("actua-backup-destination", Context.MODE_PRIVATE)

    fun read(): BackupDestinationState = BackupDestinationState(
        preferences.getString("uri", null)?.let(Uri::parse),
        preferences.getString("name", null),
        preferences.getLong("lastMirrored", 0),
        preferences.getString("error", null),
    )

    fun select(uri: Uri, displayName: String?) {
        resolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        preferences.edit().putString("uri", uri.toString())
            .apply {
                val name = displayName ?: documentName(uri)
                if (name == null) remove("name") else putString("name", name)
            }
            .remove("error").apply()
    }

    fun reset() {
        read().uri?.let { runCatching { resolver.releasePersistableUriPermission(
            it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        ) } }
        preferences.edit().clear().apply()
    }

    fun mirror(budgetId: String, archive: File) {
        val tree = read().uri ?: return
        runCatching {
            val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val appDirectory = child(root, "Actua") ?: createDirectory(root, "Actua")
            val budgetDirectory = child(appDirectory, budgetId) ?: createDirectory(appDirectory, budgetId)
            child(budgetDirectory, archive.name)?.let { DocumentsContract.deleteDocument(resolver, it) }
            val target = DocumentsContract.createDocument(resolver, budgetDirectory, "application/zip", archive.name)
                ?: throw BackupDestinationException(BackupDestinationError.CANNOT_CREATE_BACKUP_FILE)
            val output = resolver.openOutputStream(target, "w")
                ?: throw BackupDestinationException(BackupDestinationError.CANNOT_WRITE_BACKUP)
            output.use { stream -> archive.inputStream().use { it.copyTo(stream) } }
        }.onSuccess {
            preferences.edit().putLong("lastMirrored", System.currentTimeMillis()).remove("error").apply()
        }.onFailure { error ->
            val code = (error as? BackupDestinationException)?.code
                ?: BackupDestinationError.CANNOT_WRITE_BACKUP
            preferences.edit().putString("error", code.persistedValue).apply()
            throw error
        }
    }

    fun mirrorExisting(budgetId: String, archives: List<File>) = archives.forEach { mirror(budgetId, it) }

    fun removeMirror(budgetId: String, name: String) {
        val tree = read().uri ?: return
        runCatching {
            val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val appDirectory = child(root, "Actua") ?: return
            val budgetDirectory = child(appDirectory, budgetId) ?: return
            child(budgetDirectory, name)?.let { DocumentsContract.deleteDocument(resolver, it) }
        }
    }

    private fun createDirectory(parent: Uri, name: String): Uri =
        DocumentsContract.createDocument(resolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, name)
            ?: throw BackupDestinationException(BackupDestinationError.CANNOT_CREATE_DIRECTORY)

    private fun child(parent: Uri, name: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(parent, DocumentsContract.getDocumentId(parent))
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) if (cursor.getString(1) == name) {
                return DocumentsContract.buildDocumentUriUsingTree(parent, cursor.getString(0))
            }
        }
        return null
    }

    private fun documentName(uri: Uri): String? = resolver.query(
        DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri)),
        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
    )?.use { if (it.moveToFirst()) it.getString(0) else null }

    private class BackupDestinationException(
        val code: BackupDestinationError,
    ) : Exception()
}
