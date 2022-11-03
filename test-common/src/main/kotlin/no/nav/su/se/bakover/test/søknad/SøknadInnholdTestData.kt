package no.nav.su.se.bakover.test.søknad

import arrow.core.getOrHandle
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.domain.søknadinnhold.Boforhold
import no.nav.su.se.bakover.domain.søknadinnhold.Ektefelle
import no.nav.su.se.bakover.domain.søknadinnhold.EktefellePartnerSamboer
import no.nav.su.se.bakover.domain.søknadinnhold.Flyktningsstatus
import no.nav.su.se.bakover.domain.søknadinnhold.ForNav
import no.nav.su.se.bakover.domain.søknadinnhold.Formue
import no.nav.su.se.bakover.domain.søknadinnhold.HarSøktAlderspensjon
import no.nav.su.se.bakover.domain.søknadinnhold.InnlagtPåInstitusjon
import no.nav.su.se.bakover.domain.søknadinnhold.InntektOgPensjon
import no.nav.su.se.bakover.domain.søknadinnhold.Kjøretøy
import no.nav.su.se.bakover.domain.søknadinnhold.OppgittAdresse
import no.nav.su.se.bakover.domain.søknadinnhold.Oppholdstillatelse
import no.nav.su.se.bakover.domain.søknadinnhold.OppholdstillatelseAlder
import no.nav.su.se.bakover.domain.søknadinnhold.PensjonsOrdningBeløp
import no.nav.su.se.bakover.domain.søknadinnhold.Personopplysninger
import no.nav.su.se.bakover.domain.søknadinnhold.SøknadsinnholdAlder
import no.nav.su.se.bakover.domain.søknadinnhold.SøknadsinnholdUføre
import no.nav.su.se.bakover.domain.søknadinnhold.TrygdeytelseIUtlandet
import no.nav.su.se.bakover.domain.søknadinnhold.Uførevedtak
import no.nav.su.se.bakover.domain.søknadinnhold.Utenlandsopphold
import no.nav.su.se.bakover.domain.søknadinnhold.UtenlandsoppholdPeriode
import no.nav.su.se.bakover.test.epsFnr
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.fnrOver67
import no.nav.su.se.bakover.test.fnrUnder67
import java.time.LocalDate
import java.time.Month.JANUARY

fun personopplysninger(
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
) = Personopplysninger(fnr)

fun boforhold(
    borOgOppholderSegINorge: Boolean = true,
    delerBolig: Boolean = true,
    delerBoligMed: Boforhold.DelerBoligMed? = Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER,
    epsFnr: Fnr = no.nav.su.se.bakover.test.epsFnr,
    ektefellePartnerSamboer: EktefellePartnerSamboer? = EktefellePartnerSamboer(
        erUførFlyktning = false,
        fnr = epsFnr,
    ),
    innlagtPåInstitusjon: InnlagtPåInstitusjon? = InnlagtPåInstitusjon(
        datoForInnleggelse = LocalDate.of(2020, JANUARY, 1),
        datoForUtskrivelse = LocalDate.of(2020, JANUARY, 31),
        fortsattInnlagt = false,
    ),
    oppgittAdresse: OppgittAdresse = OppgittAdresse.IngenAdresse(OppgittAdresse.IngenAdresse.IngenAdresseGrunn.HAR_IKKE_FAST_BOSTED),
) = Boforhold.tryCreate(
    borOgOppholderSegINorge = borOgOppholderSegINorge,
    delerBolig = delerBolig,
    delerBoligMed = delerBoligMed,
    ektefellePartnerSamboer = ektefellePartnerSamboer,
    innlagtPåInstitusjon = innlagtPåInstitusjon,
    oppgittAdresse = oppgittAdresse,
).getOrHandle { throw IllegalArgumentException("Feil ved oppsett av test-data") }

fun utenlandsopphold(
    registrertePerioder: List<UtenlandsoppholdPeriode>? = listOf(
        UtenlandsoppholdPeriode(
            1.januar(2020),
            31.januar(2020),
        ),
        UtenlandsoppholdPeriode(
            1.februar(2020),
            5.februar(2020),
        ),
    ),
    planlagtePerioder: List<UtenlandsoppholdPeriode>? = listOf(
        UtenlandsoppholdPeriode(
            1.juli(2020),
            31.juli(2020),
        ),
    ),
) = Utenlandsopphold(
    registrertePerioder = registrertePerioder,
    planlagtePerioder = planlagtePerioder,
)

