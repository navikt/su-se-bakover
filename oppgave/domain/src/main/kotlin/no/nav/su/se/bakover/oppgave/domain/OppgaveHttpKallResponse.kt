package no.nav.su.se.bakover.oppgave.domain

import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId

/**
 * Vi ønsker å ha data fra http kallet inn i hendelsene våre.
 *
 * TODO - Denne er sauset på alle steder der oppgave service, og client brukes. Mulig vi må skille bedre på dette
 *
 * Mulig vi vi kan heller ersatte denne med at client og service returnere oppgaveHendelse
 */
data class OppgaveHttpKallResponse(
    val oppgaveId: OppgaveId,
    val oppgavetype: Oppgavetype,
    val requestBody: String,
    val response: String,
    val beskrivelse: String,
)
