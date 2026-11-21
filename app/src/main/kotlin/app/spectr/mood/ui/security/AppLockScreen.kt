package app.spectr.mood.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.spectr.mood.R
import app.spectr.mood.security.PinHashManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MAX_ATTEMPTS_BEFORE_COOLDOWN = 5
private const val COOLDOWN_SECONDS = 30

@Composable
fun AppLockScreen(
    biometricEnabled: Boolean,
    onVerifyPin: suspend (String) -> Boolean,
    onTriggerBiometric: () -> Unit,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
    biometricErrorMessage: String? = null
) {
    var digits by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var failedAttempts by remember { mutableIntStateOf(0) }
    var cooldownRemainingSec by remember { mutableIntStateOf(0) }
    var isVerifying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val wrongPinText = stringResource(R.string.pin_wrong_error)

    LaunchedEffect(cooldownRemainingSec) {
        if (cooldownRemainingSec > 0) {
            delay(1_000L)
            cooldownRemainingSec -= 1
        }
    }

    LaunchedEffect(biometricErrorMessage) {
        if (!biometricErrorMessage.isNullOrBlank()) {
            errorMessage = biometricErrorMessage
        }
    }

    val onDigitPress: (String) -> Unit = { digit ->
        if (!isVerifying && cooldownRemainingSec == 0 && digits.length < PinHashManager.PIN_LENGTH) {
            val next = digits + digit
            digits = next
            errorMessage = null
            if (next.length == PinHashManager.PIN_LENGTH) {
                isVerifying = true
                scope.launch {
                    val matched = onVerifyPin(next)
                    isVerifying = false
                    if (matched) {
                        digits = ""
                        failedAttempts = 0
                        onUnlocked()
                    } else {
                        digits = ""
                        failedAttempts += 1
                        if (failedAttempts >= MAX_ATTEMPTS_BEFORE_COOLDOWN) {
                            failedAttempts = 0
                            cooldownRemainingSec = COOLDOWN_SECONDS
                        } else {
                            errorMessage = wrongPinText
                        }
                    }
                }
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            AppLockHeaderSection(
                enteredLength = digits.length,
                errorMessage = errorMessage,
                cooldownRemainingSec = cooldownRemainingSec
            )

            PinNumericKeypad(
                enabled = !isVerifying && cooldownRemainingSec == 0,
                showBiometric = biometricEnabled,
                onDigit = onDigitPress,
                onBackspace = {
                    if (digits.isNotEmpty() && !isVerifying) {
                        digits = digits.dropLast(1)
                    }
                },
                onBiometricClick = onTriggerBiometric
            )
        }
    }
}

@Composable
private fun AppLockHeaderSection(enteredLength: Int, errorMessage: String?, cooldownRemainingSec: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.pin_lock_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.pin_lock_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        PinDotsRow(
            enteredLength = enteredLength,
            totalLength = PinHashManager.PIN_LENGTH,
            isError = errorMessage != null || cooldownRemainingSec > 0
        )

        Spacer(modifier = Modifier.height(14.dp))
        val feedback = if (cooldownRemainingSec > 0) {
            stringResource(R.string.pin_lock_cooldown_error, cooldownRemainingSec)
        } else {
            errorMessage
        }
        Text(
            text = feedback.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.height(20.dp)
        )
    }
}

@Composable
internal fun PinDotsRow(enteredLength: Int, totalLength: Int, isError: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalLength) { idx ->
            val filled = idx < enteredLength
            val fillColor = when {
                isError -> MaterialTheme.colorScheme.error
                filled -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surfaceContainerHighest
            }
            val borderColor = when {
                isError -> MaterialTheme.colorScheme.error
                filled -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outline
            }
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (filled) fillColor else MaterialTheme.colorScheme.background)
                    .border(width = 2.dp, color = borderColor, shape = CircleShape)
            )
        }
    }
}

@Composable
internal fun PinNumericKeypad(
    enabled: Boolean,
    showBiometric: Boolean,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onBiometricClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9")
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { digit ->
                    KeypadDigitButton(
                        digit = digit,
                        enabled = enabled,
                        onClick = { onDigit(digit) }
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBiometric) {
                KeypadIconButton(
                    icon = Icons.Outlined.Fingerprint,
                    contentDescription = stringResource(R.string.pin_lock_biometric_cd),
                    enabled = enabled,
                    onClick = onBiometricClick
                )
            } else {
                Spacer(modifier = Modifier.size(68.dp))
            }

            KeypadDigitButton(
                digit = "0",
                enabled = enabled,
                onClick = { onDigit("0") }
            )

            KeypadIconButton(
                icon = Icons.AutoMirrored.Outlined.Backspace,
                contentDescription = stringResource(R.string.pin_lock_backspace_cd),
                enabled = enabled,
                onClick = onBackspace
            )
        }
    }
}

@Composable
private fun KeypadDigitButton(digit: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = digit,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                }
            )
        }
    }
}

@Composable
private fun KeypadIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            },
            modifier = Modifier.size(28.dp)
        )
    }
}
