package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling

/**
 * Grupperer avslag og innvilgelser.
 */
sealed interface VedtakIverksattSøknadsbehandling : Stønadsvedtak {
    override val behandling: IverksattSøknadsbehandling
}
