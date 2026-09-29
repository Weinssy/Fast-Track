package com.wein.fasttrack.utils

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow

object BiometricAuthManager {
    private const val GRACE_PERIOD_MILLIS = 60_000L
    private var lastUnlockedTimestamp: Long = 0L

    val isAppUnlocked = MutableStateFlow(false)

    fun canAuthenticate(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        val allowedTypes = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        return biometricManager.canAuthenticate(allowedTypes) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    markUnlocked()
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("Autentikasi gagal. Silakan coba lagi.")
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Buka Kunci Fast Track")
            .setSubtitle("Gunakan biometrik atau PIN/Pola untuk masuk")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    fun markUnlocked() {
        lastUnlockedTimestamp = System.currentTimeMillis()
        isAppUnlocked.value = true
    }

    fun checkSessionValidity() {
        val now = System.currentTimeMillis()
        if (now - lastUnlockedTimestamp > GRACE_PERIOD_MILLIS) {
            isAppUnlocked.value = false
        }
    }
}
