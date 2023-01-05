package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.klage.IverksattAvvistKlage
import no.nav.su.se.bakover.domain.klage.Klage
import java.time.Clock
import java.util.UUID

sealed interface Klagevedtak : Vedtak {
    val klage: Klage

    /**
     * Ved avvisning og oversending sendes det brev
     */
    fun harDokument(): Boolean = true

    data class Avvist(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val attestant: NavIdentBruker.Attestant,
        override val klage: IverksattAvvistKlage,
    ) : Klagevedtak {
        companion object {
            fun fromIverksattAvvistKlage(
                iverksattAvvistKlage: IverksattAvvistKlage,
                clock: Clock,
            ): Avvist {
                return Avvist(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(clock),
                    saksbehandler = iverksattAvvistKlage.saksbehandler,
                    attestant = (iverksattAvvistKlage.attesteringer.hentSisteAttestering() as Attestering.Iverksatt).attestant,
                    klage = iverksattAvvistKlage,
                )
            }
        }
    }
}
