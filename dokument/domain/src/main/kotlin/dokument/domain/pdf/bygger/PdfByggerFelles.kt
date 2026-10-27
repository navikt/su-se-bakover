package dokument.domain.pdf.bygger

import no.nav.brev.brevbaker.document.dsl.document
import no.nav.brev.brevbaker.document.dsl.documentPDFRequest
import no.nav.brev.brevbaker.document.dsl.documentSaksnummer
import no.nav.brev.brevbaker.markup.Markup
import no.nav.brev.brevbaker.markup.dsl.ContentBuilder
import no.nav.brev.brevbaker.markup.dsl.paragraph
import no.nav.brev.brevbaker.markup.outline.Text.FontType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val personaliaDato: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

const val AVSENDERENHET = "Nav Familie- og pensjonsytelser Ålesund"
const val STANDARDTITTEL = "Supplerende stønad"

class UgyldigInput(melding: String) : IllegalArgumentException(melding)

fun GenerellData.ellers(annen: GenerellData): GenerellData = if (finnes) this else annen

fun telefonnummer(sakstype: GenerellData): String = if (sakstype.er("ALDER")) "55 55 33 34" else "55 55 33 33"

fun personaliaFelt(data: GenerellData, felt: String): GenerellData =
    data["personalia"][felt].takeIf { it.finnes } ?: data[felt]

fun personaliaNavn(data: GenerellData): String =
    "${personaliaFelt(data, "fornavn").tekst} ${personaliaFelt(data, "etternavn").tekst}".trim()

fun parsePersonaliaDato(data: GenerellData): LocalDate? =
    personaliaFelt(data, "dato").tilStreng()?.let {
        runCatching { LocalDate.parse(it.trim(), personaliaDato) }.getOrElse { _ ->
            throw UgyldigInput("personalia.dato må være på formatet dd.MM.yyyy, var '$it'")
        }
    }

private fun saksnummer(data: GenerellData): String? =
    personaliaFelt(data, "saksnummer").tilStreng()?.also {
        if (it.length > 20) throw UgyldigInput("saksnummer kan ikke være lengre enn 20 tegn")
    }

fun Innhold.fritekst(verdi: GenerellData) {
    val tekst = verdi.tekst.replace("\r\n", "\n")
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

fun Innhold.hilsen(saksbehandler: GenerellData, attestant: GenerellData = GenerellData.of(null), visPostadresse: Boolean = true) {
    val navn = when {
        !saksbehandler.sann -> null
        saksbehandler.er("systembruker") -> if (attestant.sann) attestant.tekst else ""
        attestant.sann && saksbehandler.tekst != attestant.tekst -> "${saksbehandler.tekst} og ${attestant.tekst}"
        else -> saksbehandler.tekst
    }
    paragraph {
        text("Med vennlig hilsen")
        newLine()
        text(if (navn == null) AVSENDERENHET else "$navn hos $AVSENDERENHET".trim())
    }
    if (visPostadresse) postadresse()
}

fun Innhold.standardHilsen(data: GenerellData, medAttestant: Boolean = false) {
    hilsen(
        data["saksbehandlerNavn"].ellers(data["saksbehandler"]),
        if (medAttestant) data["attestantNavn"].ellers(data["attestant"]) else data["attestant"],
        visPostadresse = !data["ikkeVisPostadresse"].sann,
    )
}

fun Innhold.postadresse() {
    paragraph {
        linjer("Postadresse:\nNav Skanning\nPostboks 1402\n0109 OSLO\nNorge / Norway")
    }
}

fun dokument(
    data: GenerellData,
    tittel: String?,
    medPersonalia: Boolean = true,
    innhold: Innhold.() -> Unit,
): PdfBestilling.Dokument {
    val personalia = data["personalia"]
    val visPersonalia = medPersonalia && personalia.finnes
    val gyldigTittel = tittel?.trim()?.takeIf { it.isNotBlank() }
    return PdfBestilling.Dokument(
        documentPDFRequest(
            document = document(
                tittel = gyldigTittel ?: STANDARDTITTEL,
                visTittel = gyldigTittel != null,
                visLogo = visPersonalia,
                gjelderNavn = if (visPersonalia) personaliaNavn(data) else null,
                saksnummer = if (visPersonalia) saksnummer(data)?.let { documentSaksnummer(it) } else null,
                dokumentDato = if (visPersonalia) parsePersonaliaDato(data) else null,
                build = innhold,
            ),
            spraak = Markup.Spraak.BOKMAL,
        ),
    )
}
