package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.domain.Boforhold.OppgittAdresse.IngenAdresse.IngenAdresseGrunn
import java.time.LocalDate
import java.time.Month.JANUARY

fun fnrUnder67(): Fnr {
    // Bør ikke basere oss på .now() i tester. Dersom vi begynner å validere på dette, bør vi sende inn en klokke hit.
    return Fnr("01017001337")
}

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
            ektefellePartnerSamboer = Boforhold.EktefellePartnerSamboer(
                erUførFlyktning = false,
                fnr = fnrUnder67()
            ),
            innlagtPåInstitusjon = InnlagtPåInstitusjon(
                datoForInnleggelse = LocalDate.of(2020, JANUARY, 1),
                datoForUtskrivelse = LocalDate.of(2020, JANUARY, 31),
                fortsattInnlagt = false
            ),
            oppgittAdresse = Boforhold.OppgittAdresse.IngenAdresse(IngenAdresseGrunn.HAR_IKKE_FAST_BOSTED)
        ),

        utenlandsopphold: Utenlandsopphold = Utenlandsopphold(
            registrertePerioder = listOf(
                UtenlandsoppholdPeriode(
                    1.januar(2020),
                    31.januar(2020),
                ),
                UtenlandsoppholdPeriode(
                    1.februar(2020),
                    5.februar(2020),
                )
            ),
            planlagtePerioder = listOf(
                UtenlandsoppholdPeriode(
                    1.juli(2020),
                    31.juli(2020),
                )
            )
        ),

        oppholdstillatelse: Oppholdstillatelse = Oppholdstillatelse(
            erNorskStatsborger = false,
            harOppholdstillatelse = true,
            oppholdstillatelseType = Oppholdstillatelse.OppholdstillatelseType.MIDLERTIDIG,
            statsborgerskapAndreLand = false,
            statsborgerskapAndreLandFritekst = null
        ),
        inntektOgPensjon: InntektOgPensjon = InntektOgPensjon(
            forventetInntekt = 2500,
            andreYtelserINav = "sosialstønad",
            andreYtelserINavBeløp = 33,
            søktAndreYtelserIkkeBehandletBegrunnelse = "uføre",
            trygdeytelseIUtlandet = listOf(
                TrygdeytelseIUtlandet(beløp = 200, type = "trygd", valuta = "En valuta"),
                TrygdeytelseIUtlandet(beløp = 500, type = "Annen trygd", valuta = "En annen valuta")
            ),
            pensjon = listOf(
                PensjonsOrdningBeløp("KLP", 2000.0),
                PensjonsOrdningBeløp("SPK", 5000.0)
            )
        ),
        formue: Formue = Formue(
            eierBolig = true,
            borIBolig = false,
            verdiPåBolig = 600000,
            boligBrukesTil = "Mine barn bor der",
            depositumsBeløp = 1000.0,
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

        forNav: ForNav = ForNav.DigitalSøknad(
            harFullmektigEllerVerge = ForNav.DigitalSøknad.Vergemål.VERGE
        ),
        ektefelle: Ektefelle = Ektefelle(
            formue = Formue(
                eierBolig = true,
                borIBolig = false,
                verdiPåBolig = 0,
                boligBrukesTil = "",
                depositumsBeløp = 0,
                verdiPåEiendom = 0,
                eiendomBrukesTil = "",
                kjøretøy = listOf(),
                innskuddsBeløp = 0,
                verdipapirBeløp = 0,
                skylderNoenMegPengerBeløp = 0,
                kontanterBeløp = 0
            ),
            inntektOgPensjon = InntektOgPensjon(
                forventetInntekt = null,
                andreYtelserINav = null,
                andreYtelserINavBeløp = null,
                søktAndreYtelserIkkeBehandletBegrunnelse = null,
                trygdeytelseIUtlandet = null,
                pensjon = null,
            ),
        ),
    ) = SøknadsinnholdUføre(
        uførevedtak = uførevedtak,
        flyktningsstatus = flyktningsstatus,
        personopplysninger = personopplysninger,
        boforhold = boforhold,
        utenlandsopphold = utenlandsopphold,
        oppholdstillatelse = oppholdstillatelse,
        inntektOgPensjon = inntektOgPensjon,
        formue = formue,
        forNav = forNav,
        ektefelle = ektefelle,
    )
}
