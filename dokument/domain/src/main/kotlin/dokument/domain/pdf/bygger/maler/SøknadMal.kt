package dokument.domain.pdf.bygger.maler

import dokument.domain.pdf.bygger.GenerellData
import dokument.domain.pdf.bygger.Innhold
import dokument.domain.pdf.bygger.Kontekst
import dokument.domain.pdf.bygger.dokument
import dokument.domain.pdf.bygger.isoTilDato
import dokument.domain.pdf.bygger.kroner
import no.nav.brev.brevbaker.markup.dsl.cell
import no.nav.brev.brevbaker.markup.dsl.column
import no.nav.brev.brevbaker.markup.dsl.header
import no.nav.brev.brevbaker.markup.dsl.paragraph
import no.nav.brev.brevbaker.markup.dsl.row
import no.nav.brev.brevbaker.markup.dsl.table

val soknad = { data: GenerellData ->
    val rot = Kontekst.rot(data)
    val innhold = data["søknadInnhold"]
    dokument(data, null, medPersonalia = false) {
        forNav(rot.med(rot["søknadInnhold.forNav"]))
        personopplysninger(
            rot.medHash(
                "personopplysninger" to rot["søknadInnhold.personopplysninger"],
                "boforhold" to rot["søknadInnhold.boforhold"],
            ),
        )
        if (innhold["uførevedtak"].sann) {
            val k = rot.med(innhold["uførevedtak"])
            seksjon("Uførevedtak") { jaNei("Har du fått svar på søknaden din om uføretrygd?", k["harUførevedtak"]) }
        }
        if (innhold["flyktningsstatus"].sann) {
            val med = rot.med(innhold["flyktningsstatus"])
            flyktningstatus(
                med.medHash(
                    "flyktningsstatus" to med["søknadInnhold.flyktningsstatus"],
                    "oppholdstillatelse" to med["søknadInnhold.oppholdstillatelse"],
                ),
            )
        }
        if (innhold["harSøktAlderspensjon"].sann) {
            val k = rot.med(innhold["harSøktAlderspensjon"])
            seksjon("Alderspensjon") {
                jaNei("Har du søkt om 100% alderspensjon og fått svar på søknaden?", k["harSøktAlderspensjon"])
            }
        }
        if (innhold["oppholdstillatelseAlder"].sann) {
            val med = rot.med(innhold["oppholdstillatelseAlder"])
            oppholdstillatelseAlder(
                med.medHash(
                    "oppholdstillatelseAlder" to med["søknadInnhold.oppholdstillatelseAlder"],
                    "oppholdstillatelse" to med["søknadInnhold.oppholdstillatelse"],
                ),
            )
        }
        boforhold(rot.med(rot["søknadInnhold.boforhold"]))
        formue(rot.med(rot["søknadInnhold.formue"]), søkersFormue)
        inntektOgPensjon(rot.med(rot["søknadInnhold.inntektOgPensjon"]), søkersInntekt)
        if (innhold["boforhold"].sann) {
            val med = rot.med(innhold["boforhold"])
            if (innhold["boforhold.delerBoligMed"].er("EKTEMAKE_SAMBOER")) {
                formue(med.med(med["søknadInnhold.ektefelle.formue"]), epsFormue)
                inntektOgPensjon(med.med(med["søknadInnhold.ektefelle.inntektOgPensjon"]), epsInntekt)
            }
        }
        utenlandsopphold(rot.med(rot["søknadInnhold.utenlandsopphold"]))
        paragraph(
            "Signatur, ${rot["navn.fornavn"].tekst} ${rot["navn.mellomnavn"].tekst} ${rot["navn.etternavn"].tekst}, " +
                rot["dagensDatoOgTidspunkt"].tekst,
        )
        paragraph("--------------------------------------------------")
    }
}

internal class Rader {
    val rader = mutableListOf<Pair<String, String>>()

    fun rad(spørsmål: String, svar: String) {
        rader += spørsmål to svar
    }

    fun jaNei(spørsmål: String, verdi: GenerellData) = rad(spørsmål, if (verdi.sann) "Ja" else "Nei")
}

