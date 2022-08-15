package no.nav.su.se.bakover.test

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
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

inline fun <reified T> requireType(any: Any): T {
    require(any is T) { "Feil type, forventet objekt av ${T::class}, men var ${any::class}" }
    return any
}

inline fun <reified T : Any> Any.shouldBeType(): T {
    return this.shouldBeTypeOf()
}
