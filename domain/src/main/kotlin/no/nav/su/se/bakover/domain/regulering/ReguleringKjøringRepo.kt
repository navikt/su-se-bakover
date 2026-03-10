package no.nav.su.se.bakover.domain.regulering

interface ReguleringKjøringRepo {
    fun lagre(oppsummering: ReguleringKjøring)
}