internal fun Innhold.seksjon(tittel: String, bygg: Rader.() -> Unit) {
    val rader = Rader().apply(bygg).rader
    table {
        header {
            column(tittel)
            column("")
        }
        rader.forEach { (spørsmål, svar) ->
            row {
                cell(spørsmål)
                cell(svar)
            }
        }
    }
}

internal fun GenerellData.kr(): String = "${kroner()} kr"

internal fun Kontekst.hver(sti: String, blokk: (Kontekst) -> Unit) {
    this[sti].elementer.forEach { blokk(med(it)) }
}

private fun Innhold.forNav(k: Kontekst) = seksjon("For Nav") {
    rad("Saksnummer:", k["saksnummer"].tekst)
    rad("Søknads-id:", k["søknadsId"].tekst)
    rad("Søknadsdato:", k["søknadOpprettet"].tekst)
    if (k["type"].er("DigitalSøknad")) {
        rad("Har søker møtt personlig:", if (k["harFullmektigEllerVerge"].sann) "Nei" else "Ja")
        if (k["harFullmektigEllerVerge"].sann) {
            rad("Har søker fullmektig eller verge?", k["harFullmektigEllerVerge"].tekst)
        }
    }
    if (k["type"].er("Papirsøknad")) {
        rad("Søknad innsendt på papir:", "Ja")
        rad("Mottatt dato:", k["mottaksdatoForSøknad"].tekst)
        val grunn = k["grunnForPapirinnsending"]
        rad(
            "Grunn for papirinnsending:",
            listOfNotNull(
                "Verge har søkt på vegne av bruker".takeIf { grunn.er("VergeHarSøktPåVegneAvBruker") },
                "Midlertidig unntak fra oppmøteplikt".takeIf { grunn.er("MidlertidigUnntakFraOppmøteplikt") },
                "Annet".takeIf { grunn.er("Annet") },
            ).joinToString(" "),
        )
        if (grunn.er("Annet")) rad("", k["annenGrunn"].tekst)
    }
}

private fun Innhold.personopplysninger(k: Kontekst) = seksjon("Personopplysninger") {
    rad("Fødselsnummer:", k["personopplysninger.fnr"].tekst)
    rad("Navn:", "${k["navn.fornavn"].tekst} ${k["navn.mellomnavn"].tekst} ${k["navn.etternavn"].tekst}")
    val adresse = "boforhold.oppgittAdresse"
    rad(
        "Addresse:",
        if (k["$adresse.adresselinje"].sann) {
            val linje = if (k["$adresse.bruksenhet"].sann) {
                "${k["$adresse.adresselinje"].tekst} ${k["$adresse.bruksenhet"].tekst},"
            } else {
                "${k["$adresse.adresselinje"].tekst},"
            }
            "$linje ${k["$adresse.postnummer"].tekst} ${k["$adresse.poststed"].tekst}"
        } else {
            listOfNotNull(
                "Har ikke fast bosted".takeIf { k["$adresse.grunn"].er("HAR_IKKE_FAST_BOSTED") },
                "Bor på en annen adresse".takeIf { k["$adresse.grunn"].er("BOR_PÅ_ANNEN_ADRESSE") },
            ).joinToString(" ")
        },
    )
}

private fun Rader.oppholdstillatelseType(k: Kontekst) {
    rad(
        "Er oppholdstillatelsen din permanent eller midlertidig?",
        if (k["oppholdstillatelse.oppholdstillatelseType"].er("MIDLERTIDIG")) "Midlertidig" else "Permanent",
    )
}

private fun Rader.statsborgerskapAndreLand(k: Kontekst, spørsmål: String) {
    jaNei(spørsmål, k["oppholdstillatelse.statsborgerskapAndreLand"])
    if (k["oppholdstillatelse.statsborgerskapAndreLand"].sann) {
        rad("Hvilke land har du statsborgerskap i?", k["oppholdstillatelse.statsborgerskapAndreLandFritekst"].tekst)
    }
}

private fun Innhold.flyktningstatus(k: Kontekst) = seksjon("Flyktningstatus") {
    jaNei("Er du registrert som flyktning?", k["flyktningsstatus.registrertFlyktning"])
    jaNei("Er du norsk statsborger?", k["oppholdstillatelse.erNorskStatsborger"])
    if (!k["oppholdstillatelse.erNorskStatsborger"].sann) {
        jaNei("Har du oppholdstillatelse i Norge?", k["oppholdstillatelse.harOppholdstillatelse"])
        if (k["oppholdstillatelse.harOppholdstillatelse"].sann) oppholdstillatelseType(k)
    }
    statsborgerskapAndreLand(k, "Har du statsborgerskap i andre land enn Norge?")
}

