package no.nav.su.se.bakover.domain

data class AktørId(
    private val aktørId: String
) {
    override fun toString() = aktørId
}
