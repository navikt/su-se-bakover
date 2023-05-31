package no.nav.su.se.bakover.client.pdf

import arrow.core.NonEmptyList
import arrow.core.getOrElse
import no.nav.su.se.bakover.client.pdf.SkattegrunnlagPdfJson.Annet.Companion.tilPdfJson
import no.nav.su.se.bakover.client.pdf.SkattegrunnlagPdfJson.Formue.Companion.tilPdfJson
import no.nav.su.se.bakover.client.pdf.SkattegrunnlagPdfJson.Formuesfradrag.Companion.tilPdfJson
import no.nav.su.se.bakover.client.pdf.SkattegrunnlagPdfJson.Inntekt.Companion.tilPdfJson
import no.nav.su.se.bakover.client.pdf.SkattegrunnlagPdfJson.Inntektsfradrag.Companion.tilPdfJson
import no.nav.su.se.bakover.client.pdf.SkattegrunnlagPdfJson.ManglerKategori.Companion.tilPdfJson
import no.nav.su.se.bakover.client.pdf.SkattegrunnlagPdfJson.OppjusteringAvEierinntekter.Companion.tilPdfJson
import no.nav.su.se.bakover.client.pdf.SkattegrunnlagPdfJson.VerdsettingsrabattSomGirGjeldsreduksjon.Companion.tilPdfJson
import no.nav.su.se.bakover.client.pdf.SpesifiseringPdfJson.KjøretøyJson.Companion.tilPdfJson
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.skatt.SamletSkattegrunnlagForÅrOgStadie
import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
import java.time.LocalDate
import java.util.UUID

data class SkattPdfData(
    val fnr: Fnr,
    val navn: Person.Navn,
    val årsgrunnlag: NonEmptyList<ÅrsgrunnlagPdfJson>,
) {
    data class ÅrsgrunnlagPdfJson(
        val år: Int,
        val stadie: ÅrsgrunnlagStadie,
        val oppgjørsdato: LocalDate?,
        val formue: List<SkattegrunnlagPdfJson.Formue> = emptyList(),
        val inntekt: List<SkattegrunnlagPdfJson.Inntekt> = emptyList(),
        val inntektsfradrag: List<SkattegrunnlagPdfJson.Inntektsfradrag> = emptyList(),
        val formuesfradrag: List<SkattegrunnlagPdfJson.Formuesfradrag> = emptyList(),
        val verdsettingsrabattSomGirGjeldsreduksjon: List<SkattegrunnlagPdfJson.VerdsettingsrabattSomGirGjeldsreduksjon> = emptyList(),
        val oppjusteringAvEierinntekter: List<SkattegrunnlagPdfJson.OppjusteringAvEierinntekter> = emptyList(),
        val manglerKategori: List<SkattegrunnlagPdfJson.ManglerKategori> = emptyList(),
        val annet: List<SkattegrunnlagPdfJson.Annet> = emptyList(),
    ) {
        enum class ÅrsgrunnlagStadie {
            Oppgjør,
            Utkast,
        }

        companion object {
            fun NonEmptyList<SamletSkattegrunnlagForÅrOgStadie>.tilPdfJson(vedtakContext: UUID): NonEmptyList<ÅrsgrunnlagPdfJson> =
                this.map { it.tilPdfJson(vedtakContext) }

            fun SamletSkattegrunnlagForÅrOgStadie.tilPdfJson(vedtakContext: UUID): ÅrsgrunnlagPdfJson {
                val skattegrunnlagForÅr = this.oppslag.getOrElse {
                    throw IllegalStateException("Forventet at vi skulle ha skattegrunnlag på dette tidspunktet, men var $it for vedtak $vedtakContext")
                }

                return ÅrsgrunnlagPdfJson(
                    år = this.inntektsår.value,
                    stadie = when (this) {
                        is SamletSkattegrunnlagForÅrOgStadie.Oppgjør -> ÅrsgrunnlagStadie.Oppgjør
                        is SamletSkattegrunnlagForÅrOgStadie.Utkast -> ÅrsgrunnlagStadie.Utkast
                    },
                    oppgjørsdato = skattegrunnlagForÅr.oppgjørsdato,
                    formue = skattegrunnlagForÅr.formue.tilPdfJson(),
                    inntekt = skattegrunnlagForÅr.inntekt.tilPdfJson(),
                    inntektsfradrag = skattegrunnlagForÅr.inntektsfradrag.tilPdfJson(),
                    formuesfradrag = skattegrunnlagForÅr.formuesfradrag.tilPdfJson(),
                    verdsettingsrabattSomGirGjeldsreduksjon = skattegrunnlagForÅr.verdsettingsrabattSomGirGjeldsreduksjon.tilPdfJson(),
                    oppjusteringAvEierinntekter = skattegrunnlagForÅr.oppjusteringAvEierinntekter.tilPdfJson(),
                    manglerKategori = skattegrunnlagForÅr.manglerKategori.tilPdfJson(),
                    annet = skattegrunnlagForÅr.annet.tilPdfJson(),
                )
            }
        }
    }
}

sealed interface SkattegrunnlagPdfJson {
    val tekniskNavn: String
    val beløp: String
    val spesifisering: List<SpesifiseringPdfJson>

    data class Formue(
        override val tekniskNavn: String,
        override val beløp: String,
        override val spesifisering: List<SpesifiseringPdfJson.KjøretøyJson> = emptyList(),
    ) : SkattegrunnlagPdfJson {
        companion object {
            fun Skattegrunnlag.Grunnlag.Formue.tilPdfJson(): Formue = Formue(navn, beløp, spesifisering.tilPdfJson())
            fun List<Skattegrunnlag.Grunnlag.Formue>.tilPdfJson(): List<Formue> = this.map { it.tilPdfJson() }
        }
    }

