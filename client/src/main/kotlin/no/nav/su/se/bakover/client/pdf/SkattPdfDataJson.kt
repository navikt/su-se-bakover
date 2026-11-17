package no.nav.su.se.bakover.client.pdf

import arrow.core.Either
import arrow.core.NonEmptyList
import dokument.domain.generer.bygger.maler.skattegrunnlag.SamletÅrsgrunnlagPdfJson
import dokument.domain.generer.bygger.maler.skattegrunnlag.SamletÅrsgrunnlagPdfJson.HarIkkeSkattegrunnlagForÅrOgStadie.HarIkkeSkattegrunnlagFordi
import dokument.domain.generer.bygger.maler.skattegrunnlag.SkattegrunnlagPdfJson
import dokument.domain.generer.bygger.maler.skattegrunnlag.SpesifiseringPdfJson
import dokument.domain.generer.bygger.maler.skattegrunnlag.ÅrsgrunnlagStadie
import vilkår.skatt.domain.KunneIkkeHenteSkattemelding
import vilkår.skatt.domain.SamletSkattegrunnlagForÅrOgStadie
import vilkår.skatt.domain.Skattegrunnlag

fun KunneIkkeHenteSkattemelding.tilPdfJson(): HarIkkeSkattegrunnlagFordi = when (this) {
    KunneIkkeHenteSkattemelding.FinnesIkke -> HarIkkeSkattegrunnlagFordi.FINNES_IKKE
    KunneIkkeHenteSkattemelding.ManglerRettigheter -> HarIkkeSkattegrunnlagFordi.MANGLER_RETTIGHETER
    KunneIkkeHenteSkattemelding.Nettverksfeil -> HarIkkeSkattegrunnlagFordi.NETTVERKSFEIL
    KunneIkkeHenteSkattemelding.UkjentFeil -> HarIkkeSkattegrunnlagFordi.UKJENT_FEIL
    KunneIkkeHenteSkattemelding.OppslagetInneholdtUgyldigData -> HarIkkeSkattegrunnlagFordi.OPPSLAGET_INNEHOLDT_UGYLDIG_DATA
}

fun NonEmptyList<SamletSkattegrunnlagForÅrOgStadie>.tilPdfJson(): NonEmptyList<SamletÅrsgrunnlagPdfJson> =
    this.map { it.tilPdfJson() }

fun SamletSkattegrunnlagForÅrOgStadie.tilPdfJson(): SamletÅrsgrunnlagPdfJson {
    val stadie = when (this) {
        is SamletSkattegrunnlagForÅrOgStadie.Oppgjør -> ÅrsgrunnlagStadie.Oppgjør
        is SamletSkattegrunnlagForÅrOgStadie.Utkast -> ÅrsgrunnlagStadie.Utkast
    }
    return when (val oppslag = this.oppslag) {
        is Either.Left -> SamletÅrsgrunnlagPdfJson.HarIkkeSkattegrunnlagForÅrOgStadie(
            år = this.inntektsår.value,
            stadie = stadie,
            grunn = oppslag.value.tilPdfJson(),
        )

        is Either.Right -> SamletÅrsgrunnlagPdfJson.HarSkattegrunnlagForÅrOgStadie(
            år = this.inntektsår.value,
            stadie = stadie,
            oppgjørsdato = oppslag.value.oppgjørsdato,
            formue = oppslag.value.formue.tilPdfJson(),
            inntekt = oppslag.value.inntekt.tilPdfJson(),
            inntektsfradrag = oppslag.value.inntektsfradrag.tilPdfJson(),
            formuesfradrag = oppslag.value.formuesfradrag.tilPdfJson(),
            verdsettingsrabattSomGirGjeldsreduksjon = oppslag.value.verdsettingsrabattSomGirGjeldsreduksjon.tilPdfJson(),
            oppjusteringAvEierinntekter = oppslag.value.oppjusteringAvEierinntekter.tilPdfJson(),
            annet = oppslag.value.annet.tilPdfJson(),
            kjøretøy = oppslag.value.formue.hentKjøretøyPdfJson(),
        )
    }
}

fun Skattegrunnlag.Grunnlag.Formue.tilPdfJson(): SkattegrunnlagPdfJson.Formue = SkattegrunnlagPdfJson.Formue(navn, beløp)

@JvmName("formueTilPdfJson")
fun List<Skattegrunnlag.Grunnlag.Formue>.tilPdfJson(): List<SkattegrunnlagPdfJson.Formue> = this.map { it.tilPdfJson() }

