package no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.Ektefelle
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.FeilVedOpprettelseAvFormue
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.FormueJson.Companion.toFormueJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.InntektOgPensjonJson.Companion.toInntektOgPensjonJson

data class EktefelleJson(val formue: FormueJson, val inntektOgPensjon: InntektOgPensjonJson) {
    fun toEktefelle(): Either<FeilVedOpprettelseAvEktefelleJson, Ektefelle> = Ektefelle(
        formue = formue.toFormue().getOrHandle {
            return FeilVedOpprettelseAvEktefelleJson.FeilVedOpprettelseAvFormueEktefelle(it).left()
        },
        inntektOgPensjon = inntektOgPensjon.toInntektOgPensjon(),
    ).right()

    companion object {
        fun Ektefelle.toJson() = EktefelleJson(
            formue = formue.toFormueJson(),
            inntektOgPensjon = inntektOgPensjon.toInntektOgPensjonJson(),
        )
    }
}

sealed interface FeilVedOpprettelseAvEktefelleJson {
    data class FeilVedOpprettelseAvFormueEktefelle(val underliggendeFeil: FeilVedOpprettelseAvFormue) :
        FeilVedOpprettelseAvEktefelleJson
}
