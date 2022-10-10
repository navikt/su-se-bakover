package no.nav.su.se.bakover.hendelse.domain

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

    companion object {
        /** Reservert for persisteringslaget. */
        fun create(
            id: UUID,
            sakId: UUID,
            fnr: Fnr,
            opprettetAv: NavIdentBruker,
            hendelsestidspunkt: Tidspunkt,
            meta: HendelseMetadata,
            entitetId: UUID,
            versjon: Long,
        ): SakOpprettetHendelse {
            return SakOpprettetHendelse(
                id = id,
                sakId = sakId,
                fnr = fnr,
                opprettetAv = opprettetAv,
                hendelsestidspunkt = hendelsestidspunkt,
                meta = meta,
            ).also {
                require(entitetId == it.entitetId) {
                    "EntitetId til SakOpprettetHendelse må være sakId $sakId, men var $entitetId"
                }
                require(Hendelse.Versjon(versjon) == it.versjon) {
                    "Versjonen til en SakOpprettetHendelse må være 1, men var $versjon"
                }
            }
        }
    }
}
