package no.nav.su.se.bakover.test.hendelse

import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.hendelse.application.HendelseMetadata
import no.nav.su.se.bakover.hendelse.application.SakOpprettetHendelse
import no.nav.su.se.bakover.test.fixedTidspunkt
import java.util.UUID

fun sakOpprettetHendelse(
    id: UUID = UUID.randomUUID(),
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    opprettetAv: NavIdentBruker = no.nav.su.se.bakover.test.saksbehandler,
    hendelsestidspunkt: Tidspunkt = fixedTidspunkt,
    meta: HendelseMetadata = HendelseMetadata(
        correlationId = UUID.randomUUID().toString(),
        ident = no.nav.su.se.bakover.test.saksbehandler.toString(),
    ),
) = SakOpprettetHendelse(
    id = id,
    sakId = sakId,
    fnr = fnr,
    opprettetAv = opprettetAv,
    hendelsestidspunkt = hendelsestidspunkt,
    meta = meta,
)
