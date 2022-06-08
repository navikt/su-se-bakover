package no.nav.su.se.bakover.domain

import arrow.core.getOrHandle
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.domain.Boforhold.OppgittAdresse.IngenAdresse.IngenAdresseGrunn
import no.nav.su.se.bakover.domain.søknadinnhold.Oppholdstillatelse
import java.time.LocalDate
import java.time.Month.JANUARY

fun fnrUnder67(): Fnr {
    // Bør ikke basere oss på .now() i tester. Dersom vi begynner å validere på dette, bør vi sende inn en klokke hit.
    return Fnr("01017001337")
}

val fnrOver67 = Fnr("05064535694")

fun personopplysninger(
    fnr: String = "12345678910"
) = Personopplysninger(
    Fnr(fnr)
)

fun boforhold(
    borOgOppholderSegINorge: Boolean = true,
    delerBolig: Boolean = true,
    delerBoligMed: Boforhold.DelerBoligMed? = Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER,
    ektefellePartnerSamboer: Boforhold.EktefellePartnerSamboer? = Boforhold.EktefellePartnerSamboer(
        erUførFlyktning = false,
        fnr = fnrUnder67()
    ),
    innlagtPåInstitusjon: InnlagtPåInstitusjon? = InnlagtPåInstitusjon(
        datoForInnleggelse = LocalDate.of(2020, JANUARY, 1),
        datoForUtskrivelse = LocalDate.of(2020, JANUARY, 31),
        fortsattInnlagt = false
    ),
    oppgittAdresse: Boforhold.OppgittAdresse? = Boforhold.OppgittAdresse.IngenAdresse(IngenAdresseGrunn.HAR_IKKE_FAST_BOSTED)
) = Boforhold(
    borOgOppholderSegINorge = borOgOppholderSegINorge,
    delerBolig = delerBolig,
    delerBoligMed = delerBoligMed,
    ektefellePartnerSamboer = ektefellePartnerSamboer,
    innlagtPåInstitusjon = innlagtPåInstitusjon,
    oppgittAdresse = oppgittAdresse
)

fun utenlandsopphold(
    registrertePerioder: List<UtenlandsoppholdPeriode>? = listOf(
        UtenlandsoppholdPeriode(
            1.januar(2020),
            31.januar(2020),
        ),
        UtenlandsoppholdPeriode(
            1.februar(2020),
            5.februar(2020),
        )
    ),
    planlagtePerioder: List<UtenlandsoppholdPeriode>? = listOf(
        UtenlandsoppholdPeriode(
            1.juli(2020),
            31.juli(2020),
        )
    )
) =
    Utenlandsopphold(
        registrertePerioder = registrertePerioder,
        planlagtePerioder = planlagtePerioder,
    )

fun oppholdstillatelse(
    erNorskStatsborger: Boolean = false,
    harOppholdstillatelse: Boolean? = true,
    oppholdstillatelseType: Oppholdstillatelse.OppholdstillatelseType? = Oppholdstillatelse.OppholdstillatelseType.MIDLERTIDIG,
    statsborgerskapAndreLand: Boolean = false,
    statsborgerskapAndreLandFritekst: String? = null
) =
    Oppholdstillatelse.tryCreate(
        erNorskStatsborger = erNorskStatsborger,
        harOppholdstillatelse = harOppholdstillatelse,
        oppholdstillatelseType = oppholdstillatelseType,
        statsborgerskapAndreLand = statsborgerskapAndreLand,
        statsborgerskapAndreLandFritekst = statsborgerskapAndreLandFritekst,
    ).getOrHandle { throw IllegalArgumentException("Feil ved opprettelse av test data") }

fun inntektOgPensjon() = InntektOgPensjon(
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
)

fun formue() = Formue(
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
)

fun ektefelle() = Ektefelle(
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
)

fun forNavDigitalSøknad() = ForNav.DigitalSøknad(
    harFullmektigEllerVerge = ForNav.DigitalSøknad.Vergemål.VERGE
)

fun søknadsinnholdAlder(
    harSøktAlderspensjon: HarSøktAlderspensjon = HarSøktAlderspensjon(false),
    oppholdstillatelseAlder: OppholdstillatelseAlder = OppholdstillatelseAlder(eøsborger = false, familiegjenforening = false),
    oppholdstillatelse: Oppholdstillatelse = oppholdstillatelse(),
    personopplysninger: Personopplysninger = Personopplysninger(fnrOver67),
    boforhold: Boforhold = boforhold(),
    utenlandsopphold: Utenlandsopphold = utenlandsopphold(),
    inntektOgPensjon: InntektOgPensjon = inntektOgPensjon(),
    formue: Formue = formue(),
    forNav: ForNav = forNavDigitalSøknad(),
    ektefelle: Ektefelle? = ektefelle(),
) = SøknadsinnholdAlder(
    harSøktAlderspensjon = harSøktAlderspensjon,
    personopplysninger = personopplysninger,
    boforhold = boforhold,
    oppholdstillatelseAlder = oppholdstillatelseAlder,
    utenlandsopphold = utenlandsopphold,
    oppholdstillatelse = oppholdstillatelse,
    inntektOgPensjon = inntektOgPensjon,
    formue = formue,
    forNav = forNav,
    ektefelle = ektefelle,
)

object SøknadInnholdTestdataBuilder {
    fun personopplysninger(
        fnr: String = "12345678910"
    ) = Personopplysninger(
        Fnr(fnr)
    )

    fun build(
        uførevedtak: Uførevedtak = Uførevedtak(true),
        personopplysninger: Personopplysninger = personopplysninger(),
        flyktningsstatus: Flyktningsstatus = Flyktningsstatus(
            registrertFlyktning = false
        ),
        boforhold: Boforhold = boforhold(),
        utenlandsopphold: Utenlandsopphold = utenlandsopphold(),
        oppholdstillatelse: Oppholdstillatelse = oppholdstillatelse(),
        inntektOgPensjon: InntektOgPensjon = inntektOgPensjon(),
        formue: Formue = formue(),
        forNav: ForNav = forNavDigitalSøknad(),
        ektefelle: Ektefelle = ektefelle(),
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
