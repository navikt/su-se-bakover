package no.nav.su.se.bakover.test

import io.kotest.assertions.fail
import org.mockito.kotlin.KStubbing
import org.mockito.kotlin.mock

inline fun <reified T : Any> defaultMock() = mock<T>(defaultAnswer = { fail("Unstubbed method: ${it.method}") })

inline fun <reified T : Any> defaultMock(
    stubbing: KStubbing<T>.(T) -> Unit
): T {
    return mock(defaultAnswer = { fail("Unstubbed method: ${it.method}") }, stubbing = stubbing)
}
