package no.nav.su.se.bakover.service.personhendelser

import no.nav.su.se.bakover.domain.personhendelse.Personhendelse

interface PersonhendelseService {
    fun prosesserNyHendelse(personhendelse: Personhendelse.IkkeTilknyttetSak)
    fun opprettOppgaverForPersonhendelser()
    fun dryRunPersonhendelser(personhendelser: List<Personhendelse.IkkeTilknyttetSak>): PersonhendelseServiceImpl.DryrunResult
}