private fun Innhold.oppholdstillatelseAlder(k: Kontekst) = seksjon("Oppholdstillatelse") {
    jaNei(
        "Kom du til Norge på grunn av familiegjenforening med barn, barnebarn, nevø eller niese, og fikk " +
            "oppholdstillatelse med krav til underhold?",
        k["oppholdstillatelseAlder.familiegjenforening"],
    )
    jaNei("Er du norsk statsborger eller statsborger i et annet nordisk land?", k["oppholdstillatelse.erNorskStatsborger"])
    if (!k["oppholdstillatelse.erNorskStatsborger"].sann) {
        jaNei("Er du EØS-borger eller familiemedlem til en EØS-borger?", k["oppholdstillatelseAlder.eøsborger"])
        jaNei("Har du oppholdstillatelse i Norge?", k["oppholdstillatelse.harOppholdstillatelse"])
        if (k["oppholdstillatelse.harOppholdstillatelse"].sann) oppholdstillatelseType(k)
    }
    statsborgerskapAndreLand(k, "Har du statsborgerskap i andre land?")
}

private fun Innhold.boforhold(k: Kontekst) = seksjon("Bo og opphold i Norge") {
    jaNei("Bor og oppholder du deg i Norge?", k["borOgOppholderSegINorge"])
    jaNei("Har du vært innlagt på institusjon de siste tre månedene?", k["innlagtPåInstitusjon.datoForInnleggelse"])
    if (k["innlagtPåInstitusjon.datoForInnleggelse"].sann) {
        rad("Dato for innleggelse:", k["innlagtPåInstitusjon.datoForInnleggelse"].isoTilDato())
        rad(
            "Dato for utskrivelse:",
            if (k["innlagtPåInstitusjon.datoForUtskrivelse"].sann) {
                k["innlagtPåInstitusjon.datoForUtskrivelse"].isoTilDato()
            } else {
                "Fortsatt innlagt"
            },
        )
    }
    jaNei("Deler du bolig med noen over 18 år?", k["delerBolig"])
    val delerBoligMed = k["delerBoligMed"]
    if (k["delerBolig"].sann) {
        rad(
            "Hvem deler du bolig med?",
            listOfNotNull(
                "Ektefelle eller samboer".takeIf { delerBoligMed.er("EKTEMAKE_SAMBOER") },
                "Voksne barn".takeIf { delerBoligMed.er("VOKSNE_BARN") },
                "Annen voksen".takeIf { delerBoligMed.er("ANNEN_VOKSEN") },
            ).joinToString(" "),
        )
    }
    if (delerBoligMed.er("EKTEMAKE_SAMBOER")) {
        rad("Fødselsnummeret til ektefelle eller samboer", k["ektefellePartnerSamboer.fnr"].tekst)
        jaNei("Er ektefelle eller samboer ufør flyktning?", k["ektefellePartnerSamboer.erUførFlyktning"])
    }
}

private class Formuetekster(
    val tittel: String,
    val eierBolig: String,
    val borIBolig: String,
    val eierEiendom: String,
    val eiendomKr: Boolean,
    val depositum: String,
    val depositumBeløp: String,
    val kjøretøy: String,
    val konto: String,
    val verdipapir: String,
    val skylder: String,
    val skylderBeløp: String,
    val kontanter: String,
    val kontanterBeløp: String,
)

private val søkersFormue = Formuetekster(
    tittel = "Din formue",
    eierBolig = "Eier du en bolig?",
    borIBolig = "Bor du i boligen?",
    eierEiendom = "Eier du andre eiendommer i Norge eller i andre land?",
    eiendomKr = false,
    depositum = "Har du en depositumskonto som leietaker?",
    depositumBeløp = "Beløp på depositumskontoen",
    kjøretøy = "Eier du bil, campingvogn eller andre kjøretøy?",
    konto = "Har du penger på konto?",
    verdipapir = "Har du aksjer, aksjefond eller verdipapir?",
    skylder = "Skylder noen deg mer enn 1000 kr?",
    skylderBeløp = "Hvor mye skylder de deg?",
    kontanter = "Har du mer enn 1000 kr i kontanter?",
    kontanterBeløp = "Hvor mye penger har du?",
)

