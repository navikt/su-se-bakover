package no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson

import no.nav.su.se.bakover.domain.Ektefelle
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.FormueJson.Companion.toFormueJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.InntektOgPensjonJson.Companion.toInntektOgPensjonJson

data class EktefelleJson(val formue: FormueJson, val inntektOgPensjon: InntektOgPensjonJson) {
    fun toEktefelle() = Ektefelle(
        formue = formue.toFormue(),
        inntektOgPensjon = inntektOgPensjon.toInntektOgPensjon(),
    )

    companion object {
        fun Ektefelle.toJson() = EktefelleJson(
            formue = formue.toFormueJson(),
            inntektOgPensjon = inntektOgPensjon.toInntektOgPensjonJson(),
        )
    }
}
