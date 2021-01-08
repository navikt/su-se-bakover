package no.nav.su.se.bakover.client

internal inline fun <reified T : Any> argThat(noinline predicate: (T) -> Unit): T {
    return com.nhaarman.mockitokotlin2.argThat {
        predicate(this)
        true
    }
}
