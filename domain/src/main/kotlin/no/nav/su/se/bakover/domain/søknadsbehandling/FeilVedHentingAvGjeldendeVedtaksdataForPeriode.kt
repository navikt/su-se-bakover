package no.nav.su.se.bakover.domain.søknadsbehandling

sealed interface FeilVedHentingAvGjeldendeVedtaksdataForPeriode {
    data object GjeldendeVedtaksdataFinnesIkke : FeilVedHentingAvGjeldendeVedtaksdataForPeriode
}
