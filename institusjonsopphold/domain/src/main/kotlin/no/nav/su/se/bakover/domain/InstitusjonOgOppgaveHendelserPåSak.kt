package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.extensions.whenever
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse

/**
 * Denne klassen er for å gjøre funksjoner på tvers av inst + oppgave hendelser lettere
 */
data class InstitusjonOgOppgaveHendelserPåSak(
    private val instHendelser: InstitusjonsoppholdHendelserPåSak,
    private val oppgaveHendelser: List<OppgaveHendelse>,
) {

    init {
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
        return instHendelser.filter { it.hendelseId !in oppgaveHendelser.map { it.triggetAv } }
    }

    fun hentHendelserMedSammeOppholdId(oppholdId: OppholdId): Pair<List<InstitusjonsoppholdHendelse>, List<OppgaveHendelse>>? {
        return instHendelser.filter { it.eksterneHendelse.oppholdId == oppholdId }.whenever(
            isEmpty = {
                null
            },
            isNotEmpty = { instHendelserMedSammeOppholdId ->
                val oppgaveHendelserTilknyttetOppholdId = oppgaveHendelser.filter {
                    it.triggetAv in instHendelserMedSammeOppholdId.map { it.hendelseId }
                }
                instHendelserMedSammeOppholdId to oppgaveHendelserTilknyttetOppholdId
            },
        )
    }
}
