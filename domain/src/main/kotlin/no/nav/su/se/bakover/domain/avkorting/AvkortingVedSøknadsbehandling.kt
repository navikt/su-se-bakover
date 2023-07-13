package no.nav.su.se.bakover.domain.avkorting

import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling.IkkeVurdert
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling.Vurdert
import java.util.UUID

/**
 * Representerer tilstander for håndtering av et [Avkortingsvarsel] i et søknadsbehandlingsløp.
 *
 * Vil være i tilstanden [IkkeVurdert] frem til vi har beregnet, deretter vil den være i tilstanden [Vurdert].
 * Merk at siden vi støtter paralelle behandlinger, kan
 */
sealed interface AvkortingVedSøknadsbehandling {

    /**
     * Vi tar ikke stilling til om Søknadsbehandlingen skal avkortes før beregningsteget.
     * Dette kan ikke være siste tilstand.
     */
    data object IkkeVurdert : AvkortingVedSøknadsbehandling {
        override fun toString() = this::class.simpleName!!
    }

    sealed interface Vurdert : AvkortingVedSøknadsbehandling
    sealed interface Ferdig : Vurdert

    /**
     * Søknadsbehandlingen avkorter varselet i sin helhet.
     * Se beregningens fradrag for detaljer.
     * Dette er en underveistilstand.
     */
    data class SkalAvkortes(
        val avkortingsvarsel: Avkortingsvarsel.Utenlandsopphold.SkalAvkortes,
    ) : Vurdert, KlarTilIverksetting {
        fun avkort(søknadsbehandlingId: UUID): Avkortet {
            return Avkortet(avkortingsvarsel.avkortet(søknadsbehandlingId))
        }
    }

    /**
     * Ingen avkortingsvarsler som trengs håndteres.
     * Det kan enten bety at det ikke finnes et avkortingsvarsel, eller at det er et avslag.
     */
    data object IngenAvkorting : Ferdig, KlarTilIverksetting {
        override fun toString() = this::class.simpleName!!
    }

    /**
     * Gjelder kun ved iverksatt innvilget.
     */
    data class Avkortet(
        val avkortingsvarsel: Avkortingsvarsel.Utenlandsopphold.Avkortet,
    ) : Ferdig

    /**
     * Så vi slipper exceptions ved iverksettelse.
     * En tilstand som vil gjelde fra vi er beregnet (innvilgelsessporet).
     * */
    sealed interface KlarTilIverksetting : Vurdert
}
