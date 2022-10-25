package no.nav.su.se.bakover.domain.skatt

import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.Tidspunkt
import java.time.LocalDate

data class Skattegrunnlag(
    val fnr: Fnr,
    val inntektsår: Int,
    val grunnlag: List<Grunnlag>,
    val skatteoppgjoersdato: LocalDate?,
    val hentetDato: Tidspunkt,
) {
    enum class Kategori(val stringVerdi: String) {
        INNTEKT("inntekt"),
        FORMUE("formue"),
        INNTEKTSFRADRAG("inntektsfradrag"),
        FORMUESFRADRAG("formuesfradrag"),
        VERDSETTINGSRABATT_SOM_GIR_GJELDSREDUKSJON("verdsettingsrabattSomGirGjeldsreduksjon"),
        OPPJUSTERING_AV_EIERINNTEKTER("oppjusteringAvEierinntekter"),
    }

    data class Grunnlag(
        val navn: String,
        val beløp: Int,
        val kategori: List<Kategori>,
    )
}
