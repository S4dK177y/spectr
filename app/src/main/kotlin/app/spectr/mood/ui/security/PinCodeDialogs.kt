package app.spectr.mood.ui.security

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.spectr.mood.R
import app.spectr.mood.security.PinHashManager
import kotlinx.coroutines.launch

/**
 * Flow mode for the in-app PIN management dialog.
 */
enum class PinDialogMode {
    CREATE_NEW,
    VERIFY_TO_DISABLE,
    CHANGE_EXISTING
}

internal enum class PinStep {
    VERIFY_CURRENT,
    ENTER_NEW,
    CONFIRM_NEW
}

@Composable
fun PinSetupDialog(
    mode: PinDialogMode,
    onVerifyCurrentPin: suspend (String) -> Boolean,
    onSaveNewPin: (String) -> Unit,
    onVerifiedDisable: () -> Unit,
    onDismiss: () -> Unit
) {
    val initialStep = if (mode == PinDialogMode.CREATE_NEW) {
        PinStep.ENTER_NEW
    } else {
        PinStep.VERIFY_CURRENT
    }
    var step by remember(mode) { mutableStateOf(initialStep) }
    var digits by remember(mode, step) { mutableStateOf("") }
    var firstNewPin by remember(mode) { mutableStateOf("") }
    var errorMessage by remember(mode) { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val wrongPinMsg = stringResource(R.string.pin_wrong_error)
    val mismatchMsg = stringResource(R.string.pin_setup_mismatch_error)

    val handleCompletePin: (String) -> Unit = { completed ->
        when (step) {
            PinStep.VERIFY_CURRENT -> {
                isBusy = true
                scope.launch {
                    val ok = onVerifyCurrentPin(completed)
                    isBusy = false
                    if (!ok) {
                        digits = ""
                        errorMessage = wrongPinMsg
                    } else if (mode == PinDialogMode.VERIFY_TO_DISABLE) {
                        onVerifiedDisable()
                    } else {
                        errorMessage = null
                        step = PinStep.ENTER_NEW
                        digits = ""
                    }
                }
            }
            PinStep.ENTER_NEW -> {
                firstNewPin = completed
                digits = ""
                errorMessage = null
                step = PinStep.CONFIRM_NEW
            }
            PinStep.CONFIRM_NEW -> {
                if (completed == firstNewPin) {
                    onSaveNewPin(completed)
                } else {
                    firstNewPin = ""
                    digits = ""
                    errorMessage = mismatchMsg
                    step = PinStep.ENTER_NEW
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = pinStepTitle(step)) },
        text = {
            PinDialogContent(
                subtitle = pinStepSubtitle(step, mode),
                digits = digits,
                errorMessage = errorMessage,
                isBusy = isBusy,
                onDigit = { digit ->
                    if (digits.length < PinHashManager.PIN_LENGTH) {
                        val next = digits + digit
                        digits = next
                        errorMessage = null
                        if (next.length == PinHashManager.PIN_LENGTH) {
                            handleCompletePin(next)
                        }
                    }
                },
                onBackspace = { if (digits.isNotEmpty()) digits = digits.dropLast(1) }
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.diary_action_cancel))
            }
        }
    )
}

@Composable
private fun PinDialogContent(
    subtitle: String,
    digits: String,
    errorMessage: String?,
    isBusy: Boolean,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        PinDotsRow(
            enteredLength = digits.length,
            totalLength = PinHashManager.PIN_LENGTH,
            isError = errorMessage != null
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = errorMessage.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.height(18.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        PinNumericKeypad(
            enabled = !isBusy,
            showBiometric = false,
            onDigit = onDigit,
            onBackspace = onBackspace,
            onBiometricClick = {}
        )
    }
}

@Composable
private fun pinStepTitle(step: PinStep): String = when (step) {
    PinStep.VERIFY_CURRENT -> stringResource(R.string.pin_verify_current_title)
    PinStep.ENTER_NEW -> stringResource(R.string.pin_setup_create_title)
    PinStep.CONFIRM_NEW -> stringResource(R.string.pin_setup_confirm_title)
}

@Composable
private fun pinStepSubtitle(step: PinStep, mode: PinDialogMode): String = when (step) {
    PinStep.VERIFY_CURRENT -> if (mode == PinDialogMode.VERIFY_TO_DISABLE) {
        stringResource(R.string.pin_verify_disable_subtitle)
    } else {
        stringResource(R.string.pin_verify_change_subtitle)
    }
    PinStep.ENTER_NEW -> stringResource(R.string.pin_setup_create_subtitle)
    PinStep.CONFIRM_NEW -> stringResource(R.string.pin_setup_confirm_subtitle)
}
