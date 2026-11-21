package app.spectr.mood.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.spectr.mood.R
import app.spectr.mood.domain.model.ThemeConfig
import app.spectr.mood.domain.model.ThemeMode
import app.spectr.mood.domain.model.ThemePalette
import app.spectr.mood.platform.SpectrLaunchRequest
import app.spectr.mood.platform.SpectrLaunchTarget
import app.spectr.mood.ui.components.SpectrBrandMark
import app.spectr.mood.ui.diary.DiaryRoute
import app.spectr.mood.ui.diary.DiaryScreen
import app.spectr.mood.ui.diary.sampleDiaryPreviewState
import app.spectr.mood.ui.doctor.DoctorViewRoute
import app.spectr.mood.ui.guide.GuideScreen
import app.spectr.mood.ui.more.MoreRoute
import app.spectr.mood.ui.more.MoreScreen
import app.spectr.mood.ui.more.sampleMorePreviewState
import app.spectr.mood.ui.navigation.SpectrDestination
import app.spectr.mood.ui.onboarding.OnboardingScreen
import app.spectr.mood.ui.overview.OverviewRoute
import app.spectr.mood.ui.overview.OverviewScreen
import app.spectr.mood.ui.overview.sampleOverviewPreviewState
import app.spectr.mood.ui.scales.ScalesRoute
import app.spectr.mood.ui.scales.ScalesScreen
import app.spectr.mood.ui.scales.sampleScalesPreviewState
import app.spectr.mood.ui.theme.SpectrTheme
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow

private enum class MoreOverlay {
    DOCTOR_MODE,
    GUIDE,
    ONBOARDING
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
    moreContent: @Composable (SnackbarHostState, () -> Unit, () -> Unit, () -> Unit) -> Unit =
        { hostState, onOpenDoctorMode, onOpenGuide, onOpenOnboardingTour ->
            MoreRoute(
                themeConfig = themeConfig,
                onThemeModeChange = onThemeModeChange,
                onThemePaletteChange = onThemePaletteChange,
                onOpenDoctorMode = onOpenDoctorMode,
                onOpenGuide = onOpenGuide,
                onOpenOnboardingTour = onOpenOnboardingTour,
                onLockAppNow = onLockAppNow,
                snackbarHostState = hostState
            )
        }
) {
    var currentTab by rememberSaveable { mutableStateOf(SpectrDestination.DIARY) }
    var activeOverlay by rememberSaveable { mutableStateOf<MoreOverlay?>(null) }
    var requestedDiaryDateIso by rememberSaveable { mutableStateOf<String?>(null) }
    var openMomentRequestId by rememberSaveable { mutableLongStateOf(0L) }
    var overlayBackProgress by remember { mutableFloatStateOf(0f) }
    var tabBackProgress by remember { mutableFloatStateOf(0f) }
    val snackbarHostState = remember { SnackbarHostState() }

    PredictiveBackHandler(enabled = activeOverlay != null) { progress: Flow<BackEventCompat> ->
        try {
            progress.collect { backEvent ->
                overlayBackProgress = backEvent.progress
            }
            overlayBackProgress = 0f
            activeOverlay = null
        } catch (_: CancellationException) {
            overlayBackProgress = 0f
        }
    }

    PredictiveBackHandler(
        enabled = activeOverlay == null && currentTab != SpectrDestination.DIARY
    ) { progress: Flow<BackEventCompat> ->
        try {
            progress.collect { backEvent ->
                tabBackProgress = backEvent.progress
            }
            tabBackProgress = 0f
            currentTab = SpectrDestination.DIARY
        } catch (_: CancellationException) {
            tabBackProgress = 0f
        }
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

    val openDiaryDate: (LocalDate) -> Unit = remember {
        { date ->
            requestedDiaryDateIso = date.toString()
            currentTab = SpectrDestination.DIARY
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = { SpectrTopAppBar(currentTab = currentTab) },
            bottomBar = {
                SpectrBottomNavigationBar(
                    currentTab = currentTab,
                    onSelectTab = { currentTab = it }
                )
            }
        ) { innerPadding ->
            Crossfade(
                targetState = currentTab,
                animationSpec = tween(durationMillis = 200),
                label = "spectr_tab_crossfade",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .graphicsLayer {
                        val scale = 1f - (tabBackProgress * 0.05f)
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - (tabBackProgress * 0.18f)
                    }
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
                        { activeOverlay = MoreOverlay.GUIDE },
                        { activeOverlay = MoreOverlay.ONBOARDING }
                    )
                }
            }
        }

        AnimatedContent(
            targetState = activeOverlay,
            transitionSpec = {
                (slideInHorizontally(tween(240)) { it / 5 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(200)) { it / 5 } + fadeOut(tween(180)))
            },
            label = "spectr_overlay_host"
        ) { overlay ->
            if (overlay != null) {
                SpectrMoreOverlayHost(
                    overlay = overlay,
                    onClose = { activeOverlay = null },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val scale = 1f - (overlayBackProgress * 0.07f)
                            scaleX = scale
                            scaleY = scale
                            alpha = 1f - (overlayBackProgress * 0.2f)
                            translationX = overlayBackProgress * 28.dp.toPx()
                        }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpectrTopAppBar(currentTab: SpectrDestination) {
    CenterAlignedTopAppBar(
        title = {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                },
                label = "spectr_topbar_title"
            ) { tab ->
                if (tab == SpectrDestination.DIARY) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SpectrBrandMark()
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Text(
                        text = stringResource(tab.labelRes),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    )
}

@Composable
private fun SpectrBottomNavigationBar(currentTab: SpectrDestination, onSelectTab: (SpectrDestination) -> Unit) {
    NavigationBar {
        SpectrDestination.entries.forEach { destination ->
            val selected = destination == currentTab
            val label = stringResource(destination.labelRes)
            NavigationBarItem(
                selected = selected,
                onClick = { onSelectTab(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) {
                            destination.selectedIcon
                        } else {
                            destination.unselectedIcon
                        },
                        contentDescription = label
                    )
                },
                label = { Text(text = label) }
            )
        }
    }
}

@Composable
private fun SpectrMoreOverlayHost(overlay: MoreOverlay, onClose: () -> Unit, modifier: Modifier = Modifier) {
    when (overlay) {
        MoreOverlay.DOCTOR_MODE -> DoctorViewRoute(onClose = onClose, modifier = modifier)
        MoreOverlay.GUIDE -> GuideScreen(onClose = onClose, modifier = modifier)
        MoreOverlay.ONBOARDING -> OnboardingScreen(
            isFirstLaunch = false,
            onFinish = onClose,
            modifier = modifier
        )
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
            moreContent = { _, _, _, _ ->
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
            moreContent = { _, _, _, _ ->
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
