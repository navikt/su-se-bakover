package no.nav.su.se.bakover.web.routes.skatt

import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
import java.time.LocalDate

internal data class SkattegrunnlagForÅrJson(
    val oppgjørsdato: LocalDate?,
    val formue: List<GrunnlagJson>,
    val inntekt: List<GrunnlagJson>,
    val inntektsfradrag: List<GrunnlagJson>,
    val formuesfradrag: List<GrunnlagJson>,
    val verdsettingsrabattSomGirGjeldsreduksjon: List<GrunnlagJson>,
    val oppjusteringAvEierinntekter: List<GrunnlagJson>,
    val annet: List<GrunnlagJson>,
) {
    companion object {
        fun Skattegrunnlag.SkattegrunnlagForÅr.toJson() = SkattegrunnlagForÅrJson(
            oppgjørsdato = oppgjørsdato,
            formue = this.formue.toGrunnlagJson(),
            inntekt = this.inntekt.toGrunnlagJson(),
            inntektsfradrag = this.inntektsfradrag.toGrunnlagJson(),
            formuesfradrag = this.formuesfradrag.toGrunnlagJson(),
            verdsettingsrabattSomGirGjeldsreduksjon = this.verdsettingsrabattSomGirGjeldsreduksjon.toGrunnlagJson(),
            oppjusteringAvEierinntekter = this.oppjusteringAvEierinntekter.toGrunnlagJson(),
            annet = this.annet.toGrunnlagJson(),
        )
    }
}

data class GrunnlagJson(
    val navn: String,
    val beløp: String,
    val spesifisering: List<KjøretøySpesifiseringJson>,
)

// jah: Dersom det kommer flere typer bør denne gjøres om til en sealed
data class KjøretøySpesifiseringJson(
    val beløp: String? = null,
    val registreringsnummer: String? = null,
    val fabrikatnavn: String? = null,
    val årForFørstegangsregistrering: String? = null,
    val formuesverdi: String? = null,
    val antattVerdiSomNytt: String? = null,
    val antattMarkedsverdi: String? = null,
)

private fun List<Skattegrunnlag.Grunnlag>.toGrunnlagJson(): List<GrunnlagJson> {
    return this.map {
        GrunnlagJson(
            navn = it.navn,
            beløp = it.beløp,
            spesifisering = it.spesifisering.map {
                when (it) {
                    is Skattegrunnlag.Spesifisering.Kjøretøy -> KjøretøySpesifiseringJson(
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