private val epsFormue = Formuetekster(
    tittel = "Ektefelle/samboers formue",
    eierBolig = "Eier ektefelle/samboer en bolig?",
    borIBolig = "Bor ektefelle/samboer i boligen?",
    eierEiendom = "Eier ektefelle/samboer andre eiendommer i Norge eller i andre land?",
    eiendomKr = true,
    depositum = "Har ektefelle/samboer depositumskonto som leietaker?",
    depositumBeløp = "Beløpet på depositumskontoen",
    kjøretøy = "Eier ektefelle/samboer bil, campingvogn eller andre kjøretøy?",
    konto = "Har ektefelle/samboer penger på konto?",
    verdipapir = "Har ektefelle/samboer aksjer, aksjefond eller verdipapir?",
    skylder = "Skylder noen ektefelle/samboer mer enn 1000 kr?",
    skylderBeløp = "Hvor mye skylder de ektefelle/samboer?",
    kontanter = "Har ektefelle/samboer mer enn 1000 kroner i kontanter?",
    kontanterBeløp = "Hvor mye penger har ektefelle/samboer?",
)

private fun Innhold.formue(k: Kontekst, t: Formuetekster) = seksjon(t.tittel) {
    jaNei(t.eierBolig, k["eierBolig"])
    if (k["eierBolig"].sann) jaNei(t.borIBolig, k["borIBolig"])
    if (k["boligBrukesTil"].sann) {
        rad("Boligens formueverdi", k["verdiPåBolig"].kr())
        rad("Hva brukes boligen til?", k["boligBrukesTil"].tekst)
    }
    jaNei(t.eierEiendom, k["verdiPåEiendom"])
    if (k["verdiPåEiendom"].sann) {
        rad("Eiendommenes samlede verdi", if (t.eiendomKr) k["verdiPåEiendom"].kr() else k["verdiPåEiendom"].kroner())
        rad("Hva brukes eiendommene til?", k["eiendomBrukesTil"].tekst)
    }
    if (!k["eierBolig"].sann) {
        jaNei(t.depositum, k["depositumsBeløp"])
        if (k["depositumsBeløp"].sann) rad(t.depositumBeløp, k["depositumsBeløp"].kr())
    }
    jaNei(t.kjøretøy, k["kjøretøy"])
    if (k["kjøretøy"].sann) {
        k.hver("kjøretøy") { kjøretøy ->
            rad("Registreringsnummer:", kjøretøy["kjøretøyDeEier"].tekst)
            rad("Verdien av kjøretøyet:", kjøretøy["verdiPåKjøretøy"].kr())
        }
    }
    jaNei(t.konto, k["innskuddsBeløp"])
    if (k["innskuddsBeløp"].sann) rad("Hvor mye penger er det på konto?", k["innskuddsBeløp"].kr())
    jaNei(t.verdipapir, k["verdipapirBeløp"])
    if (k["verdipapirBeløp"].sann) rad("Hvor mye penger er dette verdt?", k["verdipapirBeløp"].kr())
    jaNei(t.skylder, k["skylderNoenMegPengerBeløp"])
    if (k["skylderNoenMegPengerBeløp"].sann) rad(t.skylderBeløp, k["skylderNoenMegPengerBeløp"].kr())
    jaNei(t.kontanter, k["kontanterBeløp"])
    if (k["kontanterBeløp"].sann) rad(t.kontanterBeløp, k["kontanterBeløp"].kr())
}

private class Inntektstekster(
    val tittel: String,
    val forventer: String,
    val forventetBeløp: String,
    val andreYtelser: String,
    val andreYtelserBeløp: String,
    val søkt: String,
    val utland: String,
    val utlandBeløp: String,
    val pensjon: String,
    val pensjonFra: String,
    val pensjonBeløp: String,
    val utenlandsKr: Boolean,
)

