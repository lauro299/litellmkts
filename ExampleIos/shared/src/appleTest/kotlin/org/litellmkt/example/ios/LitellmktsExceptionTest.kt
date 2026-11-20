package org.litellmkt.example.ios

import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import org.litellmkt.errors.ProviderHttpException
import org.litellmkt.errors.ProviderStreamException
import org.litellmkt.errors.ResponseParseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class LitellmktsExceptionTest {

    @Test
    fun httpErrorKeepsStatusAndProviderMessage() {
        val error = ProviderHttpException(status = 404, providerMessage = "model not found", body = "").toLitellmktsException()

        assertEquals(ErrorCategory.HTTP, error.category)
        assertEquals(404, error.httpStatus)
        assertEquals("HTTP 404: model not found", error.detail)
    }

    @Test
    fun providerStreamError() {
        val error = ProviderStreamException("overloaded").toLitellmktsException()

        assertEquals(ErrorCategory.PROVIDER, error.category)
        assertNull(error.httpStatus)
        assertEquals("overloaded", error.detail)
    }

    @Test
    fun parseError() {
        val error = ResponseParseException(SerializationException("bad")).toLitellmktsException()

        assertEquals(ErrorCategory.INVALID_RESPONSE, error.category)
    }

    @Test
    fun ioErrorIsNetwork() {
        assertEquals(ErrorCategory.NETWORK, IOException("connection refused").toLitellmktsException().category)
    }

    @Test
    fun illegalArgumentIsConfiguration() {
        val error = IllegalArgumentException("model is required").toLitellmktsException()

        assertEquals(ErrorCategory.CONFIGURATION, error.category)
        assertEquals("model is required", error.detail)
    }

    @Test
    fun anythingElseIsUnknown() {
        assertEquals(ErrorCategory.UNKNOWN, IllegalStateException("boom").toLitellmktsException().category)
    }

    @Test
    fun alreadyMappedErrorIsKept() {
        val error = LitellmktsException(ErrorCategory.NETWORK, null, "offline")

        assertSame(error, error.toLitellmktsException())
    }
}
