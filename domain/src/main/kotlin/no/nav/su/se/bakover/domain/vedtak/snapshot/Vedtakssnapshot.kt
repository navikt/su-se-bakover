package no.nav.su.se.bakover.domain.vedtak.snapshot

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import java.util.UUID

sealed class Vedtakssnapshot {
    abstract val id: UUID
    abstract val opprettet: Tidspunkt
    abstract val behandling: Behandling

    data class Avslag(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val behandling: Behandling,
        val avslagsgrunner: List<Avslagsgrunn>
    ) : Vedtakssnapshot() {

        companion object {
            fun createFromBehandling(behandling: Behandling, avslagsgrunner: List<Avslagsgrunn>): Avslag = Avslag(
                UUID.randomUUID(),
                Tidspunkt.now(),
                behandling = behandling,
                avslagsgrunner = avslagsgrunner
            )
        }
    }

    data class Innvilgelse(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val behandling: Behandling,
        val utbetaling: Utbetaling
    ) : Vedtakssnapshot() {

        companion object {
            fun createFromBehandling(behandling: Behandling, utbetaling: Utbetaling): Innvilgelse = Innvilgelse(
                UUID.randomUUID(),
                Tidspunkt.now(),
                behandling = behandling,
                utbetaling = utbetaling
            )
        }
    }
}
