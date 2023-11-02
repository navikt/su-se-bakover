package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseMetadata
import no.nav.su.se.bakover.oppgave.domain.Oppgavetype
import java.util.UUID

fun nyOppgaveHendelse(
    sakId: UUID = UUID.randomUUID(),
    hendelseId: HendelseId = HendelseId.generer(),
    hendelsesTidspunkt: Tidspunkt = fixedTidspunkt,
    oppgaveId: OppgaveId = OppgaveId("oppgaveId"),
    nesteVersjon: Hendelsesversjon,
    relaterteHendelser: List<HendelseId> = listOf(HendelseId.generer()),
    metadata: OppgaveHendelseMetadata = OppgaveHendelseMetadata(
        correlationId = correlationId(),
        ident = null,
        brukerroller = listOf(),
        request = "requestBody - OppgaveHendelseTestData.kt",
        response = "response - OppgaveHendelseTestData.kt",
    ),
    oppgavetype: Oppgavetype = Oppgavetype.BEHANDLE_SAK,
    beskrivelse: String = "OppgaveHendelseTestData.kt",
): OppgaveHendelse {
    return OppgaveHendelse.Opprettet(
        hendelseId = hendelseId,
        sakId = sakId,
        versjon = nesteVersjon,
        hendelsestidspunkt = hendelsesTidspunkt,
        oppgaveId = oppgaveId,
        meta = metadata,
        relaterteHendelser = relaterteHendelser,
        oppgavetype = oppgavetype,
        beskrivelse = beskrivelse,
    )
}
