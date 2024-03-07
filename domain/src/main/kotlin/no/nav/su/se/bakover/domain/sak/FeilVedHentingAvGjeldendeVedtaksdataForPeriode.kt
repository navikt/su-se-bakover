package no.nav.su.se.bakover.domain.sak

sealed interface FeilVedHentingAvGjeldendeVedtaksdataForPeriode {
    data object GjeldendeVedtaksdataFinnesIkke : FeilVedHentingAvGjeldendeVedtaksdataForPeriode
}
