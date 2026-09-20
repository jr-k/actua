package com.azimulkabir.actua.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.network.ActualServerClient
import com.azimulkabir.actua.data.network.ActualServerException
import com.azimulkabir.actua.data.network.OidcCallbackPageText
import com.azimulkabir.actua.data.network.OidcCallbackServer
import com.azimulkabir.actua.data.network.RemoteBudgetFile
import com.azimulkabir.actua.data.network.ServerCertificateInfo
import com.azimulkabir.actua.data.network.TrustedCertificateStore
import com.azimulkabir.actua.data.network.UrlConnectionTransport
import com.azimulkabir.actua.data.network.inspectServerCertificate
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.BudgetDownloadException
import com.azimulkabir.actua.data.budget.BudgetDownloadService
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.data.budget.BackupItem
import com.azimulkabir.actua.data.budget.BackupService
import com.azimulkabir.actua.data.budget.DemoBudgetManager
import com.azimulkabir.actua.data.security.BudgetEncryptionKeyStore
import com.azimulkabir.actua.data.security.CredentialStore
import com.azimulkabir.actua.data.sync.ActualSyncRunner
import com.azimulkabir.actua.data.sync.SYNC_TRIGGER_AFTER_CHANGE
import com.azimulkabir.actua.data.sync.SYNC_TRIGGER_APP_OPEN
import com.azimulkabir.actua.data.sync.SYNC_TRIGGER_BACKGROUND
import com.azimulkabir.actua.data.sync.SYNC_TRIGGER_MANUAL
import com.azimulkabir.actua.data.sync.SyncErrorCode
import com.azimulkabir.actua.data.sync.SyncRunResult
import com.azimulkabir.actua.data.sync.SyncStatusStore
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.Locale

private data class PendingOpenIdLogin(
    val primaryUrl: String,
    val fallbackUrl: String,
    val activeUrl: String,
    val authorizationUrl: String,
    val callbackServer: OidcCallbackServer,
)

private enum class CertificateRetryAction { PASSWORD, OPEN_ID }

private data class PendingCertificateTrust(
    val info: ServerCertificateInfo,
    val action: CertificateRetryAction,
    val replacingExistingTrust: Boolean,
)

private data class CompletedOpenIdLogin(
    val primaryUrl: String,
    val fallbackUrl: String,
    val activeUrl: String,
    val token: String,
    val budgets: List<RemoteBudgetFile>,
)

internal fun formatSyncDuration(durationMillis: Long, locale: Locale = Locale.getDefault()): String = when {
    durationMillis < 1_000L -> "$durationMillis ms"
    else -> String.format(locale, "%.1f s", durationMillis / 1_000.0)
}

@Composable
private fun syncTriggerDisplayName(trigger: String): String = stringResource(when (trigger) {
    SYNC_TRIGGER_MANUAL, "Manual", "Manuelle", "Sync" -> R.string.sync_trigger_manual
    SYNC_TRIGGER_AFTER_CHANGE, "After change", "Après modification" -> R.string.sync_trigger_after_change
    SYNC_TRIGGER_APP_OPEN, "App open", "Ouverture de l’application" -> R.string.sync_trigger_app_open
    SYNC_TRIGGER_BACKGROUND, "Background", "Arrière-plan" -> R.string.sync_trigger_background
    else -> R.string.sync_trigger_background
})

@Composable
private fun syncErrorDisplayMessage(error: String): String = stringResource(when (error) {
    SyncErrorCode.ENCRYPTION_KEY_UNAVAILABLE.persistedValue,
    "Unlock this encrypted budget before syncing" -> R.string.unlock_budget_before_sync
    SyncErrorCode.UNAUTHORIZED.persistedValue, "Unauthorized" -> R.string.sync_error_unauthorized
    SyncErrorCode.CERTIFICATE.persistedValue -> R.string.sync_error_certificate
    SyncErrorCode.NETWORK.persistedValue -> R.string.sync_error_network
    SyncErrorCode.OUT_OF_SYNC.persistedValue, "Unable to converge with the server" -> R.string.sync_error_out_of_sync
    SyncErrorCode.CLOCK.persistedValue -> R.string.sync_error_clock
    SyncErrorCode.INVALID_SERVER_RESPONSE.persistedValue,
    "The server returned an invalid response" -> R.string.sync_error_invalid_response
    SyncErrorCode.SERVER_REQUEST.persistedValue -> R.string.sync_error_server_request
    else -> R.string.sync_error_unknown
})

