package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.EksternInstitusjonsoppholdHendelse
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelse
import no.nav.su.se.bakover.domain.InstitusjonsoppholdKilde
import no.nav.su.se.bakover.domain.InstitusjonsoppholdType
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import java.util.UUID

fun nyEksternInstitusjonsoppholdHendelse(
    hendelseId: Long = 1,
    oppholdId: Long = 2,
    norskIdent: Fnr = fnr,
    type: InstitusjonsoppholdType = InstitusjonsoppholdType.INNMELDING,
    kilde: InstitusjonsoppholdKilde = InstitusjonsoppholdKilde.INST,
): EksternInstitusjonsoppholdHendelse = EksternInstitusjonsoppholdHendelse(
    hendelseId = hendelseId,
    oppholdId = oppholdId,
    norskident = norskIdent,
    type = type,
    kilde = kilde,
)

fun nyInstitusjonsoppholdHendelseUtenOppgaveId(
    id: HendelseId = HendelseId.generer(),
    hendelseSakId: UUID = sakId,
    hendelsesTidspunkt: Tidspunkt = fixedTidspunkt,
    eksternHendelse: EksternInstitusjonsoppholdHendelse = nyEksternInstitusjonsoppholdHendelse(),
    versjon: Hendelsesversjon = Hendelsesversjon.ny(),
): InstitusjonsoppholdHendelse.UtenOppgaveId = InstitusjonsoppholdHendelse.UtenOppgaveId(
    sakId = hendelseSakId,
    hendelseId = id,
    hendelsestidspunkt = hendelsesTidspunkt,
    eksterneHendelse = eksternHendelse,
    versjon = versjon,
)

fun nyInstitusjonsoppholdHendelseMedOppgaveId(
    tidligereHendelse: InstitusjonsoppholdHendelse.UtenOppgaveId = nyInstitusjonsoppholdHendelseUtenOppgaveId(),
    id: HendelseId = HendelseId.generer(),
    sakId: UUID = tidligereHendelse.sakId,
    hendelsesTidspunkt: Tidspunkt = enUkeEtterFixedTidspunkt,
    eksternHendelse: EksternInstitusjonsoppholdHendelse = tidligereHendelse.eksterneHendelse,
    versjon: Hendelsesversjon = tidligereHendelse.versjon.inc(),
    oppgaveId: OppgaveId = OppgaveId("oppgaveId"),
): InstitusjonsoppholdHendelse.MedOppgaveId = InstitusjonsoppholdHendelse.MedOppgaveId(
    sakId = sakId,
    hendelseId = id,
    hendelsestidspunkt = hendelsesTidspunkt,
    eksterneHendelse = eksternHendelse,
    versjon = versjon,
    oppgaveId = oppgaveId,
    tidligereHendelseId = tidligereHendelse.hendelseId,
)
