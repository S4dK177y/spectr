package app.spectr.mood.ui

import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.spectr.mood.R
import app.spectr.mood.domain.model.ThemeConfig
import app.spectr.mood.domain.model.ThemeMode
import app.spectr.mood.domain.model.ThemePalette
import app.spectr.mood.ui.diary.DiaryRoute
import app.spectr.mood.ui.diary.DiaryScreen
import app.spectr.mood.ui.diary.sampleDiaryPreviewState
import app.spectr.mood.ui.navigation.SpectrDestination
import app.spectr.mood.ui.overview.OverviewRoute
import app.spectr.mood.ui.overview.OverviewScreen
import app.spectr.mood.ui.overview.sampleOverviewPreviewState
import app.spectr.mood.ui.scales.ScalesRoute
import app.spectr.mood.ui.scales.ScalesScreen
import app.spectr.mood.ui.scales.sampleScalesPreviewState
import app.spectr.mood.ui.theme.SpectrTheme
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpectrApp(
    themeConfig: ThemeConfig,
    onThemeModeChange: (ThemeMode) -> Unit,
    onThemePaletteChange: (ThemePalette) -> Unit,
    modifier: Modifier = Modifier,
    diaryContent: @Composable (SnackbarHostState, LocalDate?, () -> Unit) -> Unit =
        { hostState, requestedDate, onConsumed ->
            DiaryRoute(
                snackbarHostState = hostState,
                requestedDate = requestedDate,
                onRequestedDateConsumed = onConsumed
            )
        },
    overviewContent: @Composable ((LocalDate) -> Unit) -> Unit = { onOpenDiaryDate ->
        OverviewRoute(onOpenDiaryDate = onOpenDiaryDate)
    },
    scalesContent: @Composable (SnackbarHostState) -> Unit = { hostState ->
        ScalesRoute(snackbarHostState = hostState)
    }
) {
    var currentTab by rememberSaveable { mutableStateOf(SpectrDestination.DIARY) }
    var requestedDiaryDateIso by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

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
                    requestedDiaryDateIso?.let(LocalDate::parse)
                ) { requestedDiaryDateIso = null }
                SpectrDestination.OVERVIEW -> overviewContent(openDiaryDate)
                SpectrDestination.SCALES -> scalesContent(snackbarHostState)
                SpectrDestination.MORE -> MoreShellScreen(
                    themeConfig = themeConfig,
                    onThemeModeChange = onThemeModeChange,
                    onThemePaletteChange = onThemePaletteChange
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoreShellScreen(
    themeConfig: ThemeConfig,
    onThemeModeChange: (ThemeMode) -> Unit,
    onThemePaletteChange: (ThemePalette) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
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

        item {
            PrivacyBadgeCard()
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

@Preview(showBackground = true)
@Composable
private fun SpectrAppLightPreview() {
    SpectrTheme(
        themeConfig = ThemeConfig(mode = ThemeMode.LIGHT, palette = ThemePalette.SOFT_SLATE)
    ) {
        SpectrApp(
            themeConfig = ThemeConfig(mode = ThemeMode.LIGHT, palette = ThemePalette.SOFT_SLATE),
            onThemeModeChange = {},
            onThemePaletteChange = {},
            diaryContent = { _, _, _ -> DiaryScreen(state = sampleDiaryPreviewState(), onIntent = {}) },
            overviewContent = {
                OverviewScreen(state = sampleOverviewPreviewState(), onIntent = {}, onOpenDiaryDate = {})
            },
            scalesContent = { ScalesScreen(state = sampleScalesPreviewState(), onIntent = {}) }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SpectrAppAmoledPreview() {
    SpectrTheme(
        themeConfig = ThemeConfig(mode = ThemeMode.AMOLED, palette = ThemePalette.WARM_PAPER)
    ) {
        SpectrApp(
            themeConfig = ThemeConfig(mode = ThemeMode.AMOLED, palette = ThemePalette.WARM_PAPER),
            onThemeModeChange = {},
            onThemePaletteChange = {},
            diaryContent = { _, _, _ -> DiaryScreen(state = sampleDiaryPreviewState(), onIntent = {}) },
            overviewContent = {
                OverviewScreen(state = sampleOverviewPreviewState(), onIntent = {}, onOpenDiaryDate = {})
            },
            scalesContent = { ScalesScreen(state = sampleScalesPreviewState(), onIntent = {}) }
        )
    }
}
