package com.azimulkabir.actua.data.sync

import android.content.Context
import android.os.SystemClock
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.ActualBudgetDatabase
import com.azimulkabir.actua.data.budget.ActualTransactionWriter
import com.azimulkabir.actua.data.budget.BackupService
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.data.budget.DemoBudgetManager
import com.azimulkabir.actua.data.network.ActualServerClient
import com.azimulkabir.actua.data.network.TrustedCertificateStore
import com.azimulkabir.actua.data.network.UrlConnectionTransport
import com.azimulkabir.actua.data.notifications.CreditCardDueNotificationScheduler
import com.azimulkabir.actua.data.schedules.ActualScheduleWriter
import com.azimulkabir.actua.data.schedules.SchedulePoster
import com.azimulkabir.actua.data.security.BudgetEncryptionKeyStore
import com.azimulkabir.actua.data.security.CredentialStore
import java.util.concurrent.TimeUnit
import com.azimulkabir.actua.widget.WidgetUpdater

sealed interface SyncRunResult {
    data class Success(val outcome: SyncOutcome, val postedSchedules: Int) : SyncRunResult
    data object NotConfigured : SyncRunResult
    data object EncryptionKeyUnavailable : SyncRunResult
}

/** Headless equivalent of iOS syncInBackground, excluding Wallet/FinanceKit. */
object ActualSyncRunner {
    private var lastSuccessBudgetId: String? = null
    private var lastSuccessElapsedMillis: Long = Long.MIN_VALUE
    private var lastSuccess: SyncRunResult.Success? = null

    @Synchronized
    fun run(
        context: Context,
        makeBackup: Boolean = false,
        allowRecentSuccess: Boolean = false,
        trigger: String = SYNC_TRIGGER_MANUAL,
    ): SyncRunResult {
        val app = context.applicationContext
        val status = SyncStatusStore(app)
        val budgetId = ActiveBudgetStore(app).budgetId ?: return SyncRunResult.NotConfigured.also {
            status.stoppedWithoutSync()
        }
        val credentials = CredentialStore(app)
        val token = credentials.token() ?: return SyncRunResult.NotConfigured.also { status.stoppedWithoutSync() }
        val serverUrl = credentials.serverUrl.takeIf(String::isNotBlank)
            ?: return SyncRunResult.NotConfigured.also { status.stoppedWithoutSync() }
        val fallbackUrl = credentials.fallbackServerUrl.takeIf { it.isNotBlank() && it != serverUrl }
        val files = BudgetFileManager(app)
        if (DemoBudgetManager.isDemoBudget(budgetId)) return SyncRunResult.NotConfigured.also {
            status.stoppedWithoutSync()
        }
        val metadata = files.listLocalBudgets().firstOrNull { it.id == budgetId }
            ?: return SyncRunResult.NotConfigured.also { status.stoppedWithoutSync() }
        val fileId = metadata.cloudFileId ?: return SyncRunResult.NotConfigured.also { status.stoppedWithoutSync() }
        val groupId = metadata.groupId ?: return SyncRunResult.NotConfigured.also { status.stoppedWithoutSync() }
        val loadedKey = metadata.encryptKeyId?.let {
            BudgetEncryptionKeyStore(app).load(fileId) ?: return SyncRunResult.EncryptionKeyUnavailable.also {
                status.failed(SyncStatusException.EncryptionKeyUnavailable)
            }
        }
        if (metadata.encryptKeyId != null && loadedKey?.keyId != metadata.encryptKeyId) {
            return SyncRunResult.EncryptionKeyUnavailable.also {
                status.failed(SyncStatusException.EncryptionKeyUnavailable)
            }
        }
        val nowElapsed = SystemClock.elapsedRealtime()
        if (SyncCoalescingPolicy.shouldReuse(
                allowRecentSuccess = allowRecentSuccess,
                requestedBudgetId = budgetId,
                completedBudgetId = lastSuccessBudgetId,
                completedElapsedMillis = lastSuccessElapsedMillis,
                nowElapsedMillis = nowElapsed,
            )
        ) {
            if (makeBackup) runCatching { BackupService(app, files).makeBackup(budgetId) }
            if (trigger == SYNC_TRIGGER_APP_OPEN) status.foregroundRefreshFinished()
            return requireNotNull(lastSuccess)
        }
        status.started(trigger)
        return try {
            val result = ActualBudgetDatabase.open(files.databaseFile(budgetId)).use { database ->
                val server = ActualServerClient(UrlConnectionTransport(TrustedCertificateStore(context))).apply { customHeaders = credentials.customHeaders }
                fun syncAt(url: String) = ActualSyncClient(url, token, server, database, fileId, groupId,
                    loadedKey?.keyId, loadedKey?.let { ActualMessageCipher(it.key) }).sync()
                var outcome = try { syncAt(serverUrl) } catch (primary: Exception) {
                    fallbackUrl?.let(::syncAt) ?: throw primary
                }
                val poster = SchedulePoster(app, database, ActualTransactionWriter(database), ActualScheduleWriter(database))
                val posted = poster.runIfNeeded(budgetId)
                if (posted > 0) outcome = try { syncAt(serverUrl) } catch (primary: Exception) {
                    fallbackUrl?.let(::syncAt) ?: throw primary
                }
                if (makeBackup) runCatching { BackupService(app, files).makeBackup(budgetId) }
                SyncRunResult.Success(outcome, posted)
            }
            lastSuccessBudgetId = budgetId
            lastSuccessElapsedMillis = SystemClock.elapsedRealtime()
            lastSuccess = result
            status.succeeded(result.outcome)
            SyncSignals.dataChanged()
            result
        } catch (error: Exception) {
            status.failed(error)
            throw error
        }
    }
}

