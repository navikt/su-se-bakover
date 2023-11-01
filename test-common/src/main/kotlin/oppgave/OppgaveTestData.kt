package no.nav.su.se.bakover.test.oppgave

import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.oppgave.domain.OppgaveHttpKallResponse
import no.nav.su.se.bakover.oppgave.domain.Oppgavetype

val oppgaveId = OppgaveId("123")

fun nyOppgaveHttpKallResponse(
    oppgaveId: OppgaveId = no.nav.su.se.bakover.test.oppgave.oppgaveId,
    oppgavetype: Oppgavetype = Oppgavetype.BEHANDLE_SAK,
    requestBody: String = "requestbody",
    response: String = "response",
    beskrivelse: String = "beskrivelse",
): OppgaveHttpKallResponse = OppgaveHttpKallResponse(
    oppgaveId = oppgaveId,
    oppgavetype = oppgavetype,
    requestBody = requestBody,
    response = response,
    beskrivelse = beskrivelse,
)
