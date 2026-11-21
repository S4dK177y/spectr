package app.spectr.mood.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.spectr.mood.R
import app.spectr.mood.domain.model.ThemeConfig
import app.spectr.mood.domain.model.ThemeMode
import app.spectr.mood.domain.model.ThemePalette
import app.spectr.mood.platform.SpectrLaunchRequest
import app.spectr.mood.platform.SpectrLaunchTarget
import app.spectr.mood.ui.diary.DiaryRoute
import app.spectr.mood.ui.diary.DiaryScreen
import app.spectr.mood.ui.diary.sampleDiaryPreviewState
import app.spectr.mood.ui.doctor.DoctorViewRoute
import app.spectr.mood.ui.guide.GuideScreen
import app.spectr.mood.ui.more.MoreRoute
import app.spectr.mood.ui.more.MoreScreen
import app.spectr.mood.ui.more.sampleMorePreviewState
import app.spectr.mood.ui.navigation.SpectrDestination
import app.spectr.mood.ui.overview.OverviewRoute
import app.spectr.mood.ui.overview.OverviewScreen
import app.spectr.mood.ui.overview.sampleOverviewPreviewState
import app.spectr.mood.ui.scales.ScalesRoute
import app.spectr.mood.ui.scales.ScalesScreen
import app.spectr.mood.ui.scales.sampleScalesPreviewState
import app.spectr.mood.ui.theme.SpectrTheme
import java.time.LocalDate

private enum class MoreOverlay {
    DOCTOR_MODE,
    GUIDE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpectrApp(
    themeConfig: ThemeConfig,
    onThemeModeChange: (ThemeMode) -> Unit,
    onThemePaletteChange: (ThemePalette) -> Unit,
    modifier: Modifier = Modifier,
    launchRequest: SpectrLaunchRequest? = null,
    onLaunchRequestConsumed: () -> Unit = {},
    onLockAppNow: () -> Unit = {},
    diaryContent: @Composable (SnackbarHostState, LocalDate?, () -> Unit, Long, () -> Unit) -> Unit =
        { hostState, requestedDate, onDateConsumed, openMomentId, onMomentConsumed ->
            DiaryRoute(
                snackbarHostState = hostState,
                requestedDate = requestedDate,
                onRequestedDateConsumed = onDateConsumed,
                openMomentRequestId = openMomentId,
                onOpenMomentRequestConsumed = onMomentConsumed
            )
        },
    overviewContent: @Composable ((LocalDate) -> Unit) -> Unit = { onOpenDiaryDate ->
        OverviewRoute(onOpenDiaryDate = onOpenDiaryDate)
    },
    scalesContent: @Composable (SnackbarHostState) -> Unit = { hostState ->
        ScalesRoute(snackbarHostState = hostState)
    },
    moreContent: @Composable (SnackbarHostState, () -> Unit, () -> Unit) -> Unit =
        { hostState, onOpenDoctorMode, onOpenGuide ->
            MoreRoute(
                themeConfig = themeConfig,
                onThemeModeChange = onThemeModeChange,
                onThemePaletteChange = onThemePaletteChange,
                onOpenDoctorMode = onOpenDoctorMode,
                onOpenGuide = onOpenGuide,
                onLockAppNow = onLockAppNow,
                snackbarHostState = hostState
            )
        }
) {
    var currentTab by rememberSaveable { mutableStateOf(SpectrDestination.DIARY) }
    var activeOverlay by rememberSaveable { mutableStateOf<MoreOverlay?>(null) }
    var requestedDiaryDateIso by rememberSaveable { mutableStateOf<String?>(null) }
    var openMomentRequestId by rememberSaveable { mutableLongStateOf(0L) }
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = activeOverlay != null) {
        activeOverlay = null
    }

    LaunchedEffect(launchRequest) {
        if (launchRequest != null) {
            activeOverlay = null
            when (launchRequest.target) {
                SpectrLaunchTarget.DAILY_SUMMARY -> {
                    requestedDiaryDateIso = LocalDate.now().toString()
                    currentTab = SpectrDestination.DIARY
                }
                SpectrLaunchTarget.NEW_MOMENT -> {
                    openMomentRequestId = launchRequest.id
                    currentTab = SpectrDestination.DIARY
                }
                SpectrLaunchTarget.SCALES -> {
                    currentTab = SpectrDestination.SCALES
                }
            }
            onLaunchRequestConsumed()
        }
    }