private val søkersInntekt = Inntektstekster(
    tittel = "Din inntekt",
    forventer = "Forventer du å ha arbeidsinntekt fremover?",
    forventetBeløp = "Hvor mye regner du med å tjene i måneden?",
    andreYtelser = "Har du andre ytelser i NAV?",
    andreYtelserBeløp = "Hvor mye penger får du utbetalt i måneden?",
    søkt = "Har du søkt om trygdeytelser som du ikke har fått svar på?",
    utland = "Har du trygdeytelser fra andre land?",
    utlandBeløp = "Hvor mye får du i lokal valuta i måneden?",
    pensjon = "Får du tjenestepensjon eller pensjon som ikke er fra NAV?",
    pensjonFra = "Hvem får du pengene fra?",
    pensjonBeløp = "Hvor mye penger får du i måneden?",
    utenlandsKr = true,
)

private val epsInntekt = Inntektstekster(
    tittel = "Ektefelle/samboers inntekt",
    forventer = "Forventer ektefelle/samboer å ha arbeidsinntekt fremover?",
    forventetBeløp = "Hvor mye regner ektefelle/samboer med å tjene i måneden?",
    andreYtelser = "Har ektefelle/samboer andre ytelser fra NAV?",
    andreYtelserBeløp = "Hvor mye penger får ektefelle/samboer utbetalt i måneden?",
    søkt = "Har ektefelle/samboer søkt om trygdeytelser som de ikke har fått svar på?",
    utland = "Har ektfelle/samboer trygdeytelser fra andre land?",
    utlandBeløp = "Hvor mye får ektefelle/samboer i lokal valuta i måneden?",
    pensjon = "Får ektefelle/samboer tjenestepensjon eller pensjon som ikke er fra NAV?",
    pensjonFra = "Hvem får ektefelle/samboer pengene fra?",
    pensjonBeløp = "Hvor mye penger får ektefelle/samboer i måneden?",
    utenlandsKr = false,
)

private fun Innhold.inntektOgPensjon(k: Kontekst, t: Inntektstekster) = seksjon(t.tittel) {
    val beløp = { v: GenerellData -> if (t.utenlandsKr) v.kr() else v.kroner() }
    jaNei(t.forventer, k["forventetInntekt"])
    if (k["forventetInntekt"].sann) rad(t.forventetBeløp, k["forventetInntekt"].kr())
    jaNei(t.andreYtelser, k["andreYtelserINav"])
    if (k["andreYtelserINav"].sann) {
        rad("Hvilke ytelser?", k["andreYtelserINav"].tekst)
        rad(t.andreYtelserBeløp, k["andreYtelserINavBeløp"].kr())
    }
    jaNei(t.søkt, k["søktAndreYtelserIkkeBehandletBegrunnelse"])
    if (k["søktAndreYtelserIkkeBehandletBegrunnelse"].sann) {
        rad("Hvilke?", k["søktAndreYtelserIkkeBehandletBegrunnelse"].tekst)
    }
    jaNei(t.utland, k["trygdeytelseIUtlandet"])
    if (k["trygdeytelseIUtlandet"].sann) {
        k.hver("trygdeytelseIUtlandet") { ytelse ->
            rad(t.utlandBeløp, beløp(ytelse["beløp"]))
            rad("Valuta", ytelse["valuta"].tekst)
            rad("Type ytelse", ytelse["type"].tekst)
        }
    }
    jaNei(t.pensjon, k["pensjon"])
    if (k["pensjon"].sann) {
        k.hver("pensjon") { pensjon ->
            rad(t.pensjonFra, pensjon["ordning"].tekst)
            rad(t.pensjonBeløp, beløp(pensjon["beløp"]))
        }
    }
}

private fun Innhold.utenlandsopphold(k: Kontekst) = seksjon("Reise til utlandet") {
    fun perioder(sti: String) {
        if (k[sti].sann) {
            k.hver(sti) { periode ->
                rad("Utreisedato", periode["utreisedato"].isoTilDato())
                rad("Innreisedato", periode["innreisedato"].isoTilDato())
            }
        }
    }
    jaNei("Har du reist til utlandet de siste 90 dagene?", k["registrertePerioder"])
    perioder("registrertePerioder")
    jaNei("Har du planer om å reise til utlandet i de neste 12 månedene?", k["planlagtePerioder"])
    perioder("planlagtePerioder")
}
