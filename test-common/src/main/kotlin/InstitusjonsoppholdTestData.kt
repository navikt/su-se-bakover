package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.EksternInstitusjonsoppholdHendelse
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelse
import no.nav.su.se.bakover.domain.InstitusjonsoppholdKilde
import no.nav.su.se.bakover.domain.InstitusjonsoppholdType
import no.nav.su.se.bakover.domain.OppholdId
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import java.util.UUID

fun nyEksternInstitusjonsoppholdHendelse(
    hendelseId: Long = 1,
    oppholdId: OppholdId = OppholdId(2),
    norskIdent: Fnr = fnr,
    type: InstitusjonsoppholdType = InstitusjonsoppholdType.INNMELDING,
    kilde: InstitusjonsoppholdKilde = InstitusjonsoppholdKilde.Institusjon,
): EksternInstitusjonsoppholdHendelse = EksternInstitusjonsoppholdHendelse(
    hendelseId = hendelseId,
    oppholdId = oppholdId,
    norskident = norskIdent,
    type = type,
    kilde = kilde,
)

fun nyInstitusjonsoppholdHendelse(
    id: HendelseId = HendelseId.generer(),
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    hendelsesTidspunkt: Tidspunkt = fixedTidspunkt,
    eksternHendelse: EksternInstitusjonsoppholdHendelse = nyEksternInstitusjonsoppholdHendelse(),
    versjon: Hendelsesversjon = Hendelsesversjon.ny(),
    tidligereHendelse: HendelseId? = null,
): InstitusjonsoppholdHendelse = InstitusjonsoppholdHendelse(
    hendelseId = id,
    sakId = sakId,
    hendelsestidspunkt = hendelsesTidspunkt,
    tidligereHendelseId = tidligereHendelse,
    eksterneHendelse = eksternHendelse,
    versjon = versjon,
)
