package no.nav.su.se.bakover.domain.regulering

interface ReguleringKjøringRepo {
    fun lagre(kjøring: ReguleringKjøring)
    fun hent(): List<ReguleringKjøring>
}
