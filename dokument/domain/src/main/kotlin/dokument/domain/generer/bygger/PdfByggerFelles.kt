package dokument.domain.generer.bygger

import dokument.domain.generer.PersonaliaPdfInnhold
import no.nav.brev.brevbaker.markup.dsl.ContentBuilder
import no.nav.brev.brevbaker.markup.dsl.item
import no.nav.brev.brevbaker.markup.dsl.itemList
import no.nav.brev.brevbaker.markup.dsl.paragraph
import no.nav.brev.brevbaker.markup.dsl.title2
import no.nav.brev.brevbaker.markup.outline.Text.FontType
import no.nav.su.se.bakover.common.SU_SE_BAKOVER_CONSUMER_ID
import no.nav.su.se.bakover.common.domain.sak.Sakstype
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
