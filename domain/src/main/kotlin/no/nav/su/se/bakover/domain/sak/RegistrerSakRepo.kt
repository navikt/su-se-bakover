package no.nav.su.se.bakover.domain.sak

interface RegistrerSakRepo {
    fun persister(hendelse: SakRegistrertHendelse)
}
