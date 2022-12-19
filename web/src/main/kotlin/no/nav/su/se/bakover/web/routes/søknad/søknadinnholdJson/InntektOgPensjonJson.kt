package no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson

import no.nav.su.se.bakover.domain.søknad.søknadinnhold.InntektOgPensjon
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.PensjonsOrdningBeløp
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.TrygdeytelseIUtlandet
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.PensjonsOrdningBeløpJson.Companion.toPensjonsOrdningBeløpJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.TrygdeytelserIUtlandetJson.Companion.toTrygdeytelseIUtlandetJson

data class InntektOgPensjonJson(
    val forventetInntekt: Number? = null,
    val andreYtelserINav: String? = null,
    val andreYtelserINavBeløp: Number? = null,
    val søktAndreYtelserIkkeBehandletBegrunnelse: String? = null,
    val trygdeytelserIUtlandet: List<TrygdeytelserIUtlandetJson>? = null,
    val pensjon: List<PensjonsOrdningBeløpJson>? = null,
) {
    fun toInntektOgPensjon() = InntektOgPensjon(
        forventetInntekt = forventetInntekt,
        andreYtelserINav = andreYtelserINav,
        andreYtelserINavBeløp = andreYtelserINavBeløp,
        søktAndreYtelserIkkeBehandletBegrunnelse = søktAndreYtelserIkkeBehandletBegrunnelse,
        trygdeytelseIUtlandet = trygdeytelserIUtlandet.toTrygdeytelseList(),
        pensjon = pensjon.toPensjonList(),
    )

    private fun List<PensjonsOrdningBeløpJson>?.toPensjonList() = this?.map {
        it.toPensjonsOrdningBeløp()
    }

    private fun List<TrygdeytelserIUtlandetJson>?.toTrygdeytelseList() = this?.map {
        it.toTrygdeytelseIUtlandet()
    }

    companion object {
        fun InntektOgPensjon.toInntektOgPensjonJson() =
            InntektOgPensjonJson(
                forventetInntekt = forventetInntekt,
                andreYtelserINav = andreYtelserINav,
                andreYtelserINavBeløp = andreYtelserINavBeløp,
                søktAndreYtelserIkkeBehandletBegrunnelse = søktAndreYtelserIkkeBehandletBegrunnelse,
                trygdeytelserIUtlandet = trygdeytelseIUtlandet.toTrygdeytelseIUtlandetJson(),
                pensjon = pensjon.toPensjonsOrdningBeløpListJson(),
            )

        private fun List<PensjonsOrdningBeløp>?.toPensjonsOrdningBeløpListJson() = this?.map {
            it.toPensjonsOrdningBeløpJson()
        }

        private fun List<TrygdeytelseIUtlandet>?.toTrygdeytelseIUtlandetJson() = this?.map {
            it.toTrygdeytelseIUtlandetJson()
        }
    }
}

data class PensjonsOrdningBeløpJson(
    val ordning: String,
    val beløp: Double,
) {
    fun toPensjonsOrdningBeløp() = PensjonsOrdningBeløp(
        ordning = ordning,
        beløp = beløp,
    )

    companion object {
        fun PensjonsOrdningBeløp.toPensjonsOrdningBeløpJson() =
            PensjonsOrdningBeløpJson(
                ordning = ordning,
                beløp = beløp,
            )
    }
}

data class TrygdeytelserIUtlandetJson(
    val beløp: Number,
    val type: String,
    val valuta: String,
) {
    fun toTrygdeytelseIUtlandet() = TrygdeytelseIUtlandet(
        beløp = beløp,
        type = type,
        valuta = valuta,
    )

    companion object {
        fun TrygdeytelseIUtlandet.toTrygdeytelseIUtlandetJson() =
            TrygdeytelserIUtlandetJson(
                beløp = beløp,
                type = type,
                valuta = valuta,
            )
    }
}
