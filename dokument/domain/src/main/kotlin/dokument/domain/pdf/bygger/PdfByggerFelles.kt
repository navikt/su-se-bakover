package dokument.domain.pdf.bygger

import dokument.domain.pdf.PersonaliaPdfInnhold
import no.nav.brev.brevbaker.markup.dsl.ContentBuilder
import no.nav.brev.brevbaker.markup.dsl.paragraph
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

fun parsePersonaliaDato(personaliaDato: String): LocalDate? =
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