/**
 * The raw exception message for an untrusted server certificate is a cryptic Java stack-trace
 * line (e.g. "Trust anchor for certification path not found"), which just repeats the error
 * shown before this fix without telling the user what to actually do about it. Two distinct
 * setups produce this same exception:
 *  - A self-signed/private-CA cert: the CA needs installing as a user CA certificate, scoped to
 *    "VPN and apps" — the install flow also offers a "Wi-Fi" only scope that leaves apps still
 *    untrusting it, reproducing this exact error even after "installing" the certificate.
 *  - A publicly-issued cert (e.g. Let's Encrypt via Tailscale) whose server sends only the leaf
 *    certificate instead of the full chain: unlike browsers, Android's TLS stack doesn't fetch
 *    missing intermediates on the fly, so the trust path fails even though the root is already
 *    trusted (see https://discuss.grapheneos.org/d/13339-is-there-no-lets-encrypt-ca-integrated).
 *    That side is a server/reverse-proxy misconfiguration no client-side change can fix.
 */
internal fun isCertificateTrustFailure(error: Throwable): Boolean {
    val causes = generateSequence(error) { it.cause }.toList()
    return causes.any { it is java.security.cert.CertPathValidatorException } ||
        causes.any {
            it is java.security.cert.CertificateException &&
                (it.message?.contains("trust anchor", ignoreCase = true) == true ||
                    it.message?.contains("no longer matches the certificate trusted in Actua", ignoreCase = true) == true)
        }
}

internal fun connectionErrorMessage(error: Throwable, fallback: String, certificateTrustMessage: String = fallback): String {
    return if (isCertificateTrustFailure(error)) {
        certificateTrustMessage
    } else {
        fallback
    }
}

@Composable
internal fun ManualSyncButtonContent(
    syncing: Boolean,
    demoActive: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.testTag("manualSyncButtonContent"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (syncing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(16.dp)
                        .testTag("manualSyncButtonIndicator"),
                    strokeWidth = 2.dp,
                )
            }
            Text(stringResource(if (demoActive) R.string.demo_local_only else if (syncing) R.string.syncing else R.string.sync_now))
        }
    }
}

