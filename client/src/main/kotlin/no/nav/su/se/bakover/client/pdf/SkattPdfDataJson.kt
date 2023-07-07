package no.nav.su.se.bakover.client.pdf

import arrow.core.Either
import arrow.core.NonEmptyList
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.client.pdf.SamletÅrsgrunnlagPdfJson.HarIkkeSkattegrunnlagForÅrOgStadie.HarIkkeSkattegrunnlagFordi.Companion.tilPdfJson
import no.nav.su.se.bakover.client.pdf.SkattegrunnlagPdfJson.Annet.Companion.tilPdfJson
import no.nav.su.se.bakover.client.pdf.SkattegrunnlagPdfJson.Formue.Companion.hentKjøretøyPdfJson
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
import no.nav.su.se.bakover.domain.skatt.KunneIkkeHenteSkattemelding
import no.nav.su.se.bakover.domain.skatt.SamletSkattegrunnlagForÅrOgStadie
import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
import java.time.LocalDate

data class SkattPdfDataJson(
    val fnr: Fnr,
    val navn: Person.Navn,
    val årsgrunnlag: NonEmptyList<SamletÅrsgrunnlagPdfJson>,
)

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
)
@JsonSubTypes(
    JsonSubTypes.Type(
        value = SamletÅrsgrunnlagPdfJson.HarIkkeSkattegrunnlagForÅrOgStadie::class,
        name = "HarIkkeSkattegrunnlag",
    ),
    JsonSubTypes.Type(
        value = SamletÅrsgrunnlagPdfJson.HarSkattegrunnlagForÅrOgStadie::class,
        name = "HarSkattegrunnlag",
    ),
)
sealed interface SamletÅrsgrunnlagPdfJson {
    val år: Int
    val stadie: ÅrsgrunnlagStadie

    data class HarSkattegrunnlagForÅrOgStadie(
        override val år: Int,
        override val stadie: ÅrsgrunnlagStadie,
        val oppgjørsdato: LocalDate?,
        val formue: List<SkattegrunnlagPdfJson.Formue> = emptyList(),
        val inntekt: List<SkattegrunnlagPdfJson.Inntekt> = emptyList(),
        val inntektsfradrag: List<SkattegrunnlagPdfJson.Inntektsfradrag> = emptyList(),
        val formuesfradrag: List<SkattegrunnlagPdfJson.Formuesfradrag> = emptyList(),
        val verdsettingsrabattSomGirGjeldsreduksjon: List<SkattegrunnlagPdfJson.VerdsettingsrabattSomGirGjeldsreduksjon> = emptyList(),
        val oppjusteringAvEierinntekter: List<SkattegrunnlagPdfJson.OppjusteringAvEierinntekter> = emptyList(),
        val manglerKategori: List<SkattegrunnlagPdfJson.ManglerKategori> = emptyList(),
        val annet: List<SkattegrunnlagPdfJson.Annet> = emptyList(),
        val kjøretøy: List<SpesifiseringPdfJson.KjøretøyJson>,
    ) : SamletÅrsgrunnlagPdfJson

    data class HarIkkeSkattegrunnlagForÅrOgStadie(
        override val år: Int,
        override val stadie: ÅrsgrunnlagStadie,
        val grunn: HarIkkeSkattegrunnlagFordi,
    ) : SamletÅrsgrunnlagPdfJson {

        enum class HarIkkeSkattegrunnlagFordi {
            NETTVERKSFEIL,
            FINNES_IKKE,
            UKJENT_FEIL,
            MANGLER_RETTIGHETER,
            PERSON_FEIL,
            ;

            companion object {
                fun KunneIkkeHenteSkattemelding.tilPdfJson(): HarIkkeSkattegrunnlagFordi = when (this) {
                    KunneIkkeHenteSkattemelding.FinnesIkke -> FINNES_IKKE
                    KunneIkkeHenteSkattemelding.ManglerRettigheter -> MANGLER_RETTIGHETER
                    KunneIkkeHenteSkattemelding.Nettverksfeil -> NETTVERKSFEIL
                    KunneIkkeHenteSkattemelding.PersonFeil -> PERSON_FEIL
                    KunneIkkeHenteSkattemelding.UkjentFeil -> UKJENT_FEIL
                }
            }
        }
    }

    companion object {
        fun NonEmptyList<SamletSkattegrunnlagForÅrOgStadie>.tilPdfJson(): NonEmptyList<SamletÅrsgrunnlagPdfJson> =
            this.map { it.tilPdfJson() }

        fun SamletSkattegrunnlagForÅrOgStadie.tilPdfJson(): SamletÅrsgrunnlagPdfJson {
            return when (val oppslag = this.oppslag) {
                is Either.Left -> HarIkkeSkattegrunnlagForÅrOgStadie(
                    år = this.inntektsår.value,
                    stadie = when (this) {
                        is SamletSkattegrunnlagForÅrOgStadie.Oppgjør -> ÅrsgrunnlagStadie.Oppgjør
                        is SamletSkattegrunnlagForÅrOgStadie.Utkast -> ÅrsgrunnlagStadie.Utkast
                    },
                    grunn = oppslag.value.tilPdfJson(),
                )

                is Either.Right -> HarSkattegrunnlagForÅrOgStadie(
                    år = this.inntektsår.value,
                    stadie = when (this) {
                        is SamletSkattegrunnlagForÅrOgStadie.Oppgjør -> ÅrsgrunnlagStadie.Oppgjør
                        is SamletSkattegrunnlagForÅrOgStadie.Utkast -> ÅrsgrunnlagStadie.Utkast
                    },
                    oppgjørsdato = oppslag.value.oppgjørsdato,
                    formue = oppslag.value.formue.tilPdfJson(),
                    inntekt = oppslag.value.inntekt.tilPdfJson(),
                    inntektsfradrag = oppslag.value.inntektsfradrag.tilPdfJson(),
                    formuesfradrag = oppslag.value.formuesfradrag.tilPdfJson(),
                    verdsettingsrabattSomGirGjeldsreduksjon = oppslag.value.verdsettingsrabattSomGirGjeldsreduksjon.tilPdfJson(),
                    oppjusteringAvEierinntekter = oppslag.value.oppjusteringAvEierinntekter.tilPdfJson(),
                    manglerKategori = oppslag.value.manglerKategori.tilPdfJson(),
                    annet = oppslag.value.annet.tilPdfJson(),
                    kjøretøy = oppslag.value.formue.hentKjøretøyPdfJson(),
                )
            }
        }
    }
}

enum class ÅrsgrunnlagStadie {
    Oppgjør,
    Utkast,
}

sealed interface SkattegrunnlagPdfJson {
    val tekniskNavn: String
    val beløp: String
    val spesifisering: List<SpesifiseringPdfJson>

    data class Formue(
        override val tekniskNavn: String,
        override val beløp: String,
    ) : SkattegrunnlagPdfJson {
        /**
         * Formue feltet som skal til pdf'en skal ikke ha kjøretøy i seg. Den skal være i et eget felt
         */
        override val spesifisering: List<SpesifiseringPdfJson.KjøretøyJson> = emptyList()

        companion object {
            fun Skattegrunnlag.Grunnlag.Formue.tilPdfJson(): Formue = Formue(navn, beløp)
            fun List<Skattegrunnlag.Grunnlag.Formue>.tilPdfJson(): List<Formue> = this.map { it.tilPdfJson() }
            fun List<Skattegrunnlag.Grunnlag.Formue>.hentKjøretøyPdfJson(): List<SpesifiseringPdfJson.KjøretøyJson> =
                this.flatMap { it.spesifisering.tilPdfJson() }
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
