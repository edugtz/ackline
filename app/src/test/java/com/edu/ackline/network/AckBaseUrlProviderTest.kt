package com.edu.ackline.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AckBaseUrlProviderTest {

    @Test
    fun provisionedValueWinsOverBuildFallback() {
        val storage = InMemoryAckBaseUrlStorage()
        val provider = AckBaseUrlProvider("https://fallback.example", storage)

        assertEquals(
            AckBaseUrlProvider.SetResult.STORED,
            provider.setProvisionedBaseUrl("https://paired.example/"),
        )
        assertEquals("https://paired.example", provider.getBaseUrl())
    }

    @Test
    fun fallbackIsUsedWhenProvisionedValueIsAbsent() {
        val provider = AckBaseUrlProvider(
            "https://fallback.example",
            InMemoryAckBaseUrlStorage(),
        )

        assertEquals("https://fallback.example", provider.getBaseUrl())
    }

    @Test
    fun blankFallbackAndNoProvisionedValueRemainNotConfigured() {
        val provider = AckBaseUrlProvider("  ", InMemoryAckBaseUrlStorage())

        assertTrue(provider.getBaseUrl().isBlank())
    }

    @Test
    fun invalidProvisionedValuesAreRejectedBeforePersistence() {
        val storage = InMemoryAckBaseUrlStorage()
        val provider = AckBaseUrlProvider("https://fallback.example", storage)

        listOf(
            "",
            "http://example.com",
            "https://",
            "https:///path",
            "https://example.com?query=value",
            "https://example.com#fragment",
            "https://user:password@example.com",
        ).forEach { value ->
            assertEquals(
                AckBaseUrlProvider.SetResult.INVALID_URL,
                provider.setProvisionedBaseUrl(value),
            )
        }

        assertEquals("https://fallback.example", provider.getBaseUrl())
    }

    @Test
    fun provisionedValueSurvivesProviderRecreation() {
        val storage = InMemoryAckBaseUrlStorage()
        val firstProvider = AckBaseUrlProvider("https://fallback.example", storage)
        assertEquals(
            AckBaseUrlProvider.SetResult.STORED,
            firstProvider.setProvisionedBaseUrl("https://paired.example/api"),
        )

        val recreatedProvider = AckBaseUrlProvider("https://fallback.example", storage)

        assertEquals("https://paired.example/api", recreatedProvider.getBaseUrl())
    }

    @Test
    fun persistenceFailureDoesNotReportStored() {
        val storage = InMemoryAckBaseUrlStorage().apply { writesSucceed = false }
        val provider = AckBaseUrlProvider("https://fallback.example", storage)

        assertEquals(
            AckBaseUrlProvider.SetResult.PERSISTENCE_FAILED,
            provider.setProvisionedBaseUrl("https://paired.example"),
        )
        assertTrue(provider.clearProvisionedBaseUrl())
    }
}
