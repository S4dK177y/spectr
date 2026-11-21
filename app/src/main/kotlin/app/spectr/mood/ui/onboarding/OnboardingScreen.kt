package app.spectr.mood.ui.onboarding

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.spectr.mood.R
import app.spectr.mood.domain.model.ThemeConfig
import app.spectr.mood.domain.model.ThemeMode
import app.spectr.mood.domain.model.ThemePalette
import app.spectr.mood.ui.components.OnboardingHeroIllustration
import app.spectr.mood.ui.components.SpectrBrandMark
import app.spectr.mood.ui.theme.SpectrTheme
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.absoluteValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Immutable
private data class OnboardingBullet(val icon: ImageVector, @param:StringRes val textRes: Int)

@Immutable
private data class OnboardingSlideSpec(
    val pageIndex: Int,
    @param:StringRes val tagRes: Int,
    @param:StringRes val titleRes: Int,
    @param:StringRes val bodyRes: Int,
    val bullets: List<OnboardingBullet>
)

private val ONBOARDING_SLIDES: List<OnboardingSlideSpec> = listOf(
    OnboardingSlideSpec(
        pageIndex = 0,
        tagRes = R.string.onboarding_p1_tag,
        titleRes = R.string.onboarding_p1_title,
        bodyRes = R.string.onboarding_p1_body,
        bullets = listOf(
            OnboardingBullet(Icons.Outlined.EditNote, R.string.onboarding_p1_bullet_1),
            OnboardingBullet(Icons.Outlined.Schedule, R.string.onboarding_p1_bullet_2),
            OnboardingBullet(Icons.Outlined.CalendarMonth, R.string.onboarding_p1_bullet_3)
        )
    ),
    OnboardingSlideSpec(
        pageIndex = 1,
        tagRes = R.string.onboarding_p2_tag,
        titleRes = R.string.onboarding_p2_title,
        bodyRes = R.string.onboarding_p2_body,
        bullets = listOf(
            OnboardingBullet(Icons.Outlined.Bedtime, R.string.onboarding_p2_bullet_1),
            OnboardingBullet(Icons.Outlined.Tune, R.string.onboarding_p2_bullet_2),
            OnboardingBullet(Icons.Outlined.Assessment, R.string.onboarding_p2_bullet_3)
        )
    ),
    OnboardingSlideSpec(
        pageIndex = 2,
        tagRes = R.string.onboarding_p3_tag,
        titleRes = R.string.onboarding_p3_title,
        bodyRes = R.string.onboarding_p3_body,
        bullets = listOf(
            OnboardingBullet(Icons.Outlined.CalendarMonth, R.string.onboarding_p3_bullet_1),
            OnboardingBullet(Icons.Outlined.Assessment, R.string.onboarding_p3_bullet_2),
            OnboardingBullet(Icons.Outlined.Check, R.string.onboarding_p3_bullet_3)
        )
    ),
    OnboardingSlideSpec(
        pageIndex = 3,
        tagRes = R.string.onboarding_p4_tag,
        titleRes = R.string.onboarding_p4_title,
        bodyRes = R.string.onboarding_p4_body,
        bullets = listOf(
            OnboardingBullet(Icons.Outlined.PictureAsPdf, R.string.onboarding_p4_bullet_1),
            OnboardingBullet(Icons.Outlined.Shield, R.string.onboarding_p4_bullet_2),
            OnboardingBullet(Icons.Outlined.NotificationsActive, R.string.onboarding_p4_bullet_3)
        )
    )
)

