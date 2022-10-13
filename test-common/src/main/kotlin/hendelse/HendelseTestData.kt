package no.nav.su.se.bakover.test.hendelse

import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.SakOpprettetHendelse
import no.nav.su.se.bakover.test.correlationId
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.saksbehandler
import java.util.UUID

fun sakOpprettetHendelse(
    hendelseId: UUID = UUID.randomUUID(),
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    opprettetAv: NavIdentBruker = saksbehandler,
    hendelsestidspunkt: Tidspunkt = fixedTidspunkt,
    meta: HendelseMetadata = HendelseMetadata(
        correlationId = correlationId(),
        ident = saksbehandler,
        brukerroller = listOf(Brukerrolle.Saksbehandler, Brukerrolle.Attestant),
    ),
) = SakOpprettetHendelse.fraPersistert(
    hendelseId = hendelseId,
    sakId = sakId,
    fnr = fnr,
    opprettetAv = opprettetAv,
    hendelsestidspunkt = hendelsestidspunkt,
    meta = meta,
    entitetId = sakId,
    versjon = 1,
)
