package no.nav.su.se.bakover.test.skatt

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.YearRange
import no.nav.su.se.bakover.test.fixedClock
import vilkår.skatt.domain.KunneIkkeHenteSkattemelding
import vilkår.skatt.domain.SamletSkattegrunnlagForÅr
import vilkår.skatt.domain.SamletSkattegrunnlagForÅrOgStadie
import vilkår.skatt.domain.Skattegrunnlag
import vilkår.skatt.domain.toYearRange
import java.time.Clock
import java.time.LocalDate
import java.time.Year
import java.util.UUID

fun nySkattegrunnlag(
    id: UUID = UUID.randomUUID(),
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    årsgrunnlag: NonEmptyList<SamletSkattegrunnlagForÅrOgStadie> = nonEmptyListOf(
        nySamletSkattegrunnlagForÅrOgStadieOppgjør(),
    ),
    clock: Clock = fixedClock,
    hentetTidspunkt: Tidspunkt = Tidspunkt.now(clock),
    årSpurtFor: YearRange = årsgrunnlag.toYearRange(),
) = Skattegrunnlag(
    id = id,
    fnr = fnr,
    hentetTidspunkt = hentetTidspunkt,
    saksbehandler = saksbehandler,
    årsgrunnlag = årsgrunnlag,
    årSpurtFor = årSpurtFor,
)

fun nySkattegrunnlagMedFeilIÅrsgrunnlag(
    id: UUID = UUID.randomUUID(),
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    årsgrunnlag: NonEmptyList<SamletSkattegrunnlagForÅrOgStadie> = nonEmptyListOf(
        nySamletSkattegrunnlagForÅrOgStadieOppgjørMedFeilIÅrsgrunnlag(),
    ),
    clock: Clock = fixedClock,
    hentetTidspunkt: Tidspunkt = Tidspunkt.now(clock),
    årSpurtFor: YearRange = årsgrunnlag.toYearRange(),
) = Skattegrunnlag(
    id = id,
    fnr = fnr,
    hentetTidspunkt = hentetTidspunkt,
    saksbehandler = saksbehandler,
    årsgrunnlag = årsgrunnlag,
    årSpurtFor = årSpurtFor,
)

fun nySamletSkattegrunnlagForÅr(
    oppgjør: SamletSkattegrunnlagForÅrOgStadie.Oppgjør = nySamletSkattegrunnlagForÅrOgStadieOppgjør(),
    utkast: SamletSkattegrunnlagForÅrOgStadie.Utkast = nySamletSkattegrunnlagForÅrOgStadieUtkast(),
    år: Year = Year.of(2021),
): SamletSkattegrunnlagForÅr = SamletSkattegrunnlagForÅr(utkast = utkast, oppgjør = oppgjør, år = år)

fun nySamletSkattegrunnlagForÅrOgStadieOppgjør(
    inntektsÅr: Year = Year.of(2021),
    oppslag: Either<KunneIkkeHenteSkattemelding, Skattegrunnlag.SkattegrunnlagForÅr> = nySkattegrunnlagForÅr().right(),
): SamletSkattegrunnlagForÅrOgStadie.Oppgjør = SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
    oppslag = oppslag,
    inntektsår = inntektsÅr,
)

fun nySamletSkattegrunnlagForÅrOgStadieUtkast(
    inntektsÅr: Year = Year.of(2021),
    oppslag: Either<KunneIkkeHenteSkattemelding, Skattegrunnlag.SkattegrunnlagForÅr> = nySkattegrunnlagForÅr().right(),
): SamletSkattegrunnlagForÅrOgStadie.Utkast = SamletSkattegrunnlagForÅrOgStadie.Utkast(
    oppslag = oppslag,
    inntektsår = inntektsÅr,
)

fun nySamletSkattegrunnlagForÅrOgStadieOppgjørMedFeilIÅrsgrunnlag(
    inntektsÅr: Year = Year.of(2021),
    oppslag: Either<KunneIkkeHenteSkattemelding, Skattegrunnlag.SkattegrunnlagForÅr> = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
): SamletSkattegrunnlagForÅrOgStadie.Oppgjør = SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
    oppslag = oppslag,
    inntektsår = inntektsÅr,
)

fun nySkattegrunnlagForÅr(
    oppgjørsdato: LocalDate? = null,
    inntekt: List<Skattegrunnlag.Grunnlag.Inntekt> = nyListeAvSkattegrunnlagInntekt(),
    formue: List<Skattegrunnlag.Grunnlag.Formue> = nyListeAvSkattegrunnlagFormue(),
    formuesFradrag: List<Skattegrunnlag.Grunnlag.Formuesfradrag> = nyListeAvFormuesfradrag(),
    inntektsfradrag: List<Skattegrunnlag.Grunnlag.Inntektsfradrag> = nyListeAvInntektsfradrag(),
    verdsettingsrabattSomGirGjeldsreduksjon: List<Skattegrunnlag.Grunnlag.VerdsettingsrabattSomGirGjeldsreduksjon> = nyListeAvVerdsettingsrabattSomGirGjeldsreduksjon(),
    oppjusteringAvEierinntekt: List<Skattegrunnlag.Grunnlag.OppjusteringAvEierinntekter> = nyListeAvOppjusteringAvEierinntekt(),
    manglerKategori: List<Skattegrunnlag.Grunnlag.ManglerKategori> = nyListeAvManglerKategori(),
    annet: List<Skattegrunnlag.Grunnlag.Annet> = nyListeAvAnnet(),
) = Skattegrunnlag.SkattegrunnlagForÅr(
    oppgjørsdato = oppgjørsdato, inntekt = inntekt, formue = formue,
    formuesfradrag = formuesFradrag, inntektsfradrag = inntektsfradrag,
    verdsettingsrabattSomGirGjeldsreduksjon = verdsettingsrabattSomGirGjeldsreduksjon,
    oppjusteringAvEierinntekter = oppjusteringAvEierinntekt,
    manglerKategori = manglerKategori, annet = annet,
)

