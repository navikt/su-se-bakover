package no.nav.su.se.bakover.test

import io.kotest.assertions.fail
import org.mockito.kotlin.mock

/**
 * Failer dersom en metode blir kalt som ikke er stubbet.
 */
inline fun <reified T : Any> defaultMock() = mock<T>(defaultAnswer = { fail("Unstubbed method: ${it.method}") })
