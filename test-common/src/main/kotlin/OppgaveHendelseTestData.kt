package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse
import java.util.UUID

fun nyOppgaveHendelseFraInstitusjonsoppholdsHendelser(
    sakId: UUID = UUID.randomUUID(),
    hendelseId: HendelseId = HendelseId.generer(),
    hendelsesTidspunkt: Tidspunkt = fixedTidspunkt,
    oppgaveId: OppgaveId = OppgaveId("oppgaveId"),
    nesteVersjon: Hendelsesversjon,
    relaterteHendelser: List<HendelseId> = listOf(HendelseId.generer()),
    metadata: HendelseMetadata = HendelseMetadata.fraCorrelationId(correlationId()),
): OppgaveHendelse {
    return OppgaveHendelse.opprettet(
        hendelseId = hendelseId,
        sakId = sakId,
        versjon = nesteVersjon,
        hendelsestidspunkt = hendelsesTidspunkt,
        oppgaveId = oppgaveId,
        meta = metadata,
        relaterteHendelser = relaterteHendelser,
    )
}
