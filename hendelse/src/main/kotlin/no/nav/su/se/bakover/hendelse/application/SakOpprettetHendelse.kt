package no.nav.su.se.bakover.hendelse.application

import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import java.util.UUID

data class SakOpprettetHendelse(
    override val id: UUID,
    override val sakId: UUID,
    val fnr: Fnr,
    val opprettetAv: NavIdentBruker,
    override val hendelsestidspunkt: Tidspunkt,
    override val meta: HendelseMetadata,
) : Hendelse {
    override val entitetId = sakId

    // Dette vil alltid være første versjon i en hendelsesserie for en sak.
    override val versjon = Hendelse.Versjon(1L)
}
