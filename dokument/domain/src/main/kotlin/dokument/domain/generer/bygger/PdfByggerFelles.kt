package dokument.domain.generer.bygger

import dokument.domain.generer.PersonaliaPdfInnhold
import no.nav.brev.brevbaker.markup.dsl.ContentBuilder
import no.nav.brev.brevbaker.markup.dsl.TableBuilder
import no.nav.brev.brevbaker.markup.dsl.cell
import no.nav.brev.brevbaker.markup.dsl.column
import no.nav.brev.brevbaker.markup.dsl.header
import no.nav.brev.brevbaker.markup.dsl.item
import no.nav.brev.brevbaker.markup.dsl.itemList
import no.nav.brev.brevbaker.markup.dsl.paragraph
import no.nav.brev.brevbaker.markup.dsl.row
import no.nav.brev.brevbaker.markup.dsl.table
import no.nav.brev.brevbaker.markup.dsl.title2
import no.nav.brev.brevbaker.markup.outline.Block.Table.ColumnAlignment
import no.nav.brev.brevbaker.markup.outline.Text.FontType
import no.nav.su.se.bakover.common.SU_SE_BAKOVER_CONSUMER_ID
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import satser.domain.Satsgrunn
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val personaliaDatoFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

const val AVSENDERENHET = "Nav Familie- og pensjonsytelser Ålesund"
const val STANDARDTITTEL = "Supplerende stønad"

class UgyldigInput(melding: String) : IllegalArgumentException(melding)

fun GenerellData.ellers(annen: GenerellData): GenerellData = if (finnes) this else annen

fun telefonnummer(sakstype: GenerellData): String = if (sakstype.er("ALDER")) "55 55 33 34" else "55 55 33 33"
fun telefonnummer(sakstype: Sakstype): String = if (sakstype == Sakstype.ALDER) "55 55 33 34" else "55 55 33 33"

fun personaliaNavn(data: GenerellData): String =
    "${personaliaFelt(data, "fornavn").tekst} ${personaliaFelt(data, "etternavn").tekst}".trim()

fun personaliaNavn(personalia: PersonaliaPdfInnhold): String =
    "${personalia.fornavn} ${personalia.etternavn}".trim()

fun parsePersonaliaDato(data: GenerellData): LocalDate? =
    personaliaFelt(data, "dato").tilStreng()?.let {
        parsePersonaliaDato(it)
    }

fun parsePersonaliaDato(personaliaDato: String): LocalDate =
    // TODO kan på sikt bare bli LocalDate i PersonaliaPdfInnhold når su-pdfgen ikke lenger er i bruk
    runCatching { LocalDate.parse(personaliaDato.trim(), personaliaDatoFormat) }.getOrElse { _ ->
        throw UgyldigInput("personalia.dato må være på formatet dd.MM.yyyy, var '$personaliaDato'")
    }

fun personaliaFelt(data: GenerellData, felt: String): GenerellData =
    data["personalia"][felt].takeIf { it.finnes } ?: data[felt]

fun saksnummer(data: GenerellData): String? =
    personaliaFelt(data, "saksnummer").tilStreng()?.also {
        if (it.length > 20) throw UgyldigInput("saksnummer kan ikke være lengre enn 20 tegn")
    }

fun Innhold.fritekst(verdi: GenerellData) {
    val tekst = verdi.tekst.replace("\r\n", "\n")
    tekst.split(Regex("\n[ \t]*\n")).forEach { avsnitt ->
        paragraph { linjer(avsnitt) }
    }
}

fun Innhold.fritekst(verdi: String) {
    val tekst = verdi.replace("\r\n", "\n")
    tekst.split(Regex("\n[ \t]*\n")).forEach { avsnitt ->
        paragraph { linjer(avsnitt) }
    }
}

fun ContentBuilder.linjer(tekst: String, fontType: FontType = FontType.PLAIN) {
    tekst.split("\n").forEachIndexed { indeks, linje ->
        if (indeks > 0) newLine()
        text(linje, fontType)
    }
}

