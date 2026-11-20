package app.spectr.mood.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import app.spectr.mood.R

/**
 * Top-level destinations for Spectr's 4-tab bottom navigation bar.
 */
enum class SpectrDestination(
    @param:StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DIARY(
        labelRes = R.string.tab_diary,
        selectedIcon = Icons.Rounded.EditNote,
        unselectedIcon = Icons.Outlined.EditNote
    ),
    OVERVIEW(
        labelRes = R.string.tab_overview,
        selectedIcon = Icons.Rounded.CalendarMonth,
        unselectedIcon = Icons.Outlined.CalendarMonth
    ),
    SCALES(
        labelRes = R.string.tab_scales,
        selectedIcon = Icons.Rounded.Assessment,
        unselectedIcon = Icons.Outlined.Assessment
    ),
    MORE(
        labelRes = R.string.tab_more,
        selectedIcon = Icons.Rounded.Tune,
        unselectedIcon = Icons.Outlined.Tune
    )
}
