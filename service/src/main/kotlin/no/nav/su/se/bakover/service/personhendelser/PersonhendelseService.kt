package no.nav.su.se.bakover.service.personhendelser

import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.personhendelse.Personhendelse

interface PersonhendelseService {
    fun prosesserNyHendelse(fraOgMed: Måned, personhendelse: Personhendelse.IkkeTilknyttetSak)

    /** For en folkeregisteridentifikator-hendelse fra PDL: lagre én rad per sak vi har for personidentene. */
    fun lagreFødselsnummerhendelseForBerørteSaker(personidenter: List<String>)

    /** Jobb-trigger: går gjennom ubehandlede rader, slår opp gjeldende fnr i PDL og oppdaterer sakens fnr hvis det er endret. */
    fun oppdaterFødselsnummerForUbehandledeHendelser()
    fun opprettOppgaverForPersonhendelser()
    fun dryRunPersonhendelser(fraOgMed: Måned, personhendelser: List<Personhendelse.IkkeTilknyttetSak>): DryrunResult
}
