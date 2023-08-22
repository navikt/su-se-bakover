package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelse
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse

fun nyOppgaveHendelse(
    triggetAv: InstitusjonsoppholdHendelse = nyInstitusjonsoppholdHendelse(),
    hendelseId: HendelseId = HendelseId.generer(),
    hendelsesTidspunkt: Tidspunkt = fixedTidspunkt,
    oppgaveId: OppgaveId = OppgaveId("oppgaveId"),
): OppgaveHendelse {
    return OppgaveHendelse(
        hendelseId = hendelseId,
        tidligereHendelseId = null,
        sakId = triggetAv.sakId,
        versjon = triggetAv.versjon.inc(),
        hendelsestidspunkt = hendelsesTidspunkt,
        triggetAv = triggetAv.hendelseId,
        oppgaveId = oppgaveId,
    )
}
