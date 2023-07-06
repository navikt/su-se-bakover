package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.EksternInstitusjonsoppholdHendelse
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelse
import no.nav.su.se.bakover.domain.InstitusjonsoppholdKilde
import no.nav.su.se.bakover.domain.InstitusjonsoppholdType
import java.util.UUID

fun nyInstitusjonsoppholdHendelseIkkeTilknyttetTilSak(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    hendelsesId: Long = 1,
    oppholdId: Long = 1,
    norskIdent: Fnr = fnr,
    type: InstitusjonsoppholdType = InstitusjonsoppholdType.OPPDATERING,
    kilde: InstitusjonsoppholdKilde = InstitusjonsoppholdKilde.INST,
    eksternHendelse: EksternInstitusjonsoppholdHendelse = EksternInstitusjonsoppholdHendelse(
        hendelseId = hendelsesId,
        oppholdId = oppholdId,
        norskident = norskIdent,
        type = type,
        kilde = kilde,
    ),
): InstitusjonsoppholdHendelse.IkkeKnyttetTilSak = InstitusjonsoppholdHendelse.IkkeKnyttetTilSak(
    id = id,
    opprettet = opprettet,
    eksternHendelse = eksternHendelse,
)

fun nyInstitusjonsoppholdHendelseKnyttetTilSakUtenOppgaveId(
    id: UUID = UUID.randomUUID(),
    sakId: UUID = UUID.randomUUID(),
    ikkeKnyttetTilSak: InstitusjonsoppholdHendelse.IkkeKnyttetTilSak = nyInstitusjonsoppholdHendelseIkkeTilknyttetTilSak(
        id = id,
    ),
): InstitusjonsoppholdHendelse.KnyttetTilSak.UtenOppgaveId = InstitusjonsoppholdHendelse.KnyttetTilSak.UtenOppgaveId(
    sakId = sakId,
    ikkeKnyttetTilSak = ikkeKnyttetTilSak,
)

fun nyInstitusjonsoppholdHendelseKnyttetTilSakMedOppgaveId(
    id: UUID = UUID.randomUUID(),
    sakId: UUID = UUID.randomUUID(),
    utenOppgaveId: InstitusjonsoppholdHendelse.KnyttetTilSak.UtenOppgaveId = nyInstitusjonsoppholdHendelseKnyttetTilSakUtenOppgaveId(
        id = id,
        sakId = sakId,
    ),
    oppgaveId: OppgaveId = OppgaveId("oppgaveId"),
): InstitusjonsoppholdHendelse.KnyttetTilSak.MedOppgaveId = InstitusjonsoppholdHendelse.KnyttetTilSak.MedOppgaveId(
    utenOppgaveId = utenOppgaveId,
    oppgaveId = oppgaveId,
)
