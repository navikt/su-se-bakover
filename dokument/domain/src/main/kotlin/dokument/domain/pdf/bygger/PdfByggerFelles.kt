package dokument.domain.pdf.bygger

import no.nav.brev.brevbaker.document.dsl.document
import no.nav.brev.brevbaker.document.dsl.documentPDFRequest
import no.nav.brev.brevbaker.document.dsl.documentSaksnummer
import no.nav.brev.brevbaker.markup.Markup
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val personaliaDato: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

const val AVSENDERENHET = "Nav Familie- og pensjonsytelser Ålesund"
const val STANDARDTITTEL = "Supplerende stønad"

class UgyldigInput(melding: String) : IllegalArgumentException(melding)

fun PdfData.ellers(annen: PdfData): PdfData = if (finnes) this else annen

fun telefonnummer(sakstype: PdfData): String = if (sakstype.er("ALDER")) "55 55 33 34" else "55 55 33 33"

fun personaliaFelt(data: PdfData, felt: String): PdfData =
    data["personalia"][felt].takeIf { it.finnes } ?: data[felt]

fun personaliaNavn(data: PdfData): String =
    "${personaliaFelt(data, "fornavn").tekst} ${personaliaFelt(data, "etternavn").tekst}".trim()

fun parsePersonaliaDato(data: PdfData): LocalDate? =
    personaliaFelt(data, "dato").tilStreng()?.let {
        runCatching { LocalDate.parse(it.trim(), personaliaDato) }.getOrElse { _ ->
            throw UgyldigInput("personalia.dato må være på formatet dd.MM.yyyy, var '$it'")
        }
    }

private fun saksnummer(data: PdfData): String? =
    personaliaFelt(data, "saksnummer").tilStreng()?.also {
        if (it.length > 20) throw UgyldigInput("saksnummer kan ikke være lengre enn 20 tegn")
    }

fun dokument(
    data: PdfData,
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

val tomDokument = { data: PdfData ->
    dokument(data, null, medPersonalia = false) {}
}
