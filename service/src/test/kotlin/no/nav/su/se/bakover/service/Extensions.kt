package no.nav.su.se.bakover.service

import io.kotest.matchers.shouldBe
import org.mockito.kotlin.argThat

/** TODO: DEPRECATED: Use the one in test-common instead */
inline fun <reified T : Any> argShouldBe(expected: T): T {
    return argThat {
        this shouldBe expected
        true
    }
}

/** TODO: DEPRECATED: Use the one in test-common instead */
inline fun <reified T : Any> argThat(noinline predicate: (T) -> Unit): T {
    return argThat {
        predicate(this)
        true
    }
}
