package no.nav.su.se.bakover.domain.sak

import dokument.domain.Distribusjonstype
import dokument.domain.Dokument
import dokument.domain.distribuering.Distribueringsadresse
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import java.util.UUID

class JournalførOgSendOpplastetPdfSomBrevCommandTest {

    @Test
    fun `oppretter dokument med metadata`() {
        val vedtaksCommand = nyCommand(
            distribueringsadresse = Distribueringsadresse(
                adresselinje1 = "Gondor",
                adresselinje2 = "calls",
                adresselinje3 = "for",
                postnummer = "aid",
                poststed = "And Rohan will answer",
            ),
        )
        val viktigCommand = nyCommand(distribusjonstype = Distribusjonstype.VIKTIG)
        val annetCommand = nyCommand(distribusjonstype = Distribusjonstype.ANNET)

        vedtaksCommand.opprettDokumentMedMetadata(fixedClock).let {
            it.shouldBeEqualToIgnoringFields(
                Dokument.MedMetadata.Vedtak(
                    utenMetadata = Dokument.UtenMetadata.Vedtak(
                        id = it.id,
                        opprettet = fixedTidspunkt,
                        tittel = vedtaksCommand.journaltittel,
                        generertDokument = vedtaksCommand.pdf,
                        //language=json
                        generertDokumentJson = """{"saksbehandler":"saksbehandler","journaltittel":"tittel på journalposten","distribueringsadresse":{"adresselinje1":"Gondor","adresselinje2":"calls","adresselinje3":"for","postnummer":"aid","poststed":"And Rohan will answer"},"distribusjonstype":"VEDTAK","kommentar":"Pdf er lastet opp manuelt. Innholdet i brevet er ukjent"}""",
                    ),
                    metadata = Dokument.Metadata(sakId = vedtaksCommand.sakId),
                    distribueringsadresse = vedtaksCommand.distribueringsadresse,
                ),
                Dokument::id,
                Dokument::generertDokument,
            )
        }

        viktigCommand.opprettDokumentMedMetadata(fixedClock).shouldBeEqualToIgnoringFields(
            Dokument.MedMetadata.Informasjon.Viktig(
                utenMetadata = Dokument.UtenMetadata.Informasjon.Viktig(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    tittel = viktigCommand.journaltittel,
                    generertDokument = viktigCommand.pdf,
                    //language=json
                    generertDokumentJson = """{"saksbehandler":"saksbehandler","journaltittel":"tittel på journalposten","distribueringsadresse":null,"distribusjonstype":"VIKTIG","kommentar":"Pdf er lastet opp manuelt. Innholdet i brevet er ukjent"}""",
                ),
                metadata = Dokument.Metadata(sakId = viktigCommand.sakId),
                distribueringsadresse = viktigCommand.distribueringsadresse,
            ),
            Dokument::id,
        )

        annetCommand.opprettDokumentMedMetadata(fixedClock).shouldBeEqualToIgnoringFields(
            Dokument.MedMetadata.Informasjon.Annet(
                utenMetadata = Dokument.UtenMetadata.Informasjon.Annet(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    tittel = annetCommand.journaltittel,
                    generertDokument = annetCommand.pdf,
                    //language=json
                    generertDokumentJson = """{"saksbehandler":"saksbehandler","journaltittel":"tittel på journalposten","distribueringsadresse":null,"distribusjonstype":"ANNET","kommentar":"Pdf er lastet opp manuelt. Innholdet i brevet er ukjent"}""",
                ),
                metadata = Dokument.Metadata(sakId = annetCommand.sakId),
                distribueringsadresse = annetCommand.distribueringsadresse,
            ),
            Dokument::id,
        )
    }

    private fun nyCommand(
        sakId: UUID = UUID.randomUUID(),
        saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
        journaltittel: String = "tittel på journalposten",
        pdf: PdfA = PdfA("pdf".toByteArray()),
        distribueringsadresse: Distribueringsadresse? = null,
        distribusjonstype: Distribusjonstype = Distribusjonstype.VEDTAK,
    ) = JournalførOgSendOpplastetPdfSomBrevCommand(
        sakId = sakId,
        saksbehandler = saksbehandler,
        journaltittel = journaltittel,
        pdf = pdf,
        distribueringsadresse = distribueringsadresse,
        distribusjonstype = distribusjonstype,
    )
}
