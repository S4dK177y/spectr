package app.spectr.mood.ui.more

import android.Manifest
import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.spectr.mood.R
import app.spectr.mood.domain.model.ReminderConfig
import app.spectr.mood.domain.model.ThemeConfig
import app.spectr.mood.domain.model.ThemeMode
import app.spectr.mood.domain.model.ThemePalette
import app.spectr.mood.domain.reminder.DailyReminderSchedule
import app.spectr.mood.domain.reminder.ScalesReminderSchedule
import app.spectr.mood.platform.widget.MoodWidgetReceiver
import app.spectr.mood.ui.pdf.PdfExportBottomSheet
import app.spectr.mood.ui.security.PinSetupDialog
import app.spectr.mood.ui.theme.SpectrTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun MoreRoute(
    themeConfig: ThemeConfig,
    onThemeModeChange: (ThemeMode) -> Unit,
    onThemePaletteChange: (ThemePalette) -> Unit,
    onOpenDoctorMode: () -> Unit,
    onOpenGuide: () -> Unit,
    onLockAppNow: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    onOpenOnboardingTour: () -> Unit = {},
    viewModel: MoreViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val pinRequestedText = stringResource(R.string.widget_pin_requested_snackbar)

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            viewModel.exportPdfToDestinationUri(uri)
        }
    }

    ObserveMoreEvents(events = viewModel.events, snackbarHostState = snackbarHostState)

    val refreshPermissions = {
        viewModel.onIntent(
            MoreIntent.RefreshSystemPermissions(
                notificationsGranted = isNotificationPermissionGranted(context),
                canScheduleExactAlarms = isExactAlarmGranted(context)
            )
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        refreshPermissions()
    }

    DisposableEffect(lifecycleOwner, context) {
        refreshPermissions()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val requestNotifications: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openNotificationSettings(context)
        }
    }

    val requestExactAlarms: () -> Unit = {
        openExactAlarmSettings(context)
    }

    val canPinWidget = isPinWidgetSupported(context)
    val onPinWidget: () -> Unit = {
        val manager = AppWidgetManager.getInstance(context)
        val provider = ComponentName(context, MoodWidgetReceiver::class.java)
        if (manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(provider, null, null)
            coroutineScope.launch {
                snackbarHostState.showSnackbar(pinRequestedText)
            }
        }
    }

    MoreScreen(
        state = state,
        themeConfig = themeConfig,
        canPinWidget = canPinWidget,
        onIntent = { intent ->
            if (intent is MoreIntent.SetDailyReminderEnabled && intent.enabled && !state.notificationsGranted) {
                requestNotifications()
            } else if (intent is MoreIntent.SetScalesReminderEnabled && intent.enabled && !state.notificationsGranted) {
                requestNotifications()
            }
            viewModel.onIntent(intent)
        },
        onVerifyCurrentPin = viewModel::verifyCurrentPin,
        onSavePdfToFileClick = { savePdfLauncher.launch(viewModel.suggestedPdfFileName()) },
        onOpenDoctorMode = onOpenDoctorMode,
        onOpenGuide = onOpenGuide,
        onOpenOnboardingTour = onOpenOnboardingTour,
        onLockAppNow = onLockAppNow,
        onThemeModeChange = onThemeModeChange,
        onThemePaletteChange = onThemePaletteChange,
        onRequestNotificationPermission = requestNotifications,
        onRequestExactAlarmPermission = requestExactAlarms,
        onPinWidgetClick = onPinWidget,
        modifier = modifier
    )
}