fun Innhold.fødselsnummerAvsnitt(personalia: GenerellData) {
    paragraph("Fødselsnummer ${personalia["fødselsnummer"].tekst} (oppgi fødselsnummer ved henvendelser til oss)")
}

fun Innhold.fødselsnummerAvsnitt(personalia: PersonaliaPdfInnhold) {
    paragraph("Fødselsnummer ${personalia.fødselsnummer} (oppgi fødselsnummer ved henvendelser til oss)")
}

fun Innhold.hilsen(saksbehandler: String?, attestant: String? = null, visPostadresse: Boolean = true) {
    val navn = when {
        saksbehandler == null -> null
        saksbehandler == SU_SE_BAKOVER_CONSUMER_ID -> attestant ?: ""
        attestant != null && saksbehandler != attestant -> "$saksbehandler og $attestant"
        else -> saksbehandler
    }
    paragraph {
        text("Med vennlig hilsen")
        newLine()
        text(if (navn == null) AVSENDERENHET else "$navn hos $AVSENDERENHET".trim())
    }
    if (visPostadresse) postadresse()
}

fun Innhold.postadresse() {
    paragraph {
        linjer("Postadresse:\nNav Skanning\nPostboks 1402\n0109 OSLO\nNorge / Norway")
    }
}

fun Innhold.fellesKlageinfo(sakstype: Sakstype) {
    title2("Du har rett til å klage")
    paragraph("Du kan klage innen 6 uker fra den datoen du mottok vedtaket. Du finner skjema og informasjon på www.nav.no/klage.")
    rettTilInnsyn()
    harDuSpørsmål(sakstype)
}

private fun Innhold.rettTilInnsyn() {
    title2("Du har rett til innsyn")
    paragraph("På www.nav.no/minside kan du se dokumentene i saken din.")
}

fun Innhold.harDuSpørsmål(sakstype: Sakstype) {
    title2("Har du spørsmål?")
    paragraph {
        text("Du finner nyttig informasjon på ")
        text(
            if (sakstype == Sakstype.ALDER) "www.nav.no/supplerende-stonad-over-67." else "www.nav.no/supplerende-stonad-flyktninger.",
        )
    }
    paragraph("Du kan også kontakte oss på ${telefonnummer(sakstype)}.")
}

fun Innhold.punktliste(vararg punkter: String) = punktliste(punkter.map { listOf(it) })

fun Innhold.punktliste(punkter: List<List<String>>) {
    if (punkter.isEmpty()) return
    itemList {
        punkter.forEach { avsnitt ->
            item {
                avsnitt.forEachIndexed { indeks, tekst ->
                    if (indeks > 0) newLine()
                    text(tekst)
                }
            }
        }
    }
}

fun Innhold.beregning(
    sakstype: Sakstype,
    satsoversikt: SatsoversiktBrev?,
    harFradrag: Boolean,
    forventetInntektStørreEnn0: Boolean,
    beregningsperioder: List<BeregningsperiodeBrev>,
) {
    title2("Slik beregner vi din supplerende stønad:")
    satsoversikt?.let { satsoversikt(it) }
    if (harFradrag) paragraph("Vi har redusert stønaden din med inntekt og andre fradrag.")
    if (sakstype == Sakstype.UFØRE && forventetInntektStørreEnn0) {
        paragraph(
            "Vi bruker alltid det høyeste beløpet av arbeidsinntekten din og din forventede inntekt etter uførhet når vi " +
                "beregner stønaden din. Du ser i beregningen hvilket beløp vi bruker.",
        )
    }
    beregningsperioder.forEach { periode ->
        beregningsperiode(periode)
        if (periode.fradrag.eps.harFradragMedSumSomErLavereEnnFribeløp) {
            paragraph(
                "Inntekten til ektefelle/samboer er under fribeløpet på ${periode.epsFribeløp.kroner(true)} kr per måned og er " +
                    "derfor ikke med i regnestykket for denne perioden.",
            )
        }
    }
    paragraph("Stønaden reguleres automatisk i samsvar med grunnbeløpet fra 1. mai hvert år. Økningen etterbetales vanligvis i juni.")
}

