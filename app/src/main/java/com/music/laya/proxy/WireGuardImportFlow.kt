package com.music.laya.proxy

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import com.music.laya.R
import com.music.laya.data.AppProxyKind
import com.music.laya.data.AppProxyRoute
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One import path shared by first-run onboarding and the normal proxy editor.
 * Only authentic .conf filenames are accepted. SAF cannot reliably filter
 * by filename extension across document providers, so the picker leaves MIME
 * unrestricted and validates the returned document's DISPLAY_NAME instead.
 */
object WireGuardConfImporter {
    class InvalidExtension : IOException("Expected a .conf document")

    suspend fun importAndActivate(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val resolver: ContentResolver = context.contentResolver
        val displayName = resolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }
        if (displayName == null ||
            displayName == ".conf" ||
            !displayName.lowercase(Locale.ROOT).endsWith(".conf")
        ) {
            throw InvalidExtension()
        }
        val profile = resolver.openInputStream(uri)?.use(WireGuardProfileStore::readPlaintext)
            ?: throw IOException("Unable to read configuration")
        // Native validates the WireGuard profile before it is stored, and the
        // private key is persisted encrypted with AndroidKeyStore AES-GCM.
        WireGuardProfileStore.saveValidated(context, profile)
        // commit() is synchronous. The helper process will kill this process
        // immediately after confirmation, so queued .apply() is not safe.
        AppProxyStore.save(
            context,
            AppProxyRoute(AppProxyKind.WIREGUARD_LOCAL, "127.0.0.1", 9),
        )
    }
}

class WireGuardImportUiState internal constructor(
    val openConf: () -> Unit,
    val importing: Boolean,
    val error: String?,
    val restartRequired: Boolean,
    val confirmRestart: () -> Unit,
)

@Composable
fun rememberWireGuardImportUiState(): WireGuardImportUiState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var restartRequired by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !importing && !restartRequired) {
            scope.launch {
                importing = true
                error = null
                try {
                    WireGuardConfImporter.importAndActivate(context, uri)
                    restartRequired = true
                } catch (_: WireGuardConfImporter.InvalidExtension) {
                    error = context.getString(R.string.fourpril_conf_extension_error)
                } catch (_: LinkageError) {
                    error = context.getString(R.string.fourpril_conf_engine_error)
                } catch (_: Exception) {
                    // Do not show malformed profile text, native exceptions or
                    // private keys. Both pages use a non-sensitive error.
                    error = context.getString(R.string.fourpril_conf_import_error)
                } finally {
                    importing = false
                }
            }
        }
    }
    return WireGuardImportUiState(
        openConf = {
            if (!importing && !restartRequired) {
                error = null
                // SAF restricts by MIME not file extension; a .conf may be
                // text/plain or octet-stream depending on device/provider.
                // Acceptable document names are enforced after selection.
                importer.launch(arrayOf("*/*"))
            }
        },
        importing = importing,
        error = error,
        restartRequired = restartRequired,
        confirmRestart = {
            var found: Context? = context
            var activity: Activity? = null
            while (found != null) {
                if (found is Activity) {
                    activity = found
                    break
                }
                found = (found as? ContextWrapper)?.baseContext
            }
            activity?.let(AppProxyRelaunchActivity::restartFrom)
                ?: throw IllegalStateException("No Activity to restart")
        },
    )
}

/** Same mandatory confirmation in onboarding and in the settings page. */
@Composable
fun WireGuardImportRestartDialog(state: WireGuardImportUiState) {
    if (!state.restartRequired) return
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        title = { Text(stringResource(R.string.fourpril_proxy_restart_title)) },
        text = { Text(stringResource(R.string.fourpril_proxy_restart_message)) },
        confirmButton = {
            TextButton(onClick = state.confirmRestart) {
                Text(stringResource(R.string.fourpril_proxy_restart_confirm))
            }
        },
    )
}