@Composable
fun MoreScreen(
    state: MoreUiState,
    themeConfig: ThemeConfig,
    canPinWidget: Boolean,
    onIntent: (MoreIntent) -> Unit,
    onVerifyCurrentPin: suspend (String) -> Boolean,
    onSavePdfToFileClick: () -> Unit,
    onOpenDoctorMode: () -> Unit,
    onOpenGuide: () -> Unit,
    onLockAppNow: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onThemePaletteChange: (ThemePalette) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestExactAlarmPermission: () -> Unit,
    onPinWidgetClick: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenOnboardingTour: () -> Unit = {}
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "doctor_tools_card", contentType = "doctor_tools") {
            DoctorToolsAndGuideCard(
                onOpenPdfExport = { onIntent(MoreIntent.OpenPdfSheet) },
                onOpenDoctorMode = onOpenDoctorMode,
                onOpenGuide = onOpenGuide,
                onOpenOnboardingTour = onOpenOnboardingTour
            )
        }

        item(key = "security_card", contentType = "security") {
            LocalSecurityCard(
                config = state.securityConfig,
                biometricAvailability = state.biometricAvailability,
                onIntent = onIntent,
                onLockNowClick = onLockAppNow
            )
        }

        item(key = "reminders_card", contentType = "reminders") {
            RemindersCard(
                state = state,
                onIntent = onIntent,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onRequestExactAlarmPermission = onRequestExactAlarmPermission
            )
        }

        item(key = "widget_card", contentType = "widget") {
            WidgetCard(
                canPinWidget = canPinWidget,
                onPinWidgetClick = onPinWidgetClick
            )
        }

        item(key = "theme_card", contentType = "theme") {
            ThemeSettingsCard(
                themeConfig = themeConfig,
                onThemeModeChange = onThemeModeChange,
                onThemePaletteChange = onThemePaletteChange
            )
        }

        item(key = "privacy_card", contentType = "privacy") {
            PrivacyBadgeCard()
        }
    }

    if (state.isTimePickerOpen) {
        ReminderTimePickerDialog(
            initialHour = state.reminderConfig.safeDailyHour,
            initialMinute = state.reminderConfig.safeDailyMinute,
            onConfirm = { hour, minute -> onIntent(MoreIntent.UpdateDailyReminderTime(hour, minute)) },
            onDismiss = { onIntent(MoreIntent.DismissDailyTimePicker) }
        )
    }

    val pinMode = state.pinDialogMode
    if (pinMode != null) {
        PinSetupDialog(
            mode = pinMode,
            onVerifyCurrentPin = onVerifyCurrentPin,
            onSaveNewPin = { pin -> onIntent(MoreIntent.SaveNewPin(pin)) },
            onVerifiedDisable = { onIntent(MoreIntent.ConfirmDisablePinLock) },
            onDismiss = { onIntent(MoreIntent.DismissPinDialog) }
        )
    }

    if (state.pdfSheet.isOpen) {
        PdfExportBottomSheet(
            state = state.pdfSheet,
            onSelectPreset = { onIntent(MoreIntent.SelectPdfPeriodPreset(it)) },
            onChangeCustomStart = { onIntent(MoreIntent.UpdatePdfCustomStart(it)) },
            onChangeCustomEnd = { onIntent(MoreIntent.UpdatePdfCustomEnd(it)) },
            onToggleDailyTable = { onIntent(MoreIntent.SetPdfIncludeDailyTable(it)) },
            onToggleNotes = { onIntent(MoreIntent.SetPdfIncludePersonalNotes(it)) },
            onShareClick = { onIntent(MoreIntent.GenerateAndSharePdf) },
            onOpenClick = { onIntent(MoreIntent.GenerateAndOpenPdf) },
            onSaveToFileClick = onSavePdfToFileClick,
            onDismiss = { onIntent(MoreIntent.DismissPdfSheet) }
        )
    }
}

