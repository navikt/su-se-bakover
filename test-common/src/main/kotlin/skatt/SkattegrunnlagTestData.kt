package no.nav.su.se.bakover.test.skatt

import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
import no.nav.su.se.bakover.domain.skatt.Stadie
import no.nav.su.se.bakover.test.fixedClock
import java.time.Clock
import java.time.LocalDate
import java.time.Year

fun nySkattegrunnlag(
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    årsgrunnlag: Skattegrunnlag.Årsgrunnlag = nyÅrsgrunnlag(),
    clock: Clock = fixedClock,
    hentetTidspunkt: Tidspunkt = Tidspunkt.now(clock),
): Skattegrunnlag {
    return Skattegrunnlag(
        fnr = fnr,
        hentetTidspunkt = hentetTidspunkt,
        årsgrunnlag = årsgrunnlag,
    )
}

fun nyÅrsgrunnlag(
    inntektsÅr: Year = Year.of(2021),
    skatteoppgjørsdato: LocalDate = 1.april(2021),
    grunnlag: Skattegrunnlag.Grunnlagsliste = nyGrunnlagsliste(),
    stadie: Stadie = Stadie.FASTSATT,
) = Skattegrunnlag.Årsgrunnlag(
    inntektsår = inntektsÅr,
    skatteoppgjørsdato = skatteoppgjørsdato,
    grunnlag = grunnlag,
    stadie = stadie,
)

fun nyGrunnlagsliste(
    inntekt: List<Skattegrunnlag.Grunnlag.Inntekt> = nyListeAvSkattegrunnlagInntekt(),
    formue: List<Skattegrunnlag.Grunnlag.Formue> = nyListeAvSkattegrunnlagFormue(),
    formuesFradrag: List<Skattegrunnlag.Grunnlag.Formuesfradrag> = nyListeAvFormuesfradrag(),
    inntektsFradrag: List<Skattegrunnlag.Grunnlag.Inntektsfradrag> = nyListeAvInntektsfradrag(),
) = Skattegrunnlag.Grunnlagsliste(
    inntekt = inntekt,
    formue = formue,
    formuesfradrag = formuesFradrag,
    inntektsfradrag = inntektsFradrag,
)

fun nyListeAvSkattegrunnlagInntekt(
    input: List<Skattegrunnlag.Grunnlag.Inntekt> = listOf(nySkattegrunnlagInntekt()),
) = input

fun nySkattegrunnlagInntekt(
    navn: String = "alminneligInntektFoerSaerfradrag",
    beløp: String = "1000",
) = Skattegrunnlag.Grunnlag.Inntekt(
    navn = navn,
    beløp = beløp,
)

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
    spesifisering: List<Skattegrunnlag.Spesifisering.Kjøretøy>? = null,
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
