package no.nav.su.se.bakover.domain.sak

import no.nav.su.se.bakover.common.domain.tid.periode.PeriodeMedOptionalTilOgMed
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling

/**
 * Sjekker om denne saken har en stønadsperiode som overlapper med angitt periode.
 * Her tar vi kun høyde for innvilget søknadsbehandlinger.
 * I.e. vi tar ikke høyde for opphør/stans.
 */
fun Sak.harStønadForPeriode(periode: PeriodeMedOptionalTilOgMed): Boolean {
    return this.vedtakListe
        .filterIsInstance<VedtakInnvilgetSøknadsbehandling>()
        .any { periode.overlapper(it.periode) }
}