@Composable
private fun RemindersCard(
    state: MoreUiState,
    onIntent: (MoreIntent) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestExactAlarmPermission: () -> Unit
) {
    val anyReminderEnabled = state.reminderConfig.dailyEnabled || state.reminderConfig.scalesEnabled
    val formattedDailyTime = formatTimeHm(
        hour = state.reminderConfig.safeDailyHour,
        minute = state.reminderConfig.safeDailyMinute
    )

    Card(
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
                    imageVector = Icons.Outlined.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.reminders_section_title),
                    style = MaterialTheme.typography.titleLarge
                )
            }

            if (anyReminderEnabled && !state.notificationsGranted) {
                Spacer(modifier = Modifier.height(14.dp))
                PermissionAlertBanner(
                    icon = Icons.Outlined.NotificationsOff,
                    title = stringResource(R.string.reminders_permission_notifications_title),
                    body = stringResource(R.string.reminders_permission_notifications_body),
                    actionLabel = stringResource(R.string.reminders_permission_notifications_action),
                    onAction = onRequestNotificationPermission
                )
            }

            if (anyReminderEnabled && !state.canScheduleExactAlarms) {
                Spacer(modifier = Modifier.height(10.dp))
                PermissionAlertBanner(
                    icon = Icons.Outlined.Schedule,
                    title = stringResource(R.string.reminders_permission_exact_title),
                    body = stringResource(R.string.reminders_permission_exact_body),
                    actionLabel = stringResource(R.string.reminders_permission_exact_action),
                    onAction = onRequestExactAlarmPermission
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            ReminderToggleRow(
                title = stringResource(R.string.reminders_daily_title),
                subtitle = stringResource(R.string.reminders_daily_subtitle),
                checked = state.reminderConfig.dailyEnabled,
                onCheckedChange = { onIntent(MoreIntent.SetDailyReminderEnabled(it)) }
            )

            AnimatedVisibility(visible = state.reminderConfig.dailyEnabled) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onIntent(MoreIntent.OpenDailyTimePicker) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AccessTime,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = stringResource(R.string.reminders_daily_time_label),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Text(
                                text = formattedDailyTime,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    DailyScheduleStatusText(
                        state = state,
                        formattedDailyTime = formattedDailyTime,
                        onClearSnooze = { onIntent(MoreIntent.ClearDailySnooze) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(14.dp))

            ReminderToggleRow(
                title = stringResource(R.string.reminders_scales_title),
                subtitle = stringResource(R.string.reminders_scales_subtitle),
                checked = state.reminderConfig.scalesEnabled,
                onCheckedChange = { onIntent(MoreIntent.SetScalesReminderEnabled(it)) }
            )

            val nextScales = state.nextScalesSchedule
            if (state.reminderConfig.scalesEnabled && nextScales != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.reminders_scales_next_date,
                        formatShortDateRu(nextScales.targetDate),
                        formatEpochTime(nextScales.triggerAtEpochMs)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(16.dp)
                )
                Text(
                    text = stringResource(R.string.reminders_lockscreen_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DailyScheduleStatusText(state: MoreUiState, formattedDailyTime: String, onClearSnooze: () -> Unit) {
    val snoozeMs = state.activeSnoozeEpochMs
    if (snoozeMs != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.reminders_daily_snoozed_until, formatEpochTime(snoozeMs)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
            TextButton(onClick = onClearSnooze) {
                Text(text = stringResource(R.string.reminders_daily_snooze_clear))
            }
        }
        return
    }

    val statusText = when {
        state.isTodayEntryFilled -> stringResource(
            R.string.reminders_daily_filled_today_hint,
            formattedDailyTime
        )
        state.nextDailySchedule?.targetDate == state.today -> stringResource(
            R.string.reminders_daily_next_today,
            formattedDailyTime
        )
        else -> stringResource(
            R.string.reminders_daily_next_tomorrow,
            formattedDailyTime
        )
    }

    Text(
        text = statusText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ReminderToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
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
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun PermissionAlertBanner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.height(6.dp))
            TextButton(onClick = onAction) {
                Text(text = actionLabel)
            }
        }
    }
}

@Composable
private fun WidgetCard(canPinWidget: Boolean, onPinWidgetClick: () -> Unit) {
    Card(
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
                    imageVector = Icons.Outlined.Widgets,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.widget_section_title),
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.widget_section_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (canPinWidget) {
                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = onPinWidgetClick,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Widgets,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.widget_section_pin_button))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.widget_section_manual_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.reminders_daily_time_picker_title)) },
        text = { TimePicker(state = timePickerState) },
        confirmButton = {
            TextButton(onClick = { onConfirm(timePickerState.hour, timePickerState.minute) }) {
                Text(text = stringResource(R.string.diary_action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.diary_action_cancel))
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeSettingsCard(
    themeConfig: ThemeConfig,
    onThemeModeChange: (ThemeMode) -> Unit,
    onThemePaletteChange: (ThemePalette) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.theme_section_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.theme_mode_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = themeConfig.mode == mode,
                        onClick = { onThemeModeChange(mode) },
                        label = { Text(text = themeModeLabel(mode)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.theme_palette_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            ThemePalette.entries.forEach { palette ->
                PaletteRow(
                    title = themePaletteTitle(palette),
                    subtitle = themePaletteDesc(palette),
                    selected = themeConfig.palette == palette,
                    onClick = { onThemePaletteChange(palette) }
                )
            }
        }
    }
}

@Composable
private fun PaletteRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PrivacyBadgeCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Column {
                Text(
                    text = stringResource(R.string.privacy_badge_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.privacy_badge_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> stringResource(R.string.theme_mode_system)
    ThemeMode.LIGHT -> stringResource(R.string.theme_mode_light)
    ThemeMode.DARK -> stringResource(R.string.theme_mode_dark)
    ThemeMode.AMOLED -> stringResource(R.string.theme_mode_amoled)
}

@Composable
private fun themePaletteTitle(palette: ThemePalette): String = when (palette) {
    ThemePalette.DYNAMIC -> stringResource(R.string.theme_palette_dynamic)
    ThemePalette.MINIMAL -> stringResource(R.string.theme_palette_minimal)
    ThemePalette.WARM_PAPER -> stringResource(R.string.theme_palette_warm_paper)
    ThemePalette.SOFT_SLATE -> stringResource(R.string.theme_palette_soft_slate)
}

@Composable
private fun themePaletteDesc(palette: ThemePalette): String = when (palette) {
    ThemePalette.DYNAMIC -> stringResource(R.string.theme_palette_dynamic_desc)
    ThemePalette.MINIMAL -> stringResource(R.string.theme_palette_minimal_desc)
    ThemePalette.WARM_PAPER -> stringResource(R.string.theme_palette_warm_paper_desc)
    ThemePalette.SOFT_SLATE -> stringResource(R.string.theme_palette_soft_slate_desc)
}

private fun isNotificationPermissionGranted(context: Context): Boolean {
    val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    if (!enabled) return false
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

private fun isExactAlarmGranted(context: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: false
} else {
    true
}

private fun isPinWidgetSupported(context: Context): Boolean =
    AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = "package:${context.packageName}".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}

private fun formatTimeHm(hour: Int, minute: Int): String = String.format(Locale.US, "%02d:%02d", hour, minute)

private fun formatEpochTime(epochMs: Long): String {
    val localTime = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalTime()
    return formatTimeHm(localTime.hour, localTime.minute)
}

private val SHORT_DATE_RU_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("ru"))

private fun formatShortDateRu(date: LocalDate): String = date.format(SHORT_DATE_RU_FORMATTER)

internal fun sampleMorePreviewState(): MoreUiState {
    val previewDate = LocalDate.of(2026, 11, 21)
    return MoreUiState(
        today = previewDate,
        reminderConfig = ReminderConfig(
            dailyEnabled = true,
            dailyHour = 22,
            dailyMinute = 0,
            scalesEnabled = true
        ),
        isTodayEntryFilled = false,
        nextDailySchedule = DailyReminderSchedule(
            triggerAtEpochMs = previewDate.atTime(22, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            isSnoozed = false,
            targetDate = previewDate
        ),
        nextScalesSchedule = ScalesReminderSchedule(
            triggerAtEpochMs = previewDate.plusDays(14)
                .atTime(18, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli(),
            targetDate = previewDate.plusDays(14)
        ),
        canScheduleExactAlarms = true,
        notificationsGranted = true
    )
}

@Preview(showBackground = true)
@Composable
private fun MoreScreenPreview() {
    SpectrTheme(
        themeConfig = ThemeConfig(mode = ThemeMode.LIGHT, palette = ThemePalette.SOFT_SLATE)
    ) {
        MoreScreen(
            state = sampleMorePreviewState(),
            themeConfig = ThemeConfig(mode = ThemeMode.LIGHT, palette = ThemePalette.SOFT_SLATE),
            canPinWidget = true,
            onIntent = {},
            onVerifyCurrentPin = { true },
            onSavePdfToFileClick = {},
            onOpenDoctorMode = {},
            onOpenGuide = {},
            onLockAppNow = {},
            onThemeModeChange = {},
            onThemePaletteChange = {},
            onRequestNotificationPermission = {},
            onRequestExactAlarmPermission = {},
            onPinWidgetClick = {}
        )
    }
}
