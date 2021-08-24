package no.nav.su.se.bakover.service

import io.kotest.matchers.shouldBe
import org.mockito.kotlin.argThat

/**
 * Using kotest matchers because of better error messages
 */
inline fun <reified T : Any> argShouldBe(expected: T): T {
    return argThat {
        this shouldBe expected
        true
    }
}

inline fun <reified T : Any> argThat(noinline predicate: (T) -> Unit): T {
    return argThat {
        predicate(this)
        true
    }
}
