package no.nav.su.se.bakover.web.routes.skatt

import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag

internal data class SkattegrunnlagJSON(
    val fnr: String,
    val inntektsår: Int,
    val grunnlag: List<Grunnlag>,
    val skatteoppgjørsdato: String?,
    val hentetDato: String?,
) {
    data class Grunnlag(
        val navn: String,
        val beløp: Int,
        val kategori: List<String>,
    )
}

internal fun Skattegrunnlag.toJSON() = SkattegrunnlagJSON(
    fnr = fnr.toString(),
    inntektsår = inntektsår,
    grunnlag = grunnlag.map {
        SkattegrunnlagJSON.Grunnlag(navn = it.navn, beløp = it.beløp, kategori = it.kategori.map { k -> k.stringVerdi })
    },
    skatteoppgjørsdato = skatteoppgjoersdato?.toString(),
    hentetDato = hentetDato.toString(),
)
