package dokument.domain.generer.bygger.maler

import com.fasterxml.jackson.annotation.JsonInclude
import dokument.domain.generer.DokumentMal
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.AVSENDERENHET
import dokument.domain.generer.bygger.Beregningsperiode
import dokument.domain.generer.bygger.Innhold
import dokument.domain.generer.bygger.PdfBestilling
import dokument.domain.generer.bygger.Satsoversikt
import dokument.domain.generer.bygger.fritekst
import dokument.domain.generer.bygger.fødselsnummerAvsnitt
import dokument.domain.generer.bygger.parsePersonaliaDato
import dokument.domain.generer.bygger.personaliaNavn
import dokument.domain.generer.bygger.postadresse
import dokument.domain.generer.bygger.punktliste
import dokument.domain.generer.bygger.saksnummer
import dokument.domain.generer.bygger.telefonnummer
import no.nav.brev.brevbaker.markup.Attachment
import no.nav.brev.brevbaker.markup.Markup
import no.nav.brev.brevbaker.markup.dsl.attachment
import no.nav.brev.brevbaker.markup.dsl.letterMarkup
import no.nav.brev.brevbaker.markup.dsl.paragraph
import no.nav.brev.brevbaker.markup.dsl.saksinformasjon
import no.nav.brev.brevbaker.markup.dsl.signatur
import no.nav.brev.brevbaker.markup.dsl.title1
import no.nav.brev.brevbaker.markup.dsl.title2
import no.nav.brev.brevbaker.pdfbygger.api.letterPDFRequest
import no.nav.su.se.bakover.common.SU_SE_BAKOVER_CONSUMER_ID
import no.nav.su.se.bakover.common.domain.sak.Sakstype

// TODO Ta i bruk i mapping fra Command..
data class InnvilgetSøknadsbehandlingPdfInnhold(
    val personalia: PersonaliaPdfInnhold,
    val fradato: String,
    val tildato: String,
    val søkedato: String,
    val forventetInntektStørreEnn0: Boolean,
    val harEktefelle: Boolean,
    val beregningsperioder: List<Beregningsperiode>,
    val saksbehandlerNavn: String,
    val attestantNavn: String,
    val fritekst: String,
    val satsoversikt: Satsoversikt,
    override val sakstype: Sakstype,
) : PdfInnhold {
    override val dokumentMal = DokumentMal.InnvilgetVedtak

    @Suppress("unused")
    @JsonInclude
    val harFradrag: Boolean = beregningsperioder.harFradrag()
}
fun List<Beregningsperiode>.harFradrag(): Boolean {
    return this.any {
        // TODO jah: Rest av Avkorting. Brukes kun fra brev. Det skal egentlig ikke finnes nye avkortingsfradrag i nye behandlinger. Undersøk om vi kan fjerne denne filtreringen.
        it.fradrag.bruker.filterNot { fradrag -> fradrag.type == "Avkorting på grunn av tidligere utenlandsopphold" }
            .isNotEmpty() ||
            it.fradrag.eps.fradrag.isNotEmpty()
    }
}