private fun Innhold.beregningsperiode(periode: BeregningsperiodeBrev) {
    val brukerFradrag = periode.fradrag.bruker
    val epsFradrag = periode.fradrag.eps.fradrag
    val visEpsFribeløp = epsFradrag.isNotEmpty() && periode.epsFribeløp > 0
    table {
        header {
            column("${periode.periode.fraOgMed.storForbokstav()} til ${periode.periode.tilOgMed}")
            column("", ColumnAlignment.RIGHT)
        }
        row {
            cell("Supplerende stønad, ${periode.sats} sats")
            cell("${periode.satsbeløpPerMåned.kroner()} kr")
        }
        brukerFradrag.forEach { fradrag ->
            row {
                cell(if (fradrag.type == "Sosialstønad") "Fradrag for utbetalt sosialstønad" else fradrag.type)
                cell("-${fradrag.beløp.kroner()} kr")
            }
            utenlandskInntekt(fradrag)
        }
        if (epsFradrag.isNotEmpty()) {
            epsFradrag.forEach { fradrag ->
                row {
                    cell("${fradrag.type} (Ektefelle/samboer)")
                    cell("- ${fradrag.beløp.kroner()} kr")
                }
                utenlandskInntekt(fradrag)
            }
            if (visEpsFribeløp) {
                row {
                    cell("Fribeløp (Ektefelle/samboer)")
                    cell("${periode.epsFribeløp.kroner()} kr")
                }
            }
        }
        row {
            cell("Sum per måned", FontType.BOLD)
            cell("${periode.ytelsePerMåned.kroner()} kr", FontType.BOLD)
        }
    }
}

fun Innhold.satsoversikt(satsoversikt: SatsoversiktBrev) {
    paragraph("Maksimal ytelse per måned (før eventuelle fratrekk) bestemmes av sivilstatus og bosituasjon. Følgende vurderinger er lagt til grunn:")
    val perioder = satsoversikt.perioder
    if (perioder.isEmpty()) {
        paragraph("Periode Vurdering Sats Beløp")
        return
    }
    table {
        header {
            column("Periode")
            column("Vurdering")
            column("Sats")
            column("Beløp")
        }
        perioder.forEach { periode ->
            val satsgrunn = satsGrunner[periode.satsGrunn]
            row {
                cell("${periode.fraOgMed}-${periode.tilOgMed}")
                cell(if (!satsgrunn.isNullOrEmpty()) satsgrunn else "")
                cell(periode.sats.storForbokstav())
                cell("${periode.satsBeløp.kroner()} kr")
            }
        }
    }
}

private fun TableBuilder<ContentBuilder>.utenlandskInntekt(fradrag: MånedsfradragForBrev) {
    val utenlandsk = fradrag.utenlandskInntekt ?: return
    row {
        cell {
            text("Beløp i utenlandsk valuta: ${utenlandsk.valuta} ${utenlandsk.beløpIUtenlandskValuta.kroner()} kr")
            newLine()
            text("Kurs ved beregning: ${utenlandsk.kurs.kroner()} kr")
        }
        cell("")
    }
}

private val satsGrunner = mapOf(
    Satsgrunn.ENSLIG.name to "Deler ikke bolig med voksne barn eller andre voksne",
    Satsgrunn.DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN.name to "Deler bolig med voksne barn eller andre voksne",
    Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67.name to "Ektefelle eller samboer er under 67 år og ikke ufør flyktning",
    Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_67_ELLER_ELDRE.name to "Ektefelle eller samboer er 67 år eller eldre",
    Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67_UFØR_FLYKTNING.name to "Ektefelle eller samboer under 67 år og ufør flyktning",
)