    data class Inntekt(
        override val tekniskNavn: String,
        override val beløp: String,
    ) : SkattegrunnlagPdfJson {
        override val spesifisering: List<SpesifiseringPdfJson> = emptyList()

        companion object {
            fun Skattegrunnlag.Grunnlag.Inntekt.tilPdfJson(): Inntekt = Inntekt(navn, beløp)
            fun List<Skattegrunnlag.Grunnlag.Inntekt>.tilPdfJson(): List<Inntekt> = this.map { it.tilPdfJson() }
        }
    }

    data class Inntektsfradrag(
        override val tekniskNavn: String,
        override val beløp: String,
    ) : SkattegrunnlagPdfJson {
        override val spesifisering: List<SpesifiseringPdfJson> = emptyList()

        companion object {
            fun Skattegrunnlag.Grunnlag.Inntektsfradrag.tilPdfJson(): Inntektsfradrag = Inntektsfradrag(navn, beløp)
            fun List<Skattegrunnlag.Grunnlag.Inntektsfradrag>.tilPdfJson(): List<Inntektsfradrag> =
                this.map { it.tilPdfJson() }
        }
    }

    data class Formuesfradrag(
        override val tekniskNavn: String,
        override val beløp: String,
    ) : SkattegrunnlagPdfJson {
        override val spesifisering: List<SpesifiseringPdfJson> = emptyList()

        companion object {
            fun Skattegrunnlag.Grunnlag.Formuesfradrag.tilPdfJson(): Formuesfradrag = Formuesfradrag(navn, beløp)
            fun List<Skattegrunnlag.Grunnlag.Formuesfradrag>.tilPdfJson(): List<Formuesfradrag> =
                this.map { it.tilPdfJson() }
        }
    }

    data class VerdsettingsrabattSomGirGjeldsreduksjon(
        override val tekniskNavn: String,
        override val beløp: String,
    ) : SkattegrunnlagPdfJson {
        override val spesifisering: List<SpesifiseringPdfJson> = emptyList()

        companion object {
            fun Skattegrunnlag.Grunnlag.VerdsettingsrabattSomGirGjeldsreduksjon.tilPdfJson(): VerdsettingsrabattSomGirGjeldsreduksjon =
                VerdsettingsrabattSomGirGjeldsreduksjon(navn, beløp)

            fun List<Skattegrunnlag.Grunnlag.VerdsettingsrabattSomGirGjeldsreduksjon>.tilPdfJson(): List<VerdsettingsrabattSomGirGjeldsreduksjon> =
                this.map { it.tilPdfJson() }
        }
    }

    data class OppjusteringAvEierinntekter(
        override val tekniskNavn: String,
        override val beløp: String,
    ) : SkattegrunnlagPdfJson {
        override val spesifisering: List<SpesifiseringPdfJson> = emptyList()

        companion object {
            fun Skattegrunnlag.Grunnlag.OppjusteringAvEierinntekter.tilPdfJson(): OppjusteringAvEierinntekter =
                OppjusteringAvEierinntekter(navn, beløp)

            fun List<Skattegrunnlag.Grunnlag.OppjusteringAvEierinntekter>.tilPdfJson(): List<OppjusteringAvEierinntekter> =
                this.map { it.tilPdfJson() }
        }
    }

    data class ManglerKategori(
        override val tekniskNavn: String,
        override val beløp: String,
    ) : SkattegrunnlagPdfJson {
        override val spesifisering: List<SpesifiseringPdfJson> = emptyList()

        companion object {
            fun Skattegrunnlag.Grunnlag.ManglerKategori.tilPdfJson(): ManglerKategori = ManglerKategori(navn, beløp)
            fun List<Skattegrunnlag.Grunnlag.ManglerKategori>.tilPdfJson(): List<ManglerKategori> =
                this.map { it.tilPdfJson() }
        }
    }

    data class Annet(
        override val tekniskNavn: String,
        override val beløp: String,
    ) : SkattegrunnlagPdfJson {
        override val spesifisering: List<SpesifiseringPdfJson> = emptyList()

        companion object {
            fun Skattegrunnlag.Grunnlag.Annet.tilPdfJson(): Annet = Annet(navn, beløp)
            fun List<Skattegrunnlag.Grunnlag.Annet>.tilPdfJson(): List<Annet> = this.map { it.tilPdfJson() }
        }
    }
}

sealed interface SpesifiseringPdfJson {
    data class KjøretøyJson(
        val beløp: String? = null,
        val registreringsnummer: String? = null,
        val fabrikatnavn: String? = null,
        val årForFørstegangsregistrering: String? = null,
        val formuesverdi: String? = null,
        val antattVerdiSomNytt: String? = null,
        val antattMarkedsverdi: String? = null,
    ) : SpesifiseringPdfJson {
        companion object {
            fun Skattegrunnlag.Spesifisering.Kjøretøy.tilPdfJson(): KjøretøyJson = KjøretøyJson(
                beløp = this.beløp,
                registreringsnummer = this.registreringsnummer,
                fabrikatnavn = this.fabrikatnavn,
                årForFørstegangsregistrering = this.årForFørstegangsregistrering,
                formuesverdi = this.formuesverdi,
                antattVerdiSomNytt = this.antattVerdiSomNytt,
                antattMarkedsverdi = this.antattMarkedsverdi,
            )

            fun List<Skattegrunnlag.Spesifisering.Kjøretøy>.tilPdfJson(): List<KjøretøyJson> =
                this.map { it.tilPdfJson() }
        }
    }
}
