package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import java.util.UUID

sealed class Vedtak {
    abstract val id: UUID
    abstract val opprettet: Tidspunkt
    abstract val behandling: Behandling
    abstract val versjon: String

    data class Avslag(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val behandling: Behandling,
        val avslagsgrunner: List<Avslagsgrunn>
    ) : Vedtak() {
        // TODO: Hvordan kan vi sikre et bedre grunnbeløpsdato. Hent via første dato i beregning.
        // val halvGrunnbeløp: Double = Grunnbeløp.`0,5G`.fraDato(opprettet.toLocalDate(zoneIdOslo))
        override val versjon = "1"

        companion object {
            fun createFromBehandling(behandling: Behandling, avslagsgrunner: List<Avslagsgrunn>): Avslag = Avslag(
                UUID.randomUUID(),
                Tidspunkt.now(),
                behandling = behandling,
                avslagsgrunner = avslagsgrunner
            )
        }
    }
}
