package no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata

import dokument.domain.generer.bygger.maler.SøknadPdfInnhold
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.Boforhold
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.Ektefelle
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.EktefellePartnerSamboer
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.Flyktningsstatus
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.FnrWrapper
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.ForNav
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.Formue
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.HarSøktAlderspensjon
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.InnlagtPåInstitusjon
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.InntektOgPensjon
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.Kjøretøy
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.OppgittAdresse
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.Oppholdstillatelse
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.OppholdstillatelseAlder
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.PensjonsOrdningBeløp
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadInnhold
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadsinnholdAlder
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadsinnholdUføre
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.TrygdeytelseIUtlandet
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.Uførevedtak
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.Utenlandsopphold
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.UtenlandsoppholdPeriode
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import person.domain.Person
import java.time.LocalDate
import java.util.UUID

/** Fritekst for statsborgerskap i andre land. Påkrevd av [Oppholdstillatelse.tryCreate] når statsborgerskapAndreLand er true. */
private const val STATSBORGERSKAP_ANDRE_LAND = "Sverige"

private val forNav = ForNav.Papirsøknad(
    mottaksdatoForSøknad = LocalDate.of(2020, 12, 13),
    grunnForPapirinnsending = ForNav.Papirsøknad.GrunnForPapirinnsending.VergeHarSøktPåVegneAvBruker,
    annenGrunn = "",
)

private val personopplysninger = FnrWrapper(Fnr("12345678910"))

private val boforhold = Boforhold.tryCreate(
    borOgOppholderSegINorge = true,
    delerBolig = true,
    delerBoligMed = Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER,
    ektefellePartnerSamboer = EktefellePartnerSamboer(
        erUførFlyktning = false,
        fnr = Fnr("01010101010"),
    ),
    innlagtPåInstitusjon = InnlagtPåInstitusjon(
        datoForInnleggelse = LocalDate.of(2020, 1, 1),
        datoForUtskrivelse = LocalDate.of(2020, 1, 31),
        fortsattInnlagt = false,
    ),
    oppgittAdresse = OppgittAdresse.BorPåAdresse(
        adresselinje = "Oslogata 12",
        postnummer = "0050",
        poststed = "OSLO",
        bruksenhet = null,
    ),
).getOrFail()

private val oppholdstillatelse = Oppholdstillatelse.tryCreate(
    erNorskStatsborger = false,
    harOppholdstillatelse = false,
    oppholdstillatelseType = Oppholdstillatelse.OppholdstillatelseType.MIDLERTIDIG,
    statsborgerskapAndreLand = true,
    statsborgerskapAndreLandFritekst = STATSBORGERSKAP_ANDRE_LAND,
).getOrFail()

// kontonummer i json-en finnes ikke i Formue.
private val formue = Formue.tryCreate(
    eierBolig = false,
    borIBolig = false,
    verdiPåBolig = 600000,
    boligBrukesTil = "Mine barn bor der",
    depositumsBeløp = null,
    verdiPåEiendom = 3,
    eiendomBrukesTil = null,
    kjøretøy = listOf(
        Kjøretøy(verdiPåKjøretøy = 25000, kjøretøyDeEier = "99001122"),
        Kjøretøy(verdiPåKjøretøy = 50000, kjøretøyDeEier = "00112233"),
    ),
    innskuddsBeløp = 10,
    verdipapirBeløp = 20,
    skylderNoenMegPengerBeløp = 30,
    kontanterBeløp = 40,
).getOrFail()

private val trygdeytelserIUtlandet = listOf(
    TrygdeytelseIUtlandet(beløp = 200, type = "trygd", valuta = "En valuta"),
    TrygdeytelseIUtlandet(beløp = 500, type = "Annen trygd", valuta = "En annen valuta"),
)