val vedtakInnvilgelse = { tittel: String, pdfInnhold: InnvilgetSøknadsbehandlingPdfInnhold ->
    brev(
        tittel = "Du får supplerende stønad",
        pdfInnhold.personalia,
        saksbehandler = pdfInnhold.saksbehandlerNavn,
        attestant = pdfInnhold.attestantNavn,
        vedlegg = listOf(ekstraInnvilgelsesinformasjon(pdfInnhold.sakstype)),
    ) {
        fødselsnummerAvsnitt(pdfInnhold.personalia)
        paragraph(
            "Du får supplerende stønad fra ${pdfInnhold.fradato} til og med ${pdfInnhold.tildato} fordi du er " +
                if (pdfInnhold.sakstype == Sakstype.ALDER) "over 67 år og har kort botid i Norge." else "ufør flyktning.",
        )
        paragraph("Vedtaket er gjort etter lov om supplerende stønad til personer med kort botid i Norge, §§ 3, 5, 6 og 7.")
        if (pdfInnhold.fritekst.isNotEmpty()) fritekst(pdfInnhold.fritekst)
        // beregning(data) TODO
        title2("Viktig informasjon")
        paragraph(
            "Du kan ikke oppholde deg utenfor Norge i mer enn 90 dager til sammen i denne stønadsperioden. " +
                "Opphold i utlandet må dokumenteres med norske stempel i pass eller boardingpass. " +
                "Du må melde fra til Nav om planlagt ut- og innreisedato før du reiser til utlandet.",
        )
        paragraph("Bryter du disse reglene kan stønaden din opphøre. Du kan også få krav om å betale tilbake penger.")
        paragraph("Du kan lese mer om dine rettigheter og plikter nedenfor.")
        title2("Du må melde fra om endringer")
        paragraph(
            "Dersom det skjer endringer som kan ha betydning for stønaden du får utbetalt, må du straks melde fra til Nav. " +
                "Du finner mer informasjon på www.nav.no/endringer.",
        )
        title2("Sjekk utbetalingene dine")
        paragraph(
            "Sjekk alle utbetalingene du har mottatt på www.nav.no/minside. Her kan du også endre kontonummeret ditt.",
        )
        // fellesInfo(data) TODO etter merge
        postadresse()
    }
}

private fun ekstraInnvilgelsesinformasjon(sakstype: Sakstype): Attachment {
    return attachment {
        title1(
            "Viktig informasjon om supplerende stønad til deg som er " +
                if (sakstype == Sakstype.ALDER) "over 67 år og har kort botid i Norge" else "ufør flyktning",
        )
        outline {
            title2("Du må møte på ditt lokale Nav-kontor")
            paragraph(
                "Stønadsperioden for supplerende stønad gis som hovedregel for 12 måneder. " +
                    "Du må møte på Nav-kontoret hver gang du skal søke om ny stønadsperiode.",
            )
            title2("Du blir innkalt til to møter")
            paragraph(
                "Vi gjennomfører to kontrollsamtaler i løpet av de 12 månedene du får stønaden fordi vi vil være sikre på at " +
                    "du oppholder deg i Norge. Vi må også være sikre på at du fortsatt har rett til stønaden.",
            )
            paragraph(
                "Vi kaller deg inn til denne samtalen i god tid før du skal møte. Hvis du ikke møter uten å gi oss beskjed, " +
                    "stopper vi stønaden. Hvis du ikke kan møte av helsemessige grunner, må du dokumentere dette.",
            )
            title2("Du må også melde fra om endringer som kan ha betydning for stønaden din")
            paragraph("Dette må melde fra om hvis du:")
            punktliste(
                "Skal være i en institusjon, for eksempel sykehus, rehabilitering, eller fengsel",
                "Begynner å dele bolig med en annen voksen person eller flytter fra en annen voksen",
                "Endrer sivilstatus, for eksempel ekteskap, separasjon eller skilsmisse",
                "Endrer adresse",
                "Får pensjon eller andre ytelser fra utlandet",
                "Får endret inntekt som for eksempel arbeidsinntekt, pensjon eller andre ytelser. Dette gjelder både " +
                    "utenlandske og norske inntekter og ytelser",
                "Har formue over halvparten av folketrygdens grunnbeløp. Du kan lese mer om grunnbeløpet på www.nav.no",
            )
            paragraph(
                "Formue er eiendommer, kontanter, innskudd i norske eller utenlandske banker, omsettelige aksjer, " +
                    "obligasjoner, andeler i verdipapirfond, andre omsettelige verdipapirer og utestående fordringer. Har du " +
                    "gjeld, er ikke dette med i vurderingen vår. Egen primærbolig og en bil (to biler hvis du har " +
                    "samboer/ektefelle) regnes ikke som formue.",
            )
            paragraph(
                "Du må opplyse om all formue og inntekt i Norge og utlandet. Hvis du ikke opplyser om formuen eller inntekten " +
                    "din, kan du få penger du ikke har rett til. Da stopper vi stønaden din og vi krever tilbake penger du ikke " +
                    "skulle fått.",
            )
            title2("Hvis du har, eller får, ektefelle eller samboer")
            paragraph(
                "Du må opplyse om all formue og inntekt i Norge og utlandet også for ektefellen eller samboeren din. Formuen " +
                    "og inntekten til ektefellen eller samboeren din har betydning for om du har rett til stønad, eventuelt " +
                    "størrelsen på stønaden din.",
            )
            title2("Du må melde fra om endringer for å unngå feilutbetaling")
            paragraph(
                "Du må melde fra om alle endringer som kan ha betydning for retten din til stønad eller hvor mye du får i " +
                    "stønad. Hvis du ikke forteller om endringene, stopper vi utbetalingen av stønaden din. Du kan også få krav " +
                    "om tilbakebetaling.",
            )
            paragraph(
                "Hvis du ikke opplyser om formuen eller inntekten din, kan du få penger du ikke har rett til. Da stopper vi " +
                    "stønaden din og vi krever tilbake penger du ikke skulle fått.",
            )
            title2("Generell informasjon")
            paragraph(
                "Du får denne stønaden uavhengig av statsborgerskapet ditt. Blir du norsk statsborger i løpet av stønadstiden, " +
                    "er vilkårene de samme som før du ble norsk statsborger.",
            )
            title2("Frikort")
            paragraph(
                "Alle som får supplerende stønad slipper å betale egenandel for medisiner, næringsmidler og medisinsk " +
                    "forbruksmateriell på blå resept. Apotek/bandasjist kan sjekke at du har rett til egenandelsfritaket. Du må " +
                    "betale egenandeler for andre helsetjenester under frikortordningen, for eksempel legebesøk, fysioterapi " +
                    "etc.",
            )
            title2("Skattekort")
            paragraph(
                "Hvis du ikke har skattekort, må du ta kontakt med Skatteetaten. Har du skattekort og ønsker å endre det, kan " +
                    "du kontakte Skatteetaten.",
            )
            if (sakstype == Sakstype.UFØRE) {
                title2("Honnørkort")
                paragraph(
                    "Du har rett til honnørrabatt på offentlig kommunikasjon når du reiser i Norge. Hvis du ikke allerede har " +
                        "honnørkortet, vil du få dette kortet i posten.",
                )
            }
            title2("Kontakt oss")
            paragraph(
                "Du finner mer informasjon på www.nav.no. Her kan du sende melding eller spørsmål til oss. " +
                    "Hvis du trenger tolkebistand så kan du kontakte oss på telefon ${telefonnummer(sakstype)} " +
                    "for å bestille timeavtale.",
            )
        }
    }
}