/**
 * Welcome / Onboarding screen presenting Spectr's 4 core functional pillars with custom
 * animated vector artwork (zero emojis) and Predictive Back Gesture support.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit, modifier: Modifier = Modifier, isFirstLaunch: Boolean = true) {
    val pagerState = rememberPagerState(pageCount = { ONBOARDING_SLIDES.size })
    val scope = rememberCoroutineScope()
    var backGestureProgress by remember { mutableFloatStateOf(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "onboarding_vector_pulse")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "onboarding_pulse_phase"
    )

    PredictiveBackHandler(enabled = pagerState.currentPage > 0 || !isFirstLaunch) { progress: Flow<BackEventCompat> ->
        try {
            progress.collect { backEvent ->
                backGestureProgress = backEvent.progress
            }
            backGestureProgress = 0f
            if (pagerState.currentPage > 0) {
                pagerState.animateScrollToPage(pagerState.currentPage - 1)
            } else {
                onFinish()
            }
        } catch (_: CancellationException) {
            backGestureProgress = 0f
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val scale = 1f - (backGestureProgress * 0.06f)
                scaleX = scale
                scaleY = scale
                alpha = 1f - (backGestureProgress * 0.15f)
            },
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            OnboardingTopHeader(
                currentPage = pagerState.currentPage,
                totalPages = ONBOARDING_SLIDES.size,
                isFirstLaunch = isFirstLaunch,
                onSkipOrClose = onFinish
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                val pageOffset = (
                    (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                    ).absoluteValue.coerceIn(0f, 1f)

                OnboardingSlidePage(
                    spec = ONBOARDING_SLIDES[page],
                    totalSlides = ONBOARDING_SLIDES.size,
                    pulsePhase = pulsePhase,
                    pageOffset = pageOffset
                )
            }

            OnboardingBottomControls(
                pagerState = pagerState,
                isFirstLaunch = isFirstLaunch,
                onPreviousClick = {
                    if (pagerState.currentPage > 0) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    }
                },
                onNextOrFinishClick = {
                    if (pagerState.currentPage < ONBOARDING_SLIDES.lastIndex) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        onFinish()
                    }
                }
            )
        }
    }
}

@Composable
private fun OnboardingTopHeader(currentPage: Int, totalPages: Int, isFirstLaunch: Boolean, onSkipOrClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Box(modifier = Modifier.padding(6.dp)) {
                    SpectrBrandMark()
                }
            }
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        val showSkipButton = !isFirstLaunch || currentPage < totalPages - 1
        AnimatedVisibility(
            visible = showSkipButton,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180))
        ) {
            TextButton(onClick = onSkipOrClose) {
                Text(
                    text = stringResource(
                        if (isFirstLaunch) R.string.onboarding_skip else R.string.onboarding_close
                    )
                )
            }
        }
    }
}

@Composable
private fun OnboardingSlidePage(spec: OnboardingSlideSpec, totalSlides: Int, pulsePhase: Float, pageOffset: Float) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
            .graphicsLayer {
                val scale = 1f - (pageOffset * 0.08f)
                scaleX = scale
                scaleY = scale
                alpha = 1f - (pageOffset * 0.35f)
            },
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            shape = RoundedCornerShape(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                OnboardingHeroIllustration(
                    pageIndex = spec.pageIndex,
                    pulsePhase = pulsePhase,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = stringResource(
                        R.string.onboarding_step_badge,
                        spec.pageIndex + 1,
                        totalSlides
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = stringResource(spec.tagRes),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Text(
            text = stringResource(spec.titleRes),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = stringResource(spec.bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                spec.bullets.forEach { bullet ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = bullet.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            text = stringResource(bullet.textRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingBottomControls(
    pagerState: PagerState,
    isFirstLaunch: Boolean,
    onPreviousClick: () -> Unit,
    onNextOrFinishClick: () -> Unit
) {
    val isLastPage = pagerState.currentPage == ONBOARDING_SLIDES.lastIndex

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OnboardingPageIndicator(
            pageCount = ONBOARDING_SLIDES.size,
            currentPage = pagerState.currentPage
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(visible = pagerState.currentPage > 0) {
                OutlinedButton(
                    onClick = onPreviousClick,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.onboarding_back),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.onboarding_back))
                }
            }

            Button(
                onClick = onNextOrFinishClick,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                AnimatedContent(
                    targetState = isLastPage,
                    transitionSpec = {
                        fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                    },
                    label = "onboarding_cta_content"
                ) { last ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        val ctaRes = when {
                            !last -> R.string.onboarding_next
                            isFirstLaunch -> R.string.onboarding_start
                            else -> R.string.onboarding_done
                        }
                        Text(
                            text = stringResource(ctaRes),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = if (last) {
                                Icons.Outlined.Check
                            } else {
                                Icons.AutoMirrored.Rounded.ArrowForward
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageIndicator(pageCount: Int, currentPage: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            val width by animateDpAsState(
                targetValue = if (selected) 28.dp else 8.dp,
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                label = "indicator_width"
            )
            val color by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                animationSpec = tween(durationMillis = 240),
                label = "indicator_color"
            )
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingScreenLightPreview() {
    SpectrTheme(
        themeConfig = ThemeConfig(mode = ThemeMode.LIGHT, palette = ThemePalette.SOFT_SLATE)
    ) {
        OnboardingScreen(onFinish = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingScreenAmoledPreview() {
    SpectrTheme(
        themeConfig = ThemeConfig(mode = ThemeMode.AMOLED, palette = ThemePalette.WARM_PAPER)
    ) {
        OnboardingScreen(onFinish = {}, isFirstLaunch = false)
    }
}
