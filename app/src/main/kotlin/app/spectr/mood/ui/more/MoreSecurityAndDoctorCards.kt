package app.spectr.mood.ui.more

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MedicalInformation
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.spectr.mood.R
import app.spectr.mood.domain.model.LockTimeout
import app.spectr.mood.domain.model.SecurityConfig
import app.spectr.mood.platform.pdf.ExportedPdfFile
import app.spectr.mood.security.BiometricAvailability
import kotlinx.coroutines.flow.SharedFlow

@Composable
internal fun DoctorToolsAndGuideCard(
    onOpenPdfExport: () -> Unit,
    onOpenDoctorMode: () -> Unit,
    onOpenGuide: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.MedicalInformation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.more_doctor_section_title),
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            MoreActionRow(
                icon = Icons.Outlined.PictureAsPdf,
                title = stringResource(R.string.more_pdf_export_title),
                subtitle = stringResource(R.string.more_pdf_export_subtitle),
                onClick = onOpenPdfExport
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            MoreActionRow(
                icon = Icons.Outlined.VisibilityOff,
                title = stringResource(R.string.more_doctor_mode_title),
                subtitle = stringResource(R.string.more_doctor_mode_subtitle),
                onClick = onOpenDoctorMode
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            MoreActionRow(
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                title = stringResource(R.string.more_guide_title),
                subtitle = stringResource(R.string.more_guide_subtitle),
                onClick = onOpenGuide
            )
        }
    }
}

@Composable
private fun MoreActionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LocalSecurityCard(
    config: SecurityConfig,
    biometricAvailability: BiometricAvailability,
    onIntent: (MoreIntent) -> Unit,
    onLockNowClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.security_section_title),
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            SecurityToggleRow(
                title = stringResource(R.string.security_app_lock_title),
                subtitle = stringResource(R.string.security_app_lock_subtitle),
                checked = config.isLockActive,
                onCheckedChange = { onIntent(MoreIntent.ToggleAppLock(it)) }
            )

            if (config.isLockActive) {
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onIntent(MoreIntent.OpenChangePinDialog) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Pin,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.security_change_pin_button))
                    }

                    FilledTonalButton(
                        onClick = onLockNowClick,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.security_lock_now_button))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                val bioAvailable = biometricAvailability == BiometricAvailability.AVAILABLE
                val bioSubtitle = when (biometricAvailability) {
                    BiometricAvailability.AVAILABLE -> stringResource(R.string.security_biometric_subtitle_available)
                    BiometricAvailability.NONE_ENROLLED ->
                        stringResource(R.string.security_biometric_subtitle_not_enrolled)
                    BiometricAvailability.UNAVAILABLE ->
                        stringResource(R.string.security_biometric_subtitle_unavailable)
                }
                SecurityToggleRow(
                    title = stringResource(R.string.security_biometric_title),
                    subtitle = bioSubtitle,
                    checked = config.isBiometricEnabled && bioAvailable,
                    enabled = bioAvailable,
                    onCheckedChange = { onIntent(MoreIntent.SetBiometricEnabled(it)) }
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.security_timeout_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LockTimeout.entries.forEach { timeout ->
                        FilterChip(
                            selected = config.lockTimeout == timeout,
                            onClick = { onIntent(MoreIntent.SetLockTimeout(timeout)) },
                            label = { Text(text = lockTimeoutLabel(timeout)) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(14.dp))

            SecurityToggleRow(
                title = stringResource(R.string.security_flag_secure_title),
                subtitle = stringResource(R.string.security_flag_secure_subtitle),
                checked = config.isScreenSecurityEnabled,
                onCheckedChange = { onIntent(MoreIntent.SetScreenSecurityEnabled(it)) }
            )
        }
    }
}

@Composable
private fun SecurityToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                }
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun lockTimeoutLabel(timeout: LockTimeout): String = when (timeout) {
    LockTimeout.IMMEDIATE -> stringResource(R.string.security_timeout_immediate)
    LockTimeout.SECONDS_30 -> stringResource(R.string.security_timeout_30s)
    LockTimeout.MINUTE_1 -> stringResource(R.string.security_timeout_1m)
    LockTimeout.MINUTES_5 -> stringResource(R.string.security_timeout_5m)
}

@Composable
internal fun ObserveMoreEvents(events: SharedFlow<MoreOneShotEvent>, snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val shareChooserTitle = stringResource(R.string.pdf_share_chooser_title)
    val noPdfViewerText = stringResource(R.string.pdf_snackbar_no_viewer)
    val pinEnabledText = stringResource(R.string.security_snackbar_pin_enabled)
    val pinChangedText = stringResource(R.string.security_snackbar_pin_changed)
    val pinDisabledText = stringResource(R.string.security_snackbar_pin_disabled)
    val pdfErrorText = stringResource(R.string.pdf_snackbar_error)

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is MoreOneShotEvent.SharePdf -> launchSharePdfIntent(context, event.exported, shareChooserTitle)
                is MoreOneShotEvent.OpenPdf -> {
                    if (!launchOpenPdfIntent(context, event.exported)) {
                        snackbarHostState.showSnackbar(noPdfViewerText)
                    }
                }
                is MoreOneShotEvent.ShowPdfSaved -> {
                    snackbarHostState.showSnackbar(
                        resources.getString(R.string.pdf_snackbar_saved, event.pageCount)
                    )
                }
                is MoreOneShotEvent.ShowSnackbar -> {
                    val text = when (event.message) {
                        MoreSnackbarMessage.PIN_ENABLED -> pinEnabledText
                        MoreSnackbarMessage.PIN_CHANGED -> pinChangedText
                        MoreSnackbarMessage.PIN_DISABLED -> pinDisabledText
                        MoreSnackbarMessage.PDF_EXPORT_ERROR -> pdfErrorText
                    }
                    snackbarHostState.showSnackbar(text)
                }
            }
        }
    }
}

private fun launchSharePdfIntent(context: Context, exported: ExportedPdfFile, chooserTitle: String) {
    val contentUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        exported.file
    )
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, contentUri)
        putExtra(Intent.EXTRA_SUBJECT, exported.fileName)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(shareIntent, chooserTitle).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(chooser) }
}

private fun launchOpenPdfIntent(context: Context, exported: ExportedPdfFile): Boolean {
    val contentUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        exported.file
    )
    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(contentUri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching {
        context.startActivity(viewIntent)
        true
    }.getOrDefault(false)
}