@Composable
fun ConnectionScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    foregroundGeneration: Int = 0,
    onBeforeBudgetReplacement: () -> Unit = {},
    onBudgetInstalled: () -> Unit = {},
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    val credentials = remember { CredentialStore(context) }
    val certificateStore = remember { TrustedCertificateStore(context) }
    val client = remember {
        ActualServerClient(UrlConnectionTransport(certificateStore)).apply {
            customHeaders = credentials.customHeaders
        }
    }
    val files = remember { BudgetFileManager(context) }
    val activeBudget = remember { ActiveBudgetStore(context) }
    val downloader = remember { BudgetDownloadService(client, files, BudgetEncryptionKeyStore(context)) }
    val backupService = remember { BackupService(context, files) }
    val scope = rememberCoroutineScope()
    var serverUrl by remember { mutableStateOf(credentials.serverUrl) }
    var fallbackServerUrl by remember { mutableStateOf(credentials.fallbackServerUrl) }
    var activeServerUrl by remember { mutableStateOf(credentials.serverUrl) }
    var headerEntries by remember { mutableStateOf(credentials.customHeaders.toList()) }
    var showAddHeader by remember { mutableStateOf(false) }
    var headerNameInput by remember { mutableStateOf("") }
    var headerValueInput by remember { mutableStateOf("") }
    var editingConnection by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(credentials.token() != null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var remoteBudgets by remember { mutableStateOf<List<RemoteBudgetFile>>(emptyList()) }
    var downloadingId by remember { mutableStateOf<String?>(null) }
    var encryptionPassword by remember { mutableStateOf("") }
    val syncStatusStore = remember { SyncStatusStore(context) }
    var syncStatus by remember { mutableStateOf(syncStatusStore.read()) }
    var syncing by remember { mutableStateOf(false) }
    var backups by remember { mutableStateOf<List<BackupItem>>(emptyList()) }
    var backupBusy by remember { mutableStateOf(false) }
    var showBackups by remember { mutableStateOf(false) }
    var budgetsExpanded by remember { mutableStateOf(true) }
    var showCreateBudget by remember { mutableStateOf(false) }
    var newBudgetName by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<RemoteBudgetFile?>(null) }
    var deleteConfirmation by remember { mutableStateOf("") }
    var confirmQuickBackup by remember { mutableStateOf(false) }
    var pendingCertificateTrust by remember { mutableStateOf<PendingCertificateTrust?>(null) }
    var pendingOpenIdCallback by remember { mutableStateOf<OidcCallbackServer?>(null) }
    val demoActive = DemoBudgetManager.isDemoBudget(activeBudget.budgetId)

    fun refreshBackups() {
        val budgetId = activeBudget.budgetId
        backups = if (budgetId == null) emptyList() else runCatching {
            backupService.availableBackups(budgetId)
        }.getOrDefault(emptyList())
    }

    fun loadBudgets() {
        val token = credentials.token() ?: return
        loading = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                runCatching { serverUrl to client.listFiles(serverUrl, token) }.getOrElse { primary ->
                    val fallback = fallbackServerUrl.takeIf { it.isNotBlank() && it != serverUrl } ?: throw primary
                    fallback to client.listFiles(fallback, token)
                }
            } }.onSuccess { (usedUrl, budgets) ->
                activeServerUrl = usedUrl; remoteBudgets = budgets
                message = if (budgets.isEmpty()) resources.getString(R.string.no_budgets_found) else null
            }
                .onFailure { message = connectionErrorMessage(it, resources.getString(R.string.could_not_load_budgets), resources.getString(R.string.server_certificate_not_trusted_error)) }
            loading = false
        }
    }

    fun requestCertificateTrust(error: Throwable, action: CertificateRetryAction) {
        if (!isCertificateTrustFailure(error)) {
            message = connectionErrorMessage(error, resources.getString(R.string.could_not_connect_server), resources.getString(R.string.server_certificate_not_trusted_error))
            return
        }
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val normalized = client.normalizeServerUrl(serverUrl)
                    inspectServerCertificate(normalized)
                }
            }.onSuccess { info ->
                pendingCertificateTrust = PendingCertificateTrust(
                    info = info,
                    action = action,
                    replacingExistingTrust = certificateStore.hasTrust(info.host),
                )
                message = null
            }.onFailure {
                message = resources.getString(R.string.could_not_inspect_certificate)
            }
        }
    }

    fun connectWithPassword() {
        loading = true
        message = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val normalized = client.normalizeServerUrl(serverUrl)
                    val fallback = fallbackServerUrl.trim().takeIf(String::isNotEmpty)?.let(client::normalizeServerUrl).orEmpty()
                    runCatching { Triple(normalized, fallback, client.login(normalized, password)) }.getOrElse { primary ->
                        if (fallback.isEmpty() || fallback == normalized) throw primary
                        Triple(normalized, fallback, client.login(fallback, password))
                    }
                }
            }.onSuccess { (url, fallback, token) ->
                credentials.saveConnection(url, token, fallback)
                credentials.customHeaders = headerEntries.toMap()
                serverUrl = url
                fallbackServerUrl = fallback
                activeServerUrl = url
                password = ""
                connected = true
                message = resources.getString(R.string.connected)
                loadBudgets()
            }.onFailure { error ->
                if (isCertificateTrustFailure(error)) {
                    requestCertificateTrust(error, CertificateRetryAction.PASSWORD)
                } else if (error.message == "Incorrect server password.") {
                    message = resources.getString(R.string.incorrect_server_password)
                } else {
                    message = connectionErrorMessage(error, resources.getString(R.string.could_not_connect_server), resources.getString(R.string.server_certificate_not_trusted_error))
                }
            }
            loading = false
        }
    }

    fun connectWithOpenId() {
        loading = true
        message = resources.getString(R.string.preparing_openid)
        scope.launch {
            runCatching {
                val pending = withContext(Dispatchers.IO) {
                    val primary = client.normalizeServerUrl(serverUrl)
                    val fallback = fallbackServerUrl.trim().takeIf(String::isNotEmpty)?.let(client::normalizeServerUrl).orEmpty()
                    val candidates = listOf(primary, fallback)
                        .filter(String::isNotBlank)
                        .distinct()
                    var lastError: Throwable? = null
                    var activeUrl: String? = null
                    for (candidate in candidates) {
                        val methods = runCatching { client.loginMethods(candidate) }
                            .onFailure { lastError = it }
                            .getOrNull() ?: continue
                        if (methods.any { it.method.equals("openid", ignoreCase = true) }) {
                            activeUrl = candidate
                            break
                        }
                        lastError = IllegalStateException(resources.getString(R.string.openid_not_offered))
                    }
                    val selectedUrl = activeUrl ?: throw (lastError
                        ?: IllegalStateException(resources.getString(R.string.could_not_find_openid_server)))
                    val callback = OidcCallbackServer(
                        OidcCallbackPageText(
                            languageTag = configuration.locales[0].toLanguageTag(),
                            completionTitle = resources.getString(R.string.openid_completion_title),
                            completionMessage = resources.getString(R.string.openid_completion_message),
                            returnToApp = resources.getString(R.string.openid_return_to_app),
                            callbackErrorTitle = resources.getString(R.string.openid_callback_error_title),
                            callbackErrorMessage = resources.getString(R.string.openid_callback_error_message),
                        ),
                    )
                    try {
                        val authorizationUrl = client.startOpenIdLogin(selectedUrl, callback.returnUrl, password)
                        PendingOpenIdLogin(primary, fallback, selectedUrl, authorizationUrl, callback)
                    } catch (error: Throwable) {
                        callback.close()
                        throw error
                    }
                }

                pendingOpenIdCallback = pending.callbackServer
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(pending.authorizationUrl))
                context.startActivity(browserIntent)
                message = resources.getString(R.string.complete_sign_in_browser)

                val token = withContext(Dispatchers.IO) { pending.callbackServer.awaitToken() }
                val budgets = withContext(Dispatchers.IO) { client.listFiles(pending.activeUrl, token) }
                CompletedOpenIdLogin(
                    pending.primaryUrl,
                    pending.fallbackUrl,
                    pending.activeUrl,
                    token,
                    budgets,
                )
            }.onSuccess { result ->
                credentials.saveConnection(result.primaryUrl, result.token, result.fallbackUrl)
                credentials.customHeaders = headerEntries.toMap()
                serverUrl = result.primaryUrl
                fallbackServerUrl = result.fallbackUrl
                activeServerUrl = result.activeUrl
                remoteBudgets = result.budgets
                password = ""
                connected = true
                message = resources.getString(if (result.budgets.isEmpty()) R.string.connected_openid_no_budgets else R.string.connected_openid)
            }.onFailure { error ->
                message = when (error.message) {
                    "invalid-password" -> resources.getString(R.string.openid_current_password_required)
                    else -> {
                        if (isCertificateTrustFailure(error)) {
                            requestCertificateTrust(error, CertificateRetryAction.OPEN_ID)
                            null
                        } else {
                            connectionErrorMessage(error, resources.getString(R.string.could_not_complete_openid), resources.getString(R.string.server_certificate_not_trusted_error))
                        }
                    }
                }
            }
            pendingOpenIdCallback?.close()
            pendingOpenIdCallback = null
            loading = false
        }
    }

    fun openDemoBudget() {
        val previousBudgetId = activeBudget.budgetId
        loading = true
        message = null
        onBeforeBudgetReplacement()
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { DemoBudgetManager.recreate(files) } }
                .onSuccess { metadata ->
                    activeBudget.budgetId = metadata.id
                    message = if (previousBudgetId == metadata.id) {
                        resources.getString(R.string.demo_budget_reset)
                    } else {
                        resources.getString(R.string.demo_budget_opened)
                    }
                    onBudgetInstalled()
                    refreshBackups()
                }
                .onFailure { error ->
                    if (previousBudgetId == DemoBudgetManager.BUDGET_ID && !files.databaseFile(DemoBudgetManager.BUDGET_ID).isFile) {
                        activeBudget.budgetId = files.listLocalBudgets().firstOrNull { it.id != DemoBudgetManager.BUDGET_ID }?.id
                    }
                    message = resources.getString(R.string.could_not_create_demo_budget)
                    onBudgetInstalled()
                }
            loading = false
        }
    }

    val localNetworkPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) connectWithPassword()
        else message = resources.getString(R.string.local_network_required)
    }
    val localNetworkOpenIdPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) connectWithOpenId()
        else message = resources.getString(R.string.local_network_required)
    }

    LaunchedEffect(headerEntries) { client.customHeaders = headerEntries.toMap() }
    LaunchedEffect(foregroundGeneration) {
        pendingOpenIdCallback?.close()
    }
    DisposableEffect(pendingOpenIdCallback) {
        val callback = pendingOpenIdCallback
        onDispose { callback?.close() }
    }
    LaunchedEffect(connected) {
        if (connected && remoteBudgets.isEmpty()) loadBudgets()
        refreshBackups()
    }
    LaunchedEffect(Unit) {
        while (true) {
            syncStatus = syncStatusStore.read()
            delay(1_000)
        }
    }

    if (showBackups && activeBudget.budgetId != null) {
        BackupsScreen(
            budgetId = activeBudget.budgetId!!,
            onBack = { showBackups = false; refreshBackups() },
            onBeforeRestore = onBeforeBudgetReplacement,
            onRestored = onBudgetInstalled,
            modifier = modifier,
        )
        return
    }

    pendingCertificateTrust?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingCertificateTrust = null },
            title = {
                Text(stringResource(if (pending.replacingExistingTrust) R.string.server_certificate_changed else R.string.server_certificate_untrusted))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (pending.replacingExistingTrust) {
                            stringResource(R.string.server_certificate_changed_description)
                        } else {
                            stringResource(R.string.server_certificate_untrusted_description)
                        },
                    )
                    Text(stringResource(R.string.certificate_host, pending.info.host), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.certificate_issuer, pending.info.issuer), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.certificate_valid_from, pending.info.validFrom), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.certificate_valid_until, pending.info.validUntil), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.sha256_fingerprint), fontWeight = FontWeight.SemiBold)
                    Text(pending.info.sha256Fingerprint, style = MaterialTheme.typography.bodySmall)
                    Text(
                        stringResource(R.string.certificate_trust_scope, pending.info.host),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    certificateStore.trust(pending.info.host, pending.info.sha256Fingerprint)
                    pendingCertificateTrust = null
                    when (pending.action) {
                        CertificateRetryAction.PASSWORD -> connectWithPassword()
                        CertificateRetryAction.OPEN_ID -> connectWithOpenId()
                    }
                }) { Text(stringResource(if (pending.replacingExistingTrust) R.string.trust_new_certificate else R.string.trust_certificate)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingCertificateTrust = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showAddHeader) AlertDialog(
        onDismissRequest = { showAddHeader = false; headerNameInput = ""; headerValueInput = "" },
        title = { Text(stringResource(R.string.add_http_header)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = headerNameInput, onValueChange = { headerNameInput = it },
                    label = { Text(stringResource(R.string.header_name)) }, placeholder = { Text(stringResource(R.string.header_name_example)) }, singleLine = true,
                )
                OutlinedTextField(
                    value = headerValueInput, onValueChange = { headerValueInput = it },
                    label = { Text(stringResource(R.string.header_value)) }, singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(
            enabled = headerNameInput.isNotBlank() && headerValueInput.isNotBlank(),
            onClick = {
                val name = headerNameInput.trim()
                headerEntries = headerEntries.filterNot { it.first.equals(name, ignoreCase = true) } + (name to headerValueInput.trim())
                showAddHeader = false; headerNameInput = ""; headerValueInput = ""
            },
        ) { Text(stringResource(R.string.add)) } },
        dismissButton = { TextButton(onClick = { showAddHeader = false; headerNameInput = ""; headerValueInput = "" }) { Text(stringResource(R.string.cancel)) } },
    )

    if (showCreateBudget) AlertDialog(
        onDismissRequest = { if (!loading) showCreateBudget = false },
        title = { Text(stringResource(R.string.create_new_budget)) },
        text = { OutlinedTextField(
            value = newBudgetName,
            onValueChange = { if (it.length <= 100) newBudgetName = it },
            label = { Text(stringResource(R.string.budget_name)) },
            supportingText = { Text(stringResource(R.string.create_budget_description)) },
            singleLine = true,
        ) },
        confirmButton = { TextButton(
            enabled = !loading && newBudgetName.trim().isNotEmpty(),
            onClick = {
                val name = newBudgetName.trim()
                val existingNames = remoteBudgets.map { it.name } + files.listLocalBudgets().mapNotNull { it.budgetName }
                if (name in existingNames) { message = resources.getString(R.string.name_already_exists, name); return@TextButton }
                loading = true; message = null
                scope.launch {
                    var localId: String? = null
                    runCatching { withContext(Dispatchers.IO) {
                        val token = credentials.token() ?: throw ActualServerException.Unauthorized
                        val local = files.createBudget(name); localId = local.id
                        val cloudFileId = UUID.randomUUID().toString().lowercase()
                        val archive = files.uploadArchive(local.id)
                        val groupId = runCatching {
                            client.uploadFile(activeServerUrl, token, cloudFileId, name, archive)
                        }.getOrElse { uploadError ->
                            val remote = runCatching { client.listFiles(activeServerUrl, token) }.getOrNull()
                                ?.firstOrNull { it.fileId == cloudFileId }
                            remote?.groupId ?: throw uploadError
                        }
                        files.saveCloudRegistration(local.id, cloudFileId, groupId)
                        local.id
                    } }.onSuccess { id ->
                        onBeforeBudgetReplacement(); activeBudget.budgetId = id
                        showCreateBudget = false; newBudgetName = ""
                        message = resources.getString(R.string.budget_created_opened, name); onBudgetInstalled(); loadBudgets(); refreshBackups()
                    }.onFailure { error ->
                        localId?.let { runCatching { files.deleteBudget(it) } }
                        message = resources.getString(R.string.could_not_create_budget)
                    }
                    loading = false
                }
            },
        ) { Text(stringResource(R.string.create)) } },
        dismissButton = { TextButton(enabled = !loading, onClick = { showCreateBudget = false }) { Text(stringResource(R.string.cancel)) } },
    )

    pendingDelete?.let { remote ->
        AlertDialog(
            onDismissRequest = { if (!loading) pendingDelete = null },
            title = { Text(stringResource(R.string.delete_named_budget, remote.name)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.delete_budget_warning))
                OutlinedTextField(
                    value = deleteConfirmation,
                    onValueChange = { deleteConfirmation = it },
                    label = { Text(stringResource(R.string.type_name_to_confirm, remote.name)) },
                    singleLine = true,
                )
            } },
            confirmButton = { TextButton(
                enabled = !loading && deleteConfirmation == remote.name,
                onClick = {
                    loading = true; message = null; onBeforeBudgetReplacement()
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) {
                            val token = credentials.token() ?: throw ActualServerException.Unauthorized
                            val local = files.listLocalBudgets().firstOrNull { it.cloudFileId == remote.fileId }
                            if (local?.id == activeBudget.budgetId) runCatching { ActualSyncRunner.run(context) }
                            try { client.deleteFile(activeServerUrl, token, remote.fileId) }
                            catch (_: ActualServerException.FileNotFound) { }
                            BudgetEncryptionKeyStore(context).remove(remote.fileId)
                            local?.let { files.deleteBudget(it.id) }
                        } }.onSuccess {
                            val remaining = files.listLocalBudgets()
                            if (remaining.none { it.id == activeBudget.budgetId }) {
                                activeBudget.budgetId = remaining.firstOrNull()?.id
                            }
                            pendingDelete = null; deleteConfirmation = ""
                            message = resources.getString(R.string.budget_deleted, remote.name); loadBudgets(); refreshBackups(); onBudgetInstalled()
                        }.onFailure { error ->
                            message = resources.getString(R.string.could_not_delete_budget); onBudgetInstalled()
                        }
                        loading = false
                    }
                },
            ) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(enabled = !loading, onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmQuickBackup) AlertDialog(
        onDismissRequest = { confirmQuickBackup = false },
        title = { Text(stringResource(R.string.create_new_backup_title)) },
        text = { Text(stringResource(R.string.quick_backup_description)) },
        confirmButton = { TextButton(onClick = {
            confirmQuickBackup = false
            val budgetId = activeBudget.budgetId ?: return@TextButton
            backupBusy = true
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { backupService.makeBackup(budgetId) } }
                    .onSuccess { message = resources.getString(R.string.backup_created) }
                    .onFailure { message = resources.getString(R.string.could_not_create_a_backup) }
                backupBusy = false; refreshBackups()
            }
        }) { Text(stringResource(R.string.back_up)) } },
        dismissButton = { TextButton(onClick = { confirmQuickBackup = false }) { Text(stringResource(R.string.cancel)) } },
    )

    Column(modifier = modifier.fillMaxSize()) {
        ActuaScreenHeader(title = stringResource(R.string.connection_data_title), onBack = onBack)
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.try_actua), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (demoActive) {
                    stringResource(R.string.demo_budget_active_description)
                } else {
                    stringResource(R.string.demo_budget_description)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = ::openDemoBudget,
                enabled = !loading && downloadingId == null && !syncing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (loading) CircularProgressIndicator(Modifier.padding(end = 8.dp))
                Text(stringResource(if (demoActive) R.string.reset_demo_budget else R.string.try_demo_budget))
            }
            Text(
                stringResource(R.string.demo_local_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.connection), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
                if (connected && !editingConnection) TextButton(onClick = { editingConnection = true }) { Text(stringResource(R.string.edit)) }
            }
            OutlinedTextField(
                value = serverUrl, onValueChange = { serverUrl = it }, label = { Text(stringResource(R.string.server_url)) },
                placeholder = { Text(stringResource(R.string.server_url_example)) }, singleLine = true,
                enabled = (!connected || editingConnection) && !loading, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            OutlinedTextField(
                value = fallbackServerUrl, onValueChange = { fallbackServerUrl = it },
                label = { Text(stringResource(R.string.fallback_server_url)) },
                placeholder = { Text(stringResource(R.string.fallback_server_url_example)) }, singleLine = true,
                enabled = (!connected || editingConnection) && !loading, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            if (!connected || editingConnection) {
                Text(stringResource(R.string.custom_http_headers), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                headerEntries.forEachIndexed { index, (name, value) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(value, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(
                            enabled = !loading,
                            onClick = { headerEntries = headerEntries.filterIndexed { i, _ -> i != index } },
                        ) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.remove_named_header, name)) }
                    }
                }
                OutlinedButton(
                    onClick = { showAddHeader = true }, enabled = !loading, modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.add_header)) }
                Text(
                    stringResource(R.string.custom_headers_description),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!connected) {
                OutlinedTextField(
                    value = password, onValueChange = { password = it }, label = { Text(stringResource(R.string.server_password)) },
                    singleLine = true, enabled = !loading, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = stringResource(if (passwordVisible) R.string.hide_password else R.string.show_password))
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 37 && ContextCompat.checkSelfPermission(
                                context, Manifest.permission.ACCESS_LOCAL_NETWORK,
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            localNetworkPermission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                        } else {
                            connectWithPassword()
                        }
                    },
                    enabled = serverUrl.isNotBlank() && password.isNotBlank() && !loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (loading) CircularProgressIndicator(modifier = Modifier.padding(end = 10.dp))
                    Text(stringResource(if (loading) R.string.connecting else R.string.connect_with_password))
                }
                OutlinedButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 37 && ContextCompat.checkSelfPermission(
                                context, Manifest.permission.ACCESS_LOCAL_NETWORK,
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            localNetworkOpenIdPermission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                        } else {
                            connectWithOpenId()
                        }
                    },
                    enabled = serverUrl.isNotBlank() && !loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.sign_in_openid))
                }
                Text(
                    stringResource(R.string.openid_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(stringResource(R.string.connected_indicator), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                if (editingConnection) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = {
                        serverUrl = credentials.serverUrl
                        fallbackServerUrl = credentials.fallbackServerUrl
                        headerEntries = credentials.customHeaders.toList()
                        editingConnection = false
                    }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
                    Button(onClick = {
                        loading = true; message = null
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) {
                                val primary = client.normalizeServerUrl(serverUrl)
                                val fallback = fallbackServerUrl.trim().takeIf(String::isNotEmpty)?.let(client::normalizeServerUrl).orEmpty()
                                client.loginMethods(primary)
                                primary to fallback
                            } }.onSuccess { (primary, fallback) ->
                                credentials.updateServerUrls(primary, fallback)
                                credentials.customHeaders = headerEntries.toMap()
                                serverUrl = primary; fallbackServerUrl = fallback; activeServerUrl = primary
                                editingConnection = false; message = resources.getString(R.string.server_addresses_updated)
                            }.onFailure { message = resources.getString(R.string.could_not_reach_primary_server) }
                            loading = false
                        }
                    }, enabled = serverUrl.isNotBlank() && !loading, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.save)) }
                }
                OutlinedButton(onClick = {
                    listOf(serverUrl, fallbackServerUrl).filter(String::isNotBlank).forEach { url ->
                        runCatching { java.net.URI(client.normalizeServerUrl(url)).host }
                            .getOrNull()?.let(certificateStore::forget)
                    }
                    credentials.clear()
                    connected = false
                    remoteBudgets = emptyList()
                    message = resources.getString(R.string.disconnected)
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.disconnect)) }
            }
            message?.let {
                Text(it, color = if (connected || demoActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
            Text(stringResource(R.string.token_security_description),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (connected) {
                Text(stringResource(R.string.sync), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (demoActive) {
                    Text(
                        stringResource(R.string.demo_sync_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.status), Modifier.weight(1f))
                    Text(if (syncStatus.running || syncing) {
                        syncStatus.activeTrigger?.let {
                            stringResource(R.string.syncing_trigger, syncTriggerDisplayName(it))
                        } ?: stringResource(R.string.syncing_plain)
                    } else if (syncStatus.error != null) stringResource(R.string.error) else stringResource(R.string.idle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.last_sync), Modifier.weight(1f))
                    Text(syncStatus.lastSuccessMillis.takeIf { it > 0 }?.let { relativeTime(it) } ?: stringResource(R.string.never),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.last_app_open_refresh), Modifier.weight(1f))
                    Text(syncStatus.lastForegroundRefreshMillis.takeIf { it > 0 }?.let { relativeTime(it) } ?: stringResource(R.string.never),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.last_background_attempt), Modifier.weight(1f))
                    Text(syncStatus.lastBackgroundRefreshMillis.takeIf { it > 0 }?.let { relativeTime(it) } ?: stringResource(R.string.never),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (syncStatus.lastDurationMillis > 0) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.last_sync_duration), Modifier.weight(1f))
                        Text(formatSyncDuration(
                            syncStatus.lastDurationMillis,
                            configuration.locales[0],
                        ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    stringResource(R.string.sync_timing_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                syncStatus.error?.let { Text(syncErrorDisplayMessage(it), color = MaterialTheme.colorScheme.error) }
                OutlinedButton(enabled = !demoActive && !syncing && !loading && downloadingId == null,
                    modifier = Modifier.fillMaxWidth(), onClick = {
                        syncing = true; message = null
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) {
                                ActualSyncRunner.run(context, trigger = SYNC_TRIGGER_MANUAL)
                            } }.onSuccess { result ->
                                when (result) {
                                    is SyncRunResult.Success -> {
                                        message = resources.getString(R.string.sync_result, result.outcome.sentMessages, result.outcome.receivedMessages)
                                        onBudgetInstalled()
                                    }
                                    SyncRunResult.NotConfigured -> {
                                        message = resources.getString(R.string.download_select_budget_first)
                                    }
                                    SyncRunResult.EncryptionKeyUnavailable -> {
                                        message = resources.getString(R.string.unlock_budget_before_sync)
                                    }
                                }
                            }.onFailure { message = resources.getString(R.string.sync_failed) }
                            syncStatus = syncStatusStore.read(); syncing = false
                        }
                    }) {
                    ManualSyncButtonContent(syncing = syncing, demoActive = demoActive)
                }
                val budgetsChevronRotation by animateFloatAsState(
                    if (budgetsExpanded) 0f else -90f, tween(220), label = "budgetsSection",
                )
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable(role = Role.Button, onClick = { budgetsExpanded = !budgetsExpanded }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.budgets), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Outlined.KeyboardArrowDown,
                        contentDescription = stringResource(if (budgetsExpanded) R.string.collapse_budgets else R.string.expand_budgets),
                        modifier = Modifier.rotate(budgetsChevronRotation),
                    )
                }
                AnimatedVisibility(visible = budgetsExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        OutlinedButton(
                            onClick = { showCreateBudget = true; newBudgetName = "" },
                            enabled = !loading && downloadingId == null,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.create_new_budget)) }
                        if (remoteBudgets.any { it.encryptedKeyId != null }) {
                            OutlinedTextField(
                                value = encryptionPassword,
                                onValueChange = { encryptionPassword = it },
                                label = { Text(stringResource(R.string.budget_encryption_password)) },
                                singleLine = true,
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        remoteBudgets.forEach { remote ->
                            val local = files.listLocalBudgets().firstOrNull { it.cloudFileId == remote.fileId }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(remote.name, fontWeight = FontWeight.Medium)
                                    Text(
                                        when {
                                            activeBudget.budgetId == local?.id -> stringResource(R.string.active)
                                            local != null -> stringResource(R.string.downloaded)
                                            remote.encryptedKeyId != null -> stringResource(R.string.encrypted)
                                            else -> stringResource(R.string.available)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                OutlinedButton(
                                    enabled = downloadingId == null,
                                    onClick = {
                                        val token = credentials.token() ?: return@OutlinedButton
                                        onBeforeBudgetReplacement()
                                        downloadingId = remote.fileId
                                        message = null
                                        scope.launch {
                                            runCatching {
                                                withContext(Dispatchers.IO) {
                                                    if (remote.encryptedKeyId != null) {
                                                        if (encryptionPassword.isNotBlank()) {
                                                            downloader.unlock(activeServerUrl, token, remote.fileId, encryptionPassword)
                                                        }
                                                    }
                                                    downloader.download(activeServerUrl, token, remote)
                                                }
                                            }.onSuccess { metadata ->
                                                activeBudget.budgetId = metadata.id
                                                message = resources.getString(R.string.budget_downloaded_active, remote.name)
                                                onBudgetInstalled()
                                            }.onFailure { error ->
                                                message = when (error) {
                                                    BudgetDownloadException.EncryptionPasswordRequired -> resources.getString(R.string.enter_budget_encryption_password)
                                                    BudgetDownloadException.EncryptionKeyChanged -> resources.getString(R.string.budget_encryption_key_changed)
                                                    BudgetDownloadException.InvalidEncryptionMetadata -> resources.getString(R.string.invalid_budget_encryption_metadata)
                                                    else -> resources.getString(R.string.could_not_download_budget)
                                                }
                                                onBudgetInstalled()
                                            }
                                            downloadingId = null
                                        }
                                    },
                                ) {
                                    if (downloadingId == remote.fileId) CircularProgressIndicator(Modifier.padding(end = 8.dp))
                                    Text(stringResource(if (local == null) R.string.download else if (activeBudget.budgetId == local.id) R.string.refresh else R.string.use))
                                }
                                TextButton(
                                    enabled = downloadingId == null && !loading,
                                    onClick = { pendingDelete = remote; deleteConfirmation = "" },
                                ) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                            }
                        }
                        OutlinedButton(onClick = { loadBudgets() }, enabled = !loading && downloadingId == null,
                            modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.refresh_budget_list)) }
                    }
                }
            }

            activeBudget.budgetId?.let { budgetId ->
                Text(stringResource(R.string.backups_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.private_backups_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !backupBusy,
                    onClick = { showBackups = true },
                ) {
                    val count = backups.count { it is BackupItem.Archive }
                    Text(androidx.compose.ui.res.pluralStringResource(R.plurals.backups_count, count, count))
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !backupBusy && downloadingId == null && !syncing,
                    onClick = {
                        if (backups.any { it is BackupItem.Latest }) {
                            confirmQuickBackup = true
                            return@OutlinedButton
                        }
                        backupBusy = true; message = null
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { backupService.makeBackup(budgetId) } }
                                .onSuccess { message = resources.getString(R.string.backup_created) }
                                .onFailure { message = resources.getString(R.string.could_not_create_a_backup) }
                            backupBusy = false; refreshBackups()
                        }
                    },
                ) {
                    if (backupBusy) CircularProgressIndicator(Modifier.padding(end = 8.dp))
                    Text(stringResource(if (backupBusy) R.string.working else R.string.create_backup_now))
                }
            }
        }
    }
}
