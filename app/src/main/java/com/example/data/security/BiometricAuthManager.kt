package com.example.data.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executor

class BiometricAuthManager(private val context: Context) {

    private val biometricManager = BiometricManager.from(context)

    companion object {
        const val BIOMETRIC_SUCCESS = BiometricManager.BIOMETRIC_SUCCESS
        const val BIOMETRIC_ERROR_NONE_ENROLLED = BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
        const val BIOMETRIC_ERROR_NO_HARDWARE = BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
        const val BIOMETRIC_ERROR_HW_UNAVAILABLE = BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE
    }

    /**
     * Checks if biometric sensor is present, functional, and has prints/faces registered.
     */
    fun checkBiometricAvailability(): Int {
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
    }

    /**
     * Helper to verify if biometrics are fully available and ready to use.
     */
    fun isBiometricReady(): Boolean {
        return checkBiometricAvailability() == BIOMETRIC_SUCCESS
    }

    /**
     * Triggers the system fingerprint/face authentication prompt.
     */
    fun showBiometricPrompt(
        activity: FragmentActivity,
        title: String = "Secure Shield Verification",
        subtitle: String = "Authenticate to unlock",
        negativeButtonText: String = "Use Alternative Pass",
        onSuccess: (BiometricPrompt.AuthenticationResult) -> Unit,
        onError: (errorCode: Int, errString: CharSequence) -> Unit,
        onFailed: () -> Unit
    ) {
        try {
            val executor: Executor = ContextCompat.getMainExecutor(activity)

            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess(result)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errorCode, errString)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onFailed()
                }
            }

            val biometricPrompt = BiometricPrompt(activity, executor, callback)

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText(negativeButtonText)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()

            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            e.printStackTrace()
            onError(-1, "Biometric failed: ${e.localizedMessage}")
        }
    }
}
