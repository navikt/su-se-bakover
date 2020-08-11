package no.nav.su.se.bakover.domain

import java.time.LocalDate
import java.time.Month.FEBRUARY
import java.time.Month.JANUARY
import java.time.Month.JULY

/**
 * TODO John Andre Hestad: Det skal være mulig å bygge en testJar og importere denne fra gradle.
 */
object SøknadInnholdTestdataBuilder {
    fun personopplysninger(
        fnr: String = "12345678910",
        fornavn: String = "Ola",
        mellomnavn: String = "Erik",
        etternavn: String = "Nordmann",
        telefonnummerLandskode: String = "47",
        telefonnummer: String = "12345678",
        adressenavn: String = "Oslogata",
        husbokstav: String = "A",
        husnummer: String = "12",
        postnummer: String = "0050",
        poststed: String = "Oslo",
        bruksenhet: String = "U1H20",
        bokommune: String = "Oslo",
        kommunenummer: String = "0301",
        statsborgerskap: String = "NOR",
        kjønn: String = "MANN"
    ) = Person(
        fnr = Fnr(fnr),
        aktørId = AktørId("aktørid"),
        navn = Person.Navn(
            fornavn = fornavn,
            mellomnavn = mellomnavn,
            etternavn = etternavn
        ),
        telefonnummer = Telefonnummer(landskode = telefonnummerLandskode, nummer = telefonnummer),
        adresse = Person.Adresse(
            adressenavn = adressenavn,
            husnummer = husnummer,
            husbokstav = husbokstav,
            postnummer = postnummer,
            poststed = poststed,
            bruksenhet = bruksenhet,
            kommunenavn = bokommune,
            kommunenummer = kommunenummer
        ),
        statsborgerskap = statsborgerskap,
        kjønn = kjønn
    )

    fun build(
        uførevedtak: Uførevedtak = Uførevedtak(
            true
        ),
        personopplysninger: Person = personopplysninger(),

        flyktningsstatus: Flyktningsstatus = Flyktningsstatus(
            registrertFlyktning = false
        ),

        boforhold: Boforhold = Boforhold(
            borOgOppholderSegINorge = true,
            delerBolig = true,
            delerBoligMed = Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER,
            ektemakeEllerSamboerUnder67År = true,
            ektemakeEllerSamboerUførFlyktning = false
        ),

        utenlandsopphold: Utenlandsopphold = Utenlandsopphold(
            registrertePerioder = listOf(
                UtenlandsoppholdPeriode(
                    LocalDate.of(
                        2020,
                        JANUARY,
                        1
                    ),
                    LocalDate.of(2020, JANUARY, 31)
                ),
                UtenlandsoppholdPeriode(
                    LocalDate.of(
                        2020,
                        FEBRUARY,
                        1
                    ),
                    LocalDate.of(2020, FEBRUARY, 5)
                )
            ),
            planlagtePerioder = listOf(
                UtenlandsoppholdPeriode(
                    LocalDate.of(2020, JULY, 1),
                    LocalDate.of(2020, JULY, 31)
                )
            )
        ),

        oppholdstillatelse: Oppholdstillatelse = Oppholdstillatelse(
            erNorskStatsborger = false,
            harOppholdstillatelse = true,
            oppholdstillatelseType = Oppholdstillatelse.OppholdstillatelseType.MIDLERTIG,
            oppholdstillatelseMindreEnnTreMåneder = false,
            oppholdstillatelseForlengelse = true,
            statsborgerskapAndreLand = false,
            statsborgerskapAndreLandFritekst = null
        ),
        inntektOgPensjon: InntektOgPensjon = InntektOgPensjon(
            forventetInntekt = 2500,
            tjenerPengerIUtlandetBeløp = 20,
            andreYtelserINav = "sosialstønad",
            andreYtelserINavBeløp = 33,
            søktAndreYtelserIkkeBehandletBegrunnelse = "uføre",
            sosialstønadBeløp = 7000.0,
            trygdeytelseIUtlandet = listOf(
                TrygdeytelseIUtlandet(beløp = 200, type = "trygd", fra = "En trygdeutgiver"),
                TrygdeytelseIUtlandet(beløp = 500, type = "Annen trygd", fra = "En annen trygdeutgiver")
            ),
            pensjon = listOf(
                PensjonsOrdningBeløp("KLP", 2000.0),
                PensjonsOrdningBeløp("SPK", 5000.0)
            )
        ),
        formue: Formue = Formue(
            borIBolig = false,
            verdiPåBolig = 600000,
            boligBrukesTil = "Mine barn bor der",
            depositumsBeløp = 1000.0,
            kontonummer = "12345678912",
            verdiPåEiendom = 3,
            eiendomBrukesTil = "",
            kjøretøy = listOf(
                Kjøretøy(
                    verdiPåKjøretøy = 25000,
                    kjøretøyDeEier = "bil"
                )
            ),
            innskuddsBeløp = 25000,
            verdipapirBeløp = 25000,
            skylderNoenMegPengerBeløp = 25000,
            kontanterBeløp = 25000
        ),

        forNav: ForNav = ForNav(
            harFullmektigEllerVerge = ForNav.Vergemål.VERGE
        )
    ) = SøknadInnhold(
        uførevedtak,
        personopplysninger,
        flyktningsstatus,
        boforhold,
        utenlandsopphold,
        oppholdstillatelse,
        inntektOgPensjon,
        formue,
        forNav
    )
}
