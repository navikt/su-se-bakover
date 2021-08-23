package no.nav.su.se.bakover.web

internal inline fun <reified T : Any> argThat(noinline predicate: (T) -> Unit): T {
    return org.mockito.kotlin.argThat {
        predicate(this)
        true
    }
}
