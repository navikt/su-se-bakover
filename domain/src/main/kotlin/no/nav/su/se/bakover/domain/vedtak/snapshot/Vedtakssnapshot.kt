package no.nav.su.se.bakover.domain.vedtak.snapshot

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import java.util.UUID

sealed class Vedtakssnapshot {
    abstract val id: UUID
    abstract val opprettet: Tidspunkt
    abstract val søknadsbehandling: Søknadsbehandling.Iverksatt

    data class Avslag(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val søknadsbehandling: Søknadsbehandling.Iverksatt.Avslag,
        val avslagsgrunner: List<Avslagsgrunn>,
    ) : Vedtakssnapshot() {

        companion object {
            fun createFromBehandling(
                søknadsbehandling: Søknadsbehandling.Iverksatt.Avslag,
                avslagsgrunner: List<Avslagsgrunn>,
            ): Avslag = Avslag(
                UUID.randomUUID(),
                Tidspunkt.now(),
                søknadsbehandling = søknadsbehandling,
                avslagsgrunner = avslagsgrunner,
            )
        }
    }

    data class Innvilgelse(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val søknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget,
        val utbetaling: Utbetaling,
    ) : Vedtakssnapshot() {

        companion object {
            fun createFromBehandling(
                søknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget,
                utbetaling: Utbetaling,
            ): Innvilgelse = Innvilgelse(
                UUID.randomUUID(),
                Tidspunkt.now(),
                søknadsbehandling = søknadsbehandling,
                utbetaling = utbetaling,
            )
        }
    }
}
