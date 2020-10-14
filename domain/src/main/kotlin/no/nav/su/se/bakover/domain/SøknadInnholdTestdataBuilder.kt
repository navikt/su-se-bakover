package no.nav.su.se.bakover.domain

import java.time.LocalDate
import java.time.Month.FEBRUARY
import java.time.Month.JANUARY
import java.time.Month.JULY

fun fnrUnder67(): Fnr {
    val femtiÅrSiden = (LocalDate.now().year - 50).toString().substring(2, 4)

    return Fnr("0101${femtiÅrSiden}01337")
}

/**
 * TODO John Andre Hestad: Det skal være mulig å bygge en testJar og importere denne fra gradle.
 */
object SøknadInnholdTestdataBuilder {
    fun personopplysninger(
        fnr: String = "12345678910"
    ) = Personopplysninger(
        Fnr(fnr)
    )

    fun build(
        uførevedtak: Uførevedtak = Uførevedtak(
            true
        ),
        personopplysninger: Personopplysninger = personopplysninger(),

        flyktningsstatus: Flyktningsstatus = Flyktningsstatus(
            registrertFlyktning = false
        ),

        boforhold: Boforhold = Boforhold(
            borOgOppholderSegINorge = true,
            delerBolig = true,
            delerBoligMed = Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER,
            ektefellePartnerSamboer = Boforhold.EktefellePartnerSamboer.EktefellePartnerSamboerMedFnr(
                erUførFlyktning = false,
                fnr = fnrUnder67()
            )
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
