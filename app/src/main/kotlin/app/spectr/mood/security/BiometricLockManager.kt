package app.spectr.mood.security

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device-level biometric readiness state compatible with Android 8.0+ (`minSdk = 26`).
 */
enum class BiometricAvailability {
    AVAILABLE,
    NONE_ENROLLED,
    UNAVAILABLE
}

/**
 * Pure abstraction for querying device biometric readiness in ViewModels and JVM tests.
 */
fun interface BiometricAvailabilityProvider {
    fun checkAvailability(): BiometricAvailability
}

/**
 * Native Android `BiometricPrompt` wrapper providing biometric unlock (API 28+) with clean fallback
 * to the local 4-digit PBKDF2 App PIN across all Android 8.0+ devices.
 */
@Singleton
class BiometricLockManager @Inject constructor(@param:ApplicationContext private val context: Context) :
    BiometricAvailabilityProvider {

    override fun checkAvailability(): BiometricAvailability = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> checkAvailabilityApi30()
        Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> checkAvailabilityApi29()
        Build.VERSION.SDK_INT == Build.VERSION_CODES.P -> checkAvailabilityApi28()
        else -> BiometricAvailability.UNAVAILABLE
    }

    fun authenticate(
        activity: Activity,
        title: String,
        subtitle: String,
        negativeButtonText: String,
        onSuccess: () -> Unit,
        onError: (String?) -> Unit
    ): CancellationSignal? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            onError(null)
            return null
        }
        return authenticateApi28Plus(
            activity = activity,
            title = title,
            subtitle = subtitle,
            negativeButtonText = negativeButtonText,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun checkAvailabilityApi30(): BiometricAvailability {
        val manager = context.getSystemService(BiometricManager::class.java)
            ?: return BiometricAvailability.UNAVAILABLE
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        return mapBiometricStatus(manager.canAuthenticate(authenticators))
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @Suppress("DEPRECATION")
    private fun checkAvailabilityApi29(): BiometricAvailability {
        val manager = context.getSystemService(BiometricManager::class.java)
            ?: return BiometricAvailability.UNAVAILABLE
        return mapBiometricStatus(manager.canAuthenticate())
    }

    private fun checkAvailabilityApi28(): BiometricAvailability {
        val hasFingerprint = context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
        return if (hasFingerprint) BiometricAvailability.AVAILABLE else BiometricAvailability.UNAVAILABLE
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun mapBiometricStatus(status: Int): BiometricAvailability = when (status) {
        BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NONE_ENROLLED
        else -> BiometricAvailability.UNAVAILABLE
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun authenticateApi28Plus(
        activity: Activity,
        title: String,
        subtitle: String,
        negativeButtonText: String,
        onSuccess: () -> Unit,
        onError: (String?) -> Unit
    ): CancellationSignal {
        val executor = ContextCompat.getMainExecutor(activity)
        val cancellationSignal = CancellationSignal()

        val builder = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButton(negativeButtonText, executor) { _, _ ->
                onError(null)
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setConfirmationRequired(false)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
        }

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                val isUserCancel = errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED ||
                    errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED
                onError(if (isUserCancel) null else errString.toString())
            }
        }

        runCatching {
            builder.build().authenticate(cancellationSignal, executor, callback)
        }.onFailure { throwable ->
            onError(throwable.localizedMessage)
        }

        return cancellationSignal
    }
}
