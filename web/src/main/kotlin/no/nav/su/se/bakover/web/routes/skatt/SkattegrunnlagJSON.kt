package no.nav.su.se.bakover.web.routes.skatt

import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
import java.time.LocalDate
import java.time.Year

internal data class SkattegrunnlagJSON(
    val fnr: String,
    val hentetTidspunkt: String,
    val årsgrunnlag: List<Årsgrunnlag>,
) {
    data class Årsgrunnlag(
        val inntektsår: Year,
        val skatteoppgjørsdato: LocalDate?,
        val grunnlag: Grunnlagsliste,
        val stadie: String,
    )

    data class Grunnlagsliste(
        val formue: List<Grunnlag>,
        val inntekt: List<Grunnlag>,
        val inntektsfradrag: List<Grunnlag>,
        val formuesfradrag: List<Grunnlag>,
        val verdsettingsrabattSomGirGjeldsreduksjon: List<Grunnlag>,
        val oppjusteringAvEierinntekter: List<Grunnlag>,
        val annet: List<Grunnlag>,
    )

    data class Grunnlag(
        val navn: String,
        val beløp: String,
        val spesifisering: List<KjøretøySpesifisering>?,
    )

    // jah: Dersom det kommer flere typer bør denne gjøres om til en sealed
    data class KjøretøySpesifisering(
        val beløp: String? = null,
        val registreringsnummer: String? = null,
        val fabrikatnavn: String? = null,
        val årForFørstegangsregistrering: String? = null,
        val formuesverdi: String? = null,
        val antattVerdiSomNytt: String? = null,
        val antattMarkedsverdi: String? = null,
    )
}

internal fun Skattegrunnlag.toJSON() = SkattegrunnlagJSON(
    fnr = fnr.toString(),
    hentetTidspunkt = hentetTidspunkt.toString(),
    årsgrunnlag = årsgrunnlag.map {
        SkattegrunnlagJSON.Årsgrunnlag(
            inntektsår = it.inntektsår,
            skatteoppgjørsdato = it.skatteoppgjørsdato,
            grunnlag = SkattegrunnlagJSON.Grunnlagsliste(
                formue = it.grunnlag.formue.toGrunnlagJson(),
                inntekt = it.grunnlag.inntekt.toGrunnlagJson(),
                inntektsfradrag = it.grunnlag.inntektsfradrag.toGrunnlagJson(),
                formuesfradrag = it.grunnlag.formuesfradrag.toGrunnlagJson(),
                verdsettingsrabattSomGirGjeldsreduksjon = it.grunnlag.verdsettingsrabattSomGirGjeldsreduksjon.toGrunnlagJson(),
                oppjusteringAvEierinntekter = it.grunnlag.oppjusteringAvEierinntekter.toGrunnlagJson(),
                annet = it.grunnlag.annet.toGrunnlagJson(),
            ),
            stadie = it.stadie.verdi,
        )
    },
)

private fun List<Skattegrunnlag.Grunnlag>.toGrunnlagJson(): List<SkattegrunnlagJSON.Grunnlag> {
    return this.map {
        SkattegrunnlagJSON.Grunnlag(
            navn = it.navn,
            beløp = it.beløp,
            spesifisering = it.spesifisering.map {
                when (it) {
                    is Skattegrunnlag.Spesifisering.Kjøretøy -> SkattegrunnlagJSON.KjøretøySpesifisering(
                        beløp = it.beløp,
                        registreringsnummer = it.registreringsnummer,
                        fabrikatnavn = it.fabrikatnavn,
                        årForFørstegangsregistrering = it.årForFørstegangsregistrering,
                        formuesverdi = it.formuesverdi,
                        antattVerdiSomNytt = it.antattVerdiSomNytt,
                        antattMarkedsverdi = it.antattMarkedsverdi,
                    )
                }
            },
        )
    }
}
