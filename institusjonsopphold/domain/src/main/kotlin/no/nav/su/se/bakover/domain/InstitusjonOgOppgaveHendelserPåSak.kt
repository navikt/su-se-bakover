package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.extensions.whenever
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse

/**
 * Utilityklasse for å lage en felles tilstand for inst + oppgave-hendelser (siden tilstanden ikke er integrert i [no.nav.su.se.bakover.domain.Sak]
 */
data class InstitusjonOgOppgaveHendelserPåSak(
    private val instHendelser: InstitusjonsoppholdHendelserPåSak,
    private val oppgaveHendelser: List<OppgaveHendelse>,
) {

    init {
        instHendelser.map { it.sakId }.let {
            require(it.distinct().size == 1) {
                "Institusjonsoppholdhendelsene må være knyttet til samme sak, men var: $it"
            }
        }
        oppgaveHendelser.whenever(
            isEmpty = {},
            isNotEmpty = {
                require(instHendelser.map { it.sakId }.distinct().single() == it.map { it.sakId }.distinct().single()) {
                    "Institusjonsopphold hendelsene og oppgave hendelsene må være knyttet til samme sak"
                }
            },
        )
    }

    fun hentInstHendelserSomManglerOppgave(): List<InstitusjonsoppholdHendelse> {
        val relaterteHendelser = oppgaveHendelser.flatMap { it.relaterteHendelser }

        return instHendelser.filter { it.hendelseId !in relaterteHendelser }
    }

    fun sisteOppgaveId(): OppgaveId? {
        val relevanteOppgaveHendelser = oppgaveHendelser.filter { oppgave ->
            oppgave.relaterteHendelser.intersect(instHendelser.map { it.hendelseId }.toSet()).isNotEmpty()
        }

        return relevanteOppgaveHendelser.maxByOrNull { it.hendelsestidspunkt.instant }?.oppgaveId
    }
}