fun List<Skattegrunnlag.Grunnlag.Formue>.hentKjøretøyPdfJson(): List<SpesifiseringPdfJson.KjøretøyJson> =
    this.flatMap { it.spesifisering.tilPdfJson() }

fun Skattegrunnlag.Grunnlag.Inntekt.tilPdfJson(): SkattegrunnlagPdfJson.Inntekt = SkattegrunnlagPdfJson.Inntekt(navn, beløp)

@JvmName("inntektTilPdfJson")
fun List<Skattegrunnlag.Grunnlag.Inntekt>.tilPdfJson(): List<SkattegrunnlagPdfJson.Inntekt> = this.map { it.tilPdfJson() }

fun Skattegrunnlag.Grunnlag.Inntektsfradrag.tilPdfJson(): SkattegrunnlagPdfJson.Inntektsfradrag =
    SkattegrunnlagPdfJson.Inntektsfradrag(navn, beløp)

@JvmName("inntektsfradragTilPdfJson")
fun List<Skattegrunnlag.Grunnlag.Inntektsfradrag>.tilPdfJson(): List<SkattegrunnlagPdfJson.Inntektsfradrag> =
    this.map { it.tilPdfJson() }

fun Skattegrunnlag.Grunnlag.Formuesfradrag.tilPdfJson(): SkattegrunnlagPdfJson.Formuesfradrag =
    SkattegrunnlagPdfJson.Formuesfradrag(navn, beløp)

@JvmName("formuesfradragTilPdfJson")
fun List<Skattegrunnlag.Grunnlag.Formuesfradrag>.tilPdfJson(): List<SkattegrunnlagPdfJson.Formuesfradrag> =
    this.map { it.tilPdfJson() }

fun Skattegrunnlag.Grunnlag.VerdsettingsrabattSomGirGjeldsreduksjon.tilPdfJson(): SkattegrunnlagPdfJson.VerdsettingsrabattSomGirGjeldsreduksjon =
    SkattegrunnlagPdfJson.VerdsettingsrabattSomGirGjeldsreduksjon(navn, beløp)

@JvmName("verdsettingsrabattSomGirGjeldsreduksjonTilPdfJson")
fun List<Skattegrunnlag.Grunnlag.VerdsettingsrabattSomGirGjeldsreduksjon>.tilPdfJson(): List<SkattegrunnlagPdfJson.VerdsettingsrabattSomGirGjeldsreduksjon> =
    this.map { it.tilPdfJson() }

fun Skattegrunnlag.Grunnlag.OppjusteringAvEierinntekter.tilPdfJson(): SkattegrunnlagPdfJson.OppjusteringAvEierinntekter =
    SkattegrunnlagPdfJson.OppjusteringAvEierinntekter(navn, beløp)

@JvmName("oppjusteringAvEierinntekterTilPdfJson")
fun List<Skattegrunnlag.Grunnlag.OppjusteringAvEierinntekter>.tilPdfJson(): List<SkattegrunnlagPdfJson.OppjusteringAvEierinntekter> =
    this.map { it.tilPdfJson() }

fun Skattegrunnlag.Grunnlag.Annet.tilPdfJson(): SkattegrunnlagPdfJson.Annet = SkattegrunnlagPdfJson.Annet(navn, beløp)

@JvmName("annetTilPdfJson")
fun List<Skattegrunnlag.Grunnlag.Annet>.tilPdfJson(): List<SkattegrunnlagPdfJson.Annet> = this.map { it.tilPdfJson() }

fun Skattegrunnlag.Spesifisering.Kjøretøy.tilPdfJson(): SpesifiseringPdfJson.KjøretøyJson = SpesifiseringPdfJson.KjøretøyJson(
    beløp = this.beløp,
    registreringsnummer = this.registreringsnummer,
    fabrikatnavn = this.fabrikatnavn,
    årForFørstegangsregistrering = this.årForFørstegangsregistrering,
    formuesverdi = this.formuesverdi,
    antattVerdiSomNytt = this.antattVerdiSomNytt,
    antattMarkedsverdi = this.antattMarkedsverdi,
)

@JvmName("kjøretøyTilPdfJson")
fun List<Skattegrunnlag.Spesifisering.Kjøretøy>.tilPdfJson(): List<SpesifiseringPdfJson.KjøretøyJson> =
    this.map { it.tilPdfJson() }