fun oppholdstillatelse(
    erNorskStatsborger: Boolean = false,
    harOppholdstillatelse: Boolean? = true,
    oppholdstillatelseType: Oppholdstillatelse.OppholdstillatelseType? = Oppholdstillatelse.OppholdstillatelseType.MIDLERTIDIG,
    statsborgerskapAndreLand: Boolean = false,
    statsborgerskapAndreLandFritekst: String? = null,
) = Oppholdstillatelse.tryCreate(
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
        TrygdeytelseIUtlandet(beløp = 500, type = "Annen trygd", valuta = "En annen valuta"),
    ),
    pensjon = listOf(
        PensjonsOrdningBeløp("KLP", 2000.0),
        PensjonsOrdningBeløp("SPK", 5000.0),
    ),
)

fun formue() = Formue.tryCreate(
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
            kjøretøyDeEier = "bil",
        ),
    ),
    innskuddsBeløp = 25000,
    verdipapirBeløp = 25000,
    skylderNoenMegPengerBeløp = 25000,
    kontanterBeløp = 25000,
).getOrHandle { throw IllegalArgumentException("Feil ved oppsett av test-data") }

fun ektefelle() = Ektefelle(
    formue = Formue.tryCreate(
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
        kontanterBeløp = 0,
    ).getOrHandle { throw IllegalStateException("Feil ved oppsett av test-data") },
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
    harFullmektigEllerVerge = ForNav.DigitalSøknad.Vergemål.VERGE,
)

fun forNavPapirsøknad(
    mottaksdatoForSøknad: LocalDate = fixedLocalDate,
    grunnForPapirinnsending: ForNav.Papirsøknad.GrunnForPapirinnsending = ForNav.Papirsøknad.GrunnForPapirinnsending.MidlertidigUnntakFraOppmøteplikt,
    annenGrunn: String = "covid19",
) = ForNav.Papirsøknad(
    mottaksdatoForSøknad = mottaksdatoForSøknad,
    grunnForPapirinnsending = grunnForPapirinnsending,
    annenGrunn = annenGrunn,
)

fun søknadsinnholdAlder(
    harSøktAlderspensjon: HarSøktAlderspensjon = HarSøktAlderspensjon(false),
    oppholdstillatelseAlder: OppholdstillatelseAlder = OppholdstillatelseAlder(
        eøsborger = false,
        familiegjenforening = false,
    ),
    oppholdstillatelse: Oppholdstillatelse = oppholdstillatelse(),
    personopplysninger: Personopplysninger = Personopplysninger(fnrOver67),
    boforhold: Boforhold = boforhold(epsFnr = fnrUnder67),
    utenlandsopphold: Utenlandsopphold = utenlandsopphold(),
    inntektOgPensjon: InntektOgPensjon = inntektOgPensjon(),
    formue: Formue = formue(),
    forNav: ForNav = forNavDigitalSøknad(),
    ektefelle: Ektefelle? = ektefelle(),
) = SøknadsinnholdAlder.tryCreate(
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
).getOrHandle { throw IllegalArgumentException("Feil ved oppsett av test data - $it") }

fun søknadinnhold(
    uførevedtak: Uførevedtak = Uførevedtak(true),
    personopplysninger: Personopplysninger = Personopplysninger(fnr),
    flyktningsstatus: Flyktningsstatus = Flyktningsstatus(
        registrertFlyktning = false,
    ),
    boforhold: Boforhold = boforhold(),
    utenlandsopphold: Utenlandsopphold = utenlandsopphold(),
    oppholdstillatelse: Oppholdstillatelse = oppholdstillatelse(),
    inntektOgPensjon: InntektOgPensjon = inntektOgPensjon(),
    formue: Formue = formue(),
    forNav: ForNav = forNavDigitalSøknad(),
    ektefelle: Ektefelle = ektefelle(),
) = SøknadsinnholdUføre.tryCreate(
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
).getOrHandle { throw IllegalArgumentException("Feil ved oppsett av test data - $it") }