internal object SyncCoalescingPolicy {
    const val RECENT_SUCCESS_WINDOW_MILLIS = 5_000L

    fun shouldReuse(
        allowRecentSuccess: Boolean,
        requestedBudgetId: String?,
        completedBudgetId: String?,
        completedElapsedMillis: Long,
        nowElapsedMillis: Long,
    ): Boolean = allowRecentSuccess && requestedBudgetId != null && requestedBudgetId == completedBudgetId &&
        nowElapsedMillis >= completedElapsedMillis &&
        nowElapsedMillis - completedElapsedMillis <= RECENT_SUCCESS_WINDOW_MILLIS
}

/** Stable persisted trigger codes. These are translated only when displayed. */
const val SYNC_TRIGGER_MANUAL = "manual"
const val SYNC_TRIGGER_AFTER_CHANGE = "after_change"
const val SYNC_TRIGGER_APP_OPEN = "app_open"
const val SYNC_TRIGGER_BACKGROUND = "background"

internal fun syncTriggerLabel(reason: String?): String = when (reason) {
    ActualSyncWorker.REASON_MUTATION -> SYNC_TRIGGER_AFTER_CHANGE
    ActualSyncWorker.REASON_FOREGROUND -> SYNC_TRIGGER_APP_OPEN
    else -> SYNC_TRIGGER_BACKGROUND
}

internal fun allowsRecentSuccess(reason: String?): Boolean =
    reason != ActualSyncWorker.REASON_MUTATION

class ActualSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val reason = inputData.getString(REASON_KEY)
        return try {
            when (val run = ActualSyncRunner.run(
                applicationContext,
                makeBackup = inputData.getBoolean(BACKUP_KEY, false),
                allowRecentSuccess = allowsRecentSuccess(reason),
                trigger = syncTriggerLabel(reason),
            )) {
                is SyncRunResult.Success -> {
                    CreditCardDueNotificationScheduler.refresh(applicationContext)
                    WidgetUpdater.requestAll(applicationContext)
                    Result.success()
                }
                SyncRunResult.NotConfigured -> Result.success()
                SyncRunResult.EncryptionKeyUnavailable -> Result.failure()
            }
        } catch (error: Exception) {
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        } finally {
            if (inputData.getBoolean(BACKGROUND_KEY, false)) {
                SyncStatusStore(applicationContext).backgroundRefreshFinished()
            }
        }
    }

    companion object {
        const val BACKUP_KEY = "makeBackup"
        const val BACKGROUND_KEY = "backgroundRefresh"
        const val REASON_KEY = "reason"
        const val REASON_PERIODIC = "periodic"
        const val REASON_MUTATION = "mutation"
        const val REASON_FOREGROUND = "foreground"
    }
}

class LocalBackupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        return try {
            val budgetId = ActiveBudgetStore(applicationContext).budgetId ?: return Result.success()
            BackupService(applicationContext).makeBackup(budgetId)
            Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}

object ActualSyncScheduler {
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<ActualSyncWorker>(15, TimeUnit.MINUTES)
            .setInputData(workDataOf(
                ActualSyncWorker.BACKUP_KEY to true,
                ActualSyncWorker.BACKGROUND_KEY to true,
                ActualSyncWorker.REASON_KEY to ActualSyncWorker.REASON_PERIODIC,
            ))
            .setConstraints(network).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** A short delay coalesces the CRDT cells produced by one user operation. */
    fun scheduleMutation(context: Context) {
        val request = OneTimeWorkRequestBuilder<ActualSyncWorker>()
            .setInputData(workDataOf(ActualSyncWorker.REASON_KEY to ActualSyncWorker.REASON_MUTATION))
            .setInitialDelay(1, TimeUnit.SECONDS)
            .setConstraints(network).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(IMMEDIATE, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun scheduleLocalBackup(context: Context) {
        val request = OneTimeWorkRequestBuilder<LocalBackupWorker>().build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(LOCAL_BACKUP, ExistingWorkPolicy.REPLACE, request)
    }

    private const val PERIODIC = "actua-periodic-sync"
    private const val IMMEDIATE = "actua-immediate-sync"
    private const val LOCAL_BACKUP = "actua-local-backup"
}