// sosialstønadBeløp i json-en finnes ikke i InntektOgPensjon.
private val inntektOgPensjon = InntektOgPensjon(
    forventetInntekt = 2500,
    andreYtelserINav = "sosialstønad",
    andreYtelserINavBeløp = 33,
    søktAndreYtelserIkkeBehandletBegrunnelse = "uføre",
    trygdeytelseIUtlandet = trygdeytelserIUtlandet,
    pensjon = listOf(
        PensjonsOrdningBeløp(ordning = "KLP", beløp = 2000.0),
        PensjonsOrdningBeløp(ordning = "SPK", beløp = 5000.0),
    ),
)

// kontonummer og sosialstønadBeløp i json-en finnes ikke i Formue og InntektOgPensjon.
private val ektefelle = Ektefelle(
    formue = Formue.tryCreate(
        eierBolig = false,
        borIBolig = false,
        verdiPåBolig = 0,
        boligBrukesTil = "",
        depositumsBeløp = 0,
        verdiPåEiendom = 0,
        eiendomBrukesTil = "",
        kjøretøy = emptyList(),
        innskuddsBeløp = 0,
        verdipapirBeløp = 0,
        skylderNoenMegPengerBeløp = 100,
        kontanterBeløp = 50,
    ).getOrFail(),
    inntektOgPensjon = InntektOgPensjon(
        forventetInntekt = null,
        andreYtelserINav = null,
        andreYtelserINavBeløp = null,
        søktAndreYtelserIkkeBehandletBegrunnelse = null,
        trygdeytelseIUtlandet = trygdeytelserIUtlandet,
        pensjon = emptyList(),
    ),
)

private val utenlandsopphold = Utenlandsopphold(
    registrertePerioder = listOf(
        UtenlandsoppholdPeriode(utreisedato = LocalDate.of(2020, 1, 1), innreisedato = LocalDate.of(2020, 1, 31)),
        UtenlandsoppholdPeriode(utreisedato = LocalDate.of(2020, 2, 1), innreisedato = LocalDate.of(2020, 2, 5)),
    ),
    planlagtePerioder = listOf(
        UtenlandsoppholdPeriode(utreisedato = LocalDate.of(2020, 7, 1), innreisedato = LocalDate.of(2020, 7, 31)),
    ),
)

private val søknadInnholdUføre = SøknadsinnholdUføre.tryCreate(
    uførevedtak = Uførevedtak(harUførevedtak = true),
    flyktningsstatus = Flyktningsstatus(registrertFlyktning = true),
    personopplysninger = personopplysninger,
    boforhold = boforhold,
    utenlandsopphold = utenlandsopphold,
    oppholdstillatelse = oppholdstillatelse,
    inntektOgPensjon = inntektOgPensjon,
    formue = formue,
    forNav = forNav,
    ektefelle = ektefelle,
).getOrFail()

private val søknadInnholdAlder = SøknadsinnholdAlder.tryCreate(
    harSøktAlderspensjon = HarSøktAlderspensjon(harSøktAlderspensjon = true),
    oppholdstillatelseAlder = OppholdstillatelseAlder(eøsborger = false, familiegjenforening = true),
    personopplysninger = personopplysninger,
    boforhold = boforhold,
    utenlandsopphold = utenlandsopphold,
    oppholdstillatelse = oppholdstillatelse,
    inntektOgPensjon = inntektOgPensjon,
    formue = formue,
    forNav = forNav,
    ektefelle = ektefelle,
).getOrFail()

private fun soknadPdf(søknadInnhold: SøknadInnhold) = SøknadPdfInnhold(
    sakstype = søknadInnhold.type(),
    saksnummer = Saksnummer(2021),
    søknadsId = UUID.fromString("98465998-f749-4db4-b9ae-fd19a8f027fa"),
    navn = Person.Navn(fornavn = "Leeroy", mellomnavn = "Johnas", etternavn = "Jenkins"),
    clock = fixedClock,
    dagensDatoOgTidspunkt = "01.01.2021 12:00",
    søknadOpprettet = "01.01.2021",
    søknadInnhold = søknadInnhold,
)

val soknadUførePdf = soknadPdf(søknadInnholdUføre)

val soknadAlderPdf = soknadPdf(søknadInnholdAlder)
