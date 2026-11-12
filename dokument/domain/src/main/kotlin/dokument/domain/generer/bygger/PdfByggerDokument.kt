package dokument.domain.generer.bygger

import dokument.domain.generer.PersonaliaPdfInnhold
import no.nav.brev.brevbaker.document.dsl.document
import no.nav.brev.brevbaker.document.dsl.documentPDFRequest
import no.nav.brev.brevbaker.document.dsl.documentSaksnummer
import no.nav.brev.brevbaker.markup.Attachment
import no.nav.brev.brevbaker.markup.Markup
import no.nav.brev.brevbaker.markup.dsl.letterMarkup
import no.nav.brev.brevbaker.markup.dsl.saksinformasjon
import no.nav.brev.brevbaker.markup.dsl.signatur
import no.nav.brev.brevbaker.markup.dsl.title1
import no.nav.brev.brevbaker.pdfbygger.api.letterPDFRequest
import no.nav.su.se.bakover.common.SU_SE_BAKOVER_CONSUMER_ID

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

fun signerende(
    saksbehandler: String?,
    attestant: String?,
): Pair<String?, String?> = when {
    saksbehandler == null -> null to null
    saksbehandler == SU_SE_BAKOVER_CONSUMER_ID -> attestant to null
    attestant != null && saksbehandler != attestant -> saksbehandler to attestant
    else -> saksbehandler to null
}
