package no.nav.su.se.bakover.domain.søknad.søknadinnhold

data class InntektOgPensjon(
    val forventetInntekt: Number? = null,
    val andreYtelserINav: String? = null,
    val andreYtelserINavBeløp: Number? = null,
    val søktAndreYtelserIkkeBehandletBegrunnelse: String? = null,
    val trygdeytelseIUtlandet: List<TrygdeytelseIUtlandet>? = null,
    val pensjon: List<PensjonsOrdningBeløp>? = null,
)

data class PensjonsOrdningBeløp(
    val ordning: String,
    val beløp: Double,
)

data class TrygdeytelseIUtlandet(
    val beløp: Number,
    val type: String,
    val valuta: String,
)
