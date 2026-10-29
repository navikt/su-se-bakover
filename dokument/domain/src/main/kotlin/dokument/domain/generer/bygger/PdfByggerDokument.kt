package dokument.domain.generer.bygger

import dokument.domain.generer.PersonaliaPdfInnhold
import no.nav.brev.brevbaker.document.dsl.document
import no.nav.brev.brevbaker.document.dsl.documentPDFRequest
import no.nav.brev.brevbaker.document.dsl.documentSaksnummer
import no.nav.brev.brevbaker.markup.Markup

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

fun dokument(
    tittel: String?,
    personalia: PersonaliaPdfInnhold?,
    innhold: Innhold.() -> Unit,
): PdfBestilling.Dokument {
    val gyldigTittel = tittel?.trim()?.takeIf { it.isNotBlank() }
    return PdfBestilling.Dokument(
        documentPDFRequest(
            document = document(
                tittel = gyldigTittel ?: STANDARDTITTEL,
                visTittel = gyldigTittel != null,
                visLogo = personalia != null,
                gjelderNavn = personalia?.let { personaliaNavn(personalia) },
                saksnummer = personalia?.let { documentSaksnummer(it.saksnummer.toString()) },
                dokumentDato = personalia?.let { parsePersonaliaDato(it.dato) },
                build = innhold,
            ),
            spraak = Markup.Spraak.BOKMAL,
        ),
    )
}
