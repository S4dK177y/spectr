package app.spectr.mood.ui.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.spectr.mood.R
import app.spectr.mood.domain.model.ThemeConfig
import app.spectr.mood.ui.diary.SymptomType
import app.spectr.mood.ui.diary.formatSignedMood
import app.spectr.mood.ui.diary.moodDescription
import app.spectr.mood.ui.diary.symptomLabel
import app.spectr.mood.ui.diary.symptomWhenNotToMark
import app.spectr.mood.ui.diary.symptomWhenToMark
import app.spectr.mood.ui.theme.LocalMoodColors
import app.spectr.mood.ui.theme.MoodColorScale
import app.spectr.mood.ui.theme.SpectrTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.guide_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.guide_back_cd)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "guide_disclaimer") {
                GuideDisclaimerBanner()
            }

            item(key = "guide_mood_scale") {
                GuideMoodScaleCard()
            }

            item(key = "guide_rules") {
                GuideDiaryRulesCard()
            }

            item(key = "guide_symptoms") {
                GuideSymptomsCriteriaCard()
            }

            item(key = "guide_scales") {
                GuideClinicalScalesCard()
            }
        }
    }
}

@Composable
private fun GuideDisclaimerBanner() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(18.dp)
            )
            Text(
                text = stringResource(R.string.guide_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun GuideMoodScaleCard() {
    val moodColors = LocalMoodColors.current
    val levels = (MoodColorScale.MIN_MOOD..MoodColorScale.MAX_MOOD).toList()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.guide_section_mood_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.guide_section_mood_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                levels.forEach { score ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = moodColors.containerColor(score),
                            modifier = Modifier.width(42.dp)
                        ) {
                            Text(
                                text = formatSignedMood(score),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = moodColors.contentColor(score),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = moodDescription(score),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = moodReferenceAnchor(score),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideDiaryRulesCard() {
    val rules = listOf(
        R.string.guide_rule_overall_title to R.string.guide_rule_overall_body,
        R.string.guide_rule_death_title to R.string.guide_rule_death_body,
        R.string.guide_rule_sleep_title to R.string.guide_rule_sleep_body,
        R.string.guide_rule_moments_title to R.string.guide_rule_moments_body,
        R.string.guide_rule_mixed_title to R.string.guide_rule_mixed_body
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.guide_section_rules_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            rules.forEachIndexed { idx, (titleRes, bodyRes) ->
                if (idx > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(titleRes),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(bodyRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideSymptomsCriteriaCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.guide_section_symptoms_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            SymptomType.entries.forEachIndexed { idx, symptom ->
                if (idx > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = symptomLabel(symptom),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${stringResource(R.string.diary_help_when_mark)}: ${symptomWhenToMark(symptom)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${stringResource(R.string.diary_help_when_not_mark)}: ${symptomWhenNotToMark(symptom)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideClinicalScalesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.guide_section_scales_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.guide_scales_auto_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = stringResource(R.string.guide_scales_auto_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = stringResource(R.string.guide_scales_phq9_ranges),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.guide_scales_gad7_ranges),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.guide_scales_asrm_ranges),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun moodReferenceAnchor(score: Int): String = when (score.coerceIn(-5, 5)) {
    -5 -> stringResource(R.string.guide_mood_anchor_m5)
    -4 -> stringResource(R.string.guide_mood_anchor_m4)
    -3 -> stringResource(R.string.guide_mood_anchor_m3)
    -2 -> stringResource(R.string.guide_mood_anchor_m2)
    -1 -> stringResource(R.string.guide_mood_anchor_m1)
    0 -> stringResource(R.string.guide_mood_anchor_0)
    1 -> stringResource(R.string.guide_mood_anchor_p1)
    2 -> stringResource(R.string.guide_mood_anchor_p2)
    3 -> stringResource(R.string.guide_mood_anchor_p3)
    4 -> stringResource(R.string.guide_mood_anchor_p4)
    else -> stringResource(R.string.guide_mood_anchor_p5)
}

@Preview(showBackground = true)
@Composable
private fun GuideScreenPreview() {
    SpectrTheme(themeConfig = ThemeConfig()) {
        GuideScreen(onClose = {})
    }
}
