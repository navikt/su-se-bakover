package no.nav.su.se.bakover.service.personhendelser

import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.personhendelse.Personhendelse

interface PersonhendelseService {
    fun prosesserNyHendelse(fraOgMed: Måned, personhendelse: Personhendelse.IkkeTilknyttetSak)
    fun opprettOppgaverForPersonhendelser()
    fun dryRunPersonhendelser(fraOgMed: Måned, personhendelser: List<Personhendelse.IkkeTilknyttetSak>): DryrunResult
}