fun nyListeAvSkattegrunnlagInntekt(
    input: List<Skattegrunnlag.Grunnlag.Inntekt> = listOf(nySkattegrunnlagInntekt()),
) = input

fun nySkattegrunnlagInntekt(
    navn: String = "alminneligInntektFoerSaerfradrag",
    beløp: String = "1000",
) = Skattegrunnlag.Grunnlag.Inntekt(navn = navn, beløp = beløp)

fun nyListeAvSkattegrunnlagFormue(
    input: List<Skattegrunnlag.Grunnlag.Formue> = listOf(
        nySkattegrunnlagFormue(),
        nySkattegrunnlagFormue(
            navn = "formuesverdiForKjoeretoey",
            beløp = "20000",
            spesifisering = nyListeAvSpesifiseringKjøretøy(),
        ),
    ),
) = input

fun nySkattegrunnlagFormue(
    navn: String = "bruttoformue",
    beløp: String = "1238",
    spesifisering: List<Skattegrunnlag.Spesifisering.Kjøretøy> = emptyList(),
) = Skattegrunnlag.Grunnlag.Formue(navn = navn, beløp = beløp, spesifisering)

fun nyListeAvSpesifiseringKjøretøy(
    input: List<Skattegrunnlag.Spesifisering.Kjøretøy> = listOf(
        nySpesifiseringKjøretøy(
            beløp = "15000",
            registreringsnummer = "AB12345",
            fabrikatnavn = "Troll",
            årForFørstegangsregistrering = "1957",
            formuesverdi = "15000",
        ),
        nySpesifiseringKjøretøy(),
    ),
) = input

fun nySpesifiseringKjøretøy(
    beløp: String = "5000",
    registreringsnummer: String = "BC67890",
    fabrikatnavn: String = "Think",
    årForFørstegangsregistrering: String = "2003",
    formuesverdi: String = "5000",
) = Skattegrunnlag.Spesifisering.Kjøretøy(
    beløp = beløp,
    registreringsnummer = registreringsnummer,
    fabrikatnavn = fabrikatnavn,
    årForFørstegangsregistrering = årForFørstegangsregistrering,
    formuesverdi = formuesverdi,
)

fun nyListeAvFormuesfradrag(
    formuesFradrag: List<Skattegrunnlag.Grunnlag.Formuesfradrag> = listOf(nyFormuesfradrag()),
) = formuesFradrag

fun nyFormuesfradrag(
    navn: String = "samletAnnenGjeld",
    beløp: String = "6000",
) = Skattegrunnlag.Grunnlag.Formuesfradrag(navn = navn, beløp = beløp)

fun nyListeAvInntektsfradrag(
    inntektsfradrager: List<Skattegrunnlag.Grunnlag.Inntektsfradrag> = listOf(nyInntektsFradrag()),
) = inntektsfradrager

fun nyInntektsFradrag(
    navn: String = "fradragForFagforeningskontingent",
    beløp: String = "4000",
) = Skattegrunnlag.Grunnlag.Inntektsfradrag(navn = navn, beløp = beløp)

fun nyListeAvVerdsettingsrabattSomGirGjeldsreduksjon(
    verdsetting: List<Skattegrunnlag.Grunnlag.VerdsettingsrabattSomGirGjeldsreduksjon> = listOf(
        nyVerdsettingsrabattSomGirGjeldsreduksjon(),
    ),
) = verdsetting

fun nyVerdsettingsrabattSomGirGjeldsreduksjon(
    navn: String = "fradragForFagforeningskontingent",
    beløp: String = "4000",
) = Skattegrunnlag.Grunnlag.VerdsettingsrabattSomGirGjeldsreduksjon(navn = navn, beløp = beløp)

fun nyListeAvOppjusteringAvEierinntekt(
    oppjustering: List<Skattegrunnlag.Grunnlag.OppjusteringAvEierinntekter> = listOf(nyOppjusteringAvEierinntekt()),
) = oppjustering

fun nyOppjusteringAvEierinntekt(
    navn: String = "fradragForFagforeningskontingent",
    beløp: String = "4000",
) = Skattegrunnlag.Grunnlag.OppjusteringAvEierinntekter(navn = navn, beløp = beløp)

fun nyListeAvManglerKategori(
    mangler: List<Skattegrunnlag.Grunnlag.ManglerKategori> = listOf(nyManglerKategori()),
) = mangler

fun nyManglerKategori(
    navn: String = "fradragForFagforeningskontingent",
    beløp: String = "4000",
) = Skattegrunnlag.Grunnlag.ManglerKategori(navn = navn, beløp = beløp)

fun nyListeAvAnnet(annet: List<Skattegrunnlag.Grunnlag.Annet> = listOf(nyAnnet())) = annet

fun nyAnnet(
    navn: String = "fradragForFagforeningskontingent",
    beløp: String = "4000",
) = Skattegrunnlag.Grunnlag.Annet(navn = navn, beløp = beløp)
