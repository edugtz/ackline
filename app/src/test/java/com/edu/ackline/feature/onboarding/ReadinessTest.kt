package com.edu.ackline.feature.onboarding

import com.edu.ackline.RegistrationState
import com.edu.ackline.SetupUiState
import org.junit.Assert.*
import org.junit.Test

class ReadinessTest {
    private val ready = SetupUiState(registrationState=RegistrationState.Ready, installationId="fixture", encryptionReady=true, hasConfirmedPairing=true, legacyBootstrapResolved=true, ackProvisioned=true, notificationGranted=true)
    @Test fun onlyCompleteReadinessAllowsListo() {
        assertTrue(ready.fullyReady)
        assertEquals(OnboardingCompletion.Ready, onboardingCompletion(ready, true))
        val incomplete = listOf(ready.copy(notificationGranted=false), ready.copy(encryptionReady=false), ready.copy(ackProvisioned=false), ready.copy(rePairRequired=true), ready.copy(registrationState=RegistrationState.Waiting), ready.copy(installationId=null))
        for (state in incomplete) {
            assertFalse(state.fullyReady)
            assertFalse(state.needsOnboarding)
            assertEquals(OnboardingCompletion.Incomplete, onboardingCompletion(state, true))
        }
        assertTrue(SetupUiState().needsOnboarding)
        assertTrue(ready.copy(hasConfirmedPairing=false).needsOnboarding)
        assertEquals(OnboardingCompletion.Pending, onboardingCompletion(ready, false))
        assertEquals(OnboardingCompletion.Pending, onboardingCompletion(ready.copy(hasConfirmedPairing=false), true))
    }
    @Test fun permissionRetryUsesRationaleAndSettingsWithoutPromptCounter() {
        assertEquals(PermissionAction.Request, permissionAction(false,false,true))
        assertEquals(PermissionAction.Request, permissionAction(true,true,true))
        assertEquals(PermissionAction.Settings, permissionAction(true,false,true))
        assertEquals(PermissionAction.Settings, permissionAction(false,false,false))
    }
}
