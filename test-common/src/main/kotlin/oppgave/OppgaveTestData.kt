package no.nav.su.se.bakover.test.oppgave

import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.oppgave.domain.OppgaveHttpKallResponse
import no.nav.su.se.bakover.oppgave.domain.Oppgavetype

val oppgaveId = OppgaveId("123")

fun nyOppgaveHttpKallResponse(
    oppgaveId: OppgaveId = no.nav.su.se.bakover.test.oppgave.oppgaveId,
    oppgavetype: Oppgavetype = Oppgavetype.BEHANDLE_SAK,
    request: String = "request",
    response: String = "response",
    beskrivelse: String = "beskrivelse",
    tilordnetRessurs: String? = "tilordnetRessurs",
): OppgaveHttpKallResponse = OppgaveHttpKallResponse(
    oppgaveId = oppgaveId,
    oppgavetype = oppgavetype,
    request = request,
    response = response,
    beskrivelse = beskrivelse,
    tilordnetRessurs = tilordnetRessurs,
)
