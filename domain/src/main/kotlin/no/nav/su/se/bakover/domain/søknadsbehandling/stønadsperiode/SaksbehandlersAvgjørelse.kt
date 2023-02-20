package no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode

import no.nav.su.se.bakover.common.Tidspunkt

/**
 * En avgjørelse som er knyttet til [Aldersvurdering.Vurdert]
 */
sealed interface SaksbehandlersAvgjørelse {
    data class Avgjort(val tidspunkt: Tidspunkt) : SaksbehandlersAvgjørelse
}
