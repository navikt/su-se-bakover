package no.nav.su.se.bakover.domain.sak

import dokument.domain.Distribusjonstype
import dokument.domain.Dokument
import dokument.domain.distribuering.Distribueringsadresse
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.time.Clock
import java.util.UUID

data class JournalførOgSendOpplastetPdfSomBrevCommand(
    val sakId: UUID,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val journaltittel: String,
    val pdf: PdfA,
    val distribueringsadresse: Distribueringsadresse?,
    val distribusjonstype: Distribusjonstype,
) {
    fun opprettDokumentMedMetadata(clock: Clock): Dokument.MedMetadata {
        // TODO - enkel løsning, men det er ikke ønskelig at domenet skal forholde seg til json
        val generertDokumentJson = createJson()

        return when (this.distribusjonstype) {
            Distribusjonstype.VEDTAK -> Dokument.MedMetadata.Vedtak(
                utenMetadata = Dokument.UtenMetadata.Vedtak(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(clock),
                    tittel = journaltittel,
                    generertDokument = pdf,
                    generertDokumentJson = generertDokumentJson,
                ),
                metadata = Dokument.Metadata(sakId = this.sakId),
                distribueringsadresse = this.distribueringsadresse,
            )

            Distribusjonstype.VIKTIG -> Dokument.MedMetadata.Informasjon.Viktig(
                utenMetadata = Dokument.UtenMetadata.Informasjon.Viktig(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(clock),
                    tittel = journaltittel,
                    generertDokument = pdf,
                    generertDokumentJson = generertDokumentJson,
                ),
                metadata = Dokument.Metadata(sakId = this.sakId),
                distribueringsadresse = this.distribueringsadresse,
            )

            Distribusjonstype.ANNET -> Dokument.MedMetadata.Informasjon.Annet(
                utenMetadata = Dokument.UtenMetadata.Informasjon.Annet(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(clock),
                    tittel = journaltittel,
                    generertDokument = pdf,
                    generertDokumentJson = generertDokumentJson,
                ),
                metadata = Dokument.Metadata(sakId = this.sakId),
                distribueringsadresse = this.distribueringsadresse,
            )
        }
    }

    private fun createJson(): String = JournalførOgSendDokumentJson(
        saksbehandler = saksbehandler,
        journaltittel = journaltittel,
        distribueringsadresse = distribueringsadresse,
        distribusjonstype = distribusjonstype,
    ).let { serialize(it) }
}

private data class JournalførOgSendDokumentJson(
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val journaltittel: String,
    val distribueringsadresse: Distribueringsadresse?,
    val distribusjonstype: Distribusjonstype,
) {
    /**
     * kommentar som skal bli inkludert i json for generertDokumentJson
     */
    val kommentar: String = "Pdf er lastet opp manuelt. Innholdet i brevet er ukjent"
}
