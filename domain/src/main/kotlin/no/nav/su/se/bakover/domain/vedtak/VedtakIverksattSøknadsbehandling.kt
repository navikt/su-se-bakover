package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import vedtak.domain.Stønadsvedtak

/**
 * Grupperer avslag og innvilgelser.
 */
sealed interface VedtakIverksattSøknadsbehandling : Stønadsvedtak {
    override val behandling: IverksattSøknadsbehandling
}