    if (activeOverlay != null) {
        SpectrMoreOverlayHost(
            overlay = activeOverlay,
            onClose = { activeOverlay = null },
            modifier = modifier
        )
        return
    }

    val openDiaryDate: (LocalDate) -> Unit = { date ->
        requestedDiaryDateIso = date.toString()
        currentTab = SpectrDestination.DIARY
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (currentTab == SpectrDestination.DIARY) {
                            stringResource(R.string.app_name)
                        } else {
                            stringResource(currentTab.labelRes)
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            )
        },
        bottomBar = {
            NavigationBar {
                SpectrDestination.entries.forEach { destination ->
                    val selected = destination == currentTab
                    val label = stringResource(destination.labelRes)
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentTab = destination },
                        icon = {
                            Icon(
                                imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = label
                            )
                        },
                        label = { Text(text = label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Crossfade(
            targetState = currentTab,
            label = "spectr_tab_crossfade",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { tab ->
            when (tab) {
                SpectrDestination.DIARY -> diaryContent(
                    snackbarHostState,
                    requestedDiaryDateIso?.let(LocalDate::parse),
                    { requestedDiaryDateIso = null },
                    openMomentRequestId,
                    { openMomentRequestId = 0L }
                )
                SpectrDestination.OVERVIEW -> overviewContent(openDiaryDate)
                SpectrDestination.SCALES -> scalesContent(snackbarHostState)
                SpectrDestination.MORE -> moreContent(
                    snackbarHostState,
                    { activeOverlay = MoreOverlay.DOCTOR_MODE },
                    { activeOverlay = MoreOverlay.GUIDE }
                )
            }
        }
    }
}

@Composable
private fun SpectrMoreOverlayHost(overlay: MoreOverlay?, onClose: () -> Unit, modifier: Modifier = Modifier) {
    when (overlay) {
        MoreOverlay.DOCTOR_MODE -> DoctorViewRoute(onClose = onClose, modifier = modifier)
        MoreOverlay.GUIDE -> GuideScreen(onClose = onClose, modifier = modifier)
        null -> Unit
    }
}

@Preview(showBackground = true)
@Composable
private fun SpectrAppLightPreview() {
    val config = ThemeConfig(mode = ThemeMode.LIGHT, palette = ThemePalette.SOFT_SLATE)
    SpectrTheme(themeConfig = config) {
        SpectrApp(
            themeConfig = config,
            onThemeModeChange = {},
            onThemePaletteChange = {},
            diaryContent = { _, _, _, _, _ -> DiaryScreen(state = sampleDiaryPreviewState(), onIntent = {}) },
            overviewContent = {
                OverviewScreen(state = sampleOverviewPreviewState(), onIntent = {}, onOpenDiaryDate = {})
            },
            scalesContent = { ScalesScreen(state = sampleScalesPreviewState(), onIntent = {}) },
            moreContent = { _, _, _ ->
                MoreScreen(
                    state = sampleMorePreviewState(),
                    themeConfig = config,
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
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SpectrAppAmoledPreview() {
    val config = ThemeConfig(mode = ThemeMode.AMOLED, palette = ThemePalette.WARM_PAPER)
    SpectrTheme(themeConfig = config) {
        SpectrApp(
            themeConfig = config,
            onThemeModeChange = {},
            onThemePaletteChange = {},
            diaryContent = { _, _, _, _, _ -> DiaryScreen(state = sampleDiaryPreviewState(), onIntent = {}) },
            overviewContent = {
                OverviewScreen(state = sampleOverviewPreviewState(), onIntent = {}, onOpenDiaryDate = {})
            },
            scalesContent = { ScalesScreen(state = sampleScalesPreviewState(), onIntent = {}) },
            moreContent = { _, _, _ ->
                MoreScreen(
                    state = sampleMorePreviewState(),
                    themeConfig = config,
                    canPinWidget = false,
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
        )
    }
}