// TODO felles eller bare bruk dokument?
fun brev(
    tittel: String,
    personalia: PersonaliaPdfInnhold,
    saksbehandler: String?,
    attestant: String?,
    vedlegg: List<Attachment>,
    innhold: Innhold.() -> Unit,
): PdfBestilling.Brev {
    val (saksbehandlerNavn, attestantNavn) = signerende(saksbehandler, attestant)
    return PdfBestilling.Brev(
        letterPDFRequest(
            letterMarkup = letterMarkup(
                saksinformasjon = saksinformasjon(
                    gjelderNavn = personaliaNavn(personalia),
                    gjelderPersonidentifikator = personalia.fødselsnummer,
                    saksnummer = personalia.saksnummer.toString(),
                    dokumentDato = parsePersonaliaDato(personalia.dato),
                ),
                signatur = signatur(
                    navAvsenderEnhet = AVSENDERENHET,
                    saksbehandlerNavn = saksbehandlerNavn,
                    attesterendeSaksbehandlerNavn = attestantNavn,
                ),
            ) {
                title1(tittel)
                outline(innhold)
            },
            spraak = Markup.Spraak.BOKMAL,
            brevtype = Markup.Brevtype.VEDTAKSBREV,
            attachments = vedlegg,
        ),
    )
}

// TODO vil disse noen gang være null i bruk av denne?
// TODO og må vi ta høyde for tom streng?
fun signerende(
    saksbehandler: String?,
    attestant: String?,
): Pair<String?, String?> = when {
    saksbehandler == null -> null to null
    saksbehandler == SU_SE_BAKOVER_CONSUMER_ID -> attestant to null
    attestant != null && saksbehandler != attestant -> saksbehandler to attestant
    else -> saksbehandler to null
}
