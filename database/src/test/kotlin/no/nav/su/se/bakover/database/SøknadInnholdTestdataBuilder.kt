package no.nav.su.se.bakover.database

import no.nav.su.se.bakover.domain.Boforhold
import no.nav.su.se.bakover.domain.Flyktningsstatus
import no.nav.su.se.bakover.domain.ForNav
import no.nav.su.se.bakover.domain.Formue
import no.nav.su.se.bakover.domain.InntektOgPensjon
import no.nav.su.se.bakover.domain.Oppholdstillatelse
import no.nav.su.se.bakover.domain.PensjonsOrdningBeløp
import no.nav.su.se.bakover.domain.Personopplysninger
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.domain.Uførevedtak
import no.nav.su.se.bakover.domain.Utenlandsopphold
import no.nav.su.se.bakover.domain.UtenlandsoppholdPeriode
import java.time.LocalDate
import java.time.Month.FEBRUARY
import java.time.Month.JANUARY
import java.time.Month.JULY

/**
 * TODO John Andre Hestad: Det skal være mulig å bygge en testJar og importere denne fra gradle.
 */
class SøknadInnholdTestdataBuilder {
    companion object {
        fun personopplysninger(
            fnr: String = "12345678910",
            fornavn: String = "Ola",
            mellomnavn: String = "Erik",
            etternavn: String = "Nordmann",
            telefonnummer: String = "12345678",
            gateadresse: String = "Oslogata 12",
            postnummer: String = "0050",
            poststed: String = "Oslo",
            bruksenhet: String = "U1H20",
            bokommune: String = "Oslo",
            statsborgerskap: String = "NOR"
        ) = Personopplysninger(
                fnr, fornavn, mellomnavn, etternavn, telefonnummer, gateadresse, postnummer, poststed, bruksenhet, bokommune, statsborgerskap
        )

        fun build(
            uførevedtak: Uførevedtak = Uførevedtak(true),
            personopplysninger: Personopplysninger = personopplysninger(),

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
                            UtenlandsoppholdPeriode(LocalDate.of(2020, JANUARY, 1), LocalDate.of(2020, JANUARY, 31)),
                            UtenlandsoppholdPeriode(LocalDate.of(2020, FEBRUARY, 1), LocalDate.of(2020, FEBRUARY, 5))),
                    planlagtePerioder = listOf(
                            UtenlandsoppholdPeriode(LocalDate.of(2020, JULY, 1), LocalDate.of(2020, JULY, 31))
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
                    trygdeytelserIUtlandetBeløp = 2,
                    trygdeytelserIUtlandet = "en-eller-annen-ytelse",
                    trygdeytelserIUtlandetFra = "Utlandet",
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
                    Kontonummer = "12345678912",
                    verdiPåEiendom = 3,
                    eiendomBrukesTil = "",
                    verdiPåKjøretøy = 25000,
                    kjøretøyDeEier = "bil",
                    innskuddsBeløp = 25000,
                    verdipapirBeløp = 25000,
                    skylderNoenMegPengerBeløp = 25000,
                    kontanterBeløp = 25000
            ),

            forNav: ForNav = ForNav(
                    harFullmektigEllerVerge = ForNav.Vergemål.VERGE
            )
        ) = SøknadInnhold(uførevedtak, personopplysninger, flyktningsstatus, boforhold, utenlandsopphold, oppholdstillatelse, inntektOgPensjon, formue, forNav)
    }
}
