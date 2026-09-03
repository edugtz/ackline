package com.edu.ackline

import com.edu.ackline.pairing.FidRePairState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupStateTest {

    @Test
    fun restoredPairingStateDoesNotMarkRegistrationReady() {
        val restored = SetupUiState().withPairingState(
            FidRePairState(
                lastObservedFid = "B",
                rePairRequired = true,
            ),
        )

        assertEquals("B", restored.installationId)
        assertTrue(restored.rePairRequired)
        assertEquals(RegistrationState.Waiting, restored.registrationState)
    }
}
