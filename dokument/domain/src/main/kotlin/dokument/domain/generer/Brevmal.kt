package dokument.domain.generer

import arrow.core.Either
import arrow.core.raise.either
import dokument.domain.Brevtype
import dokument.domain.Dokument
import dokument.domain.GenererDokumentCommand
import dokument.domain.KunneIkkeLageDokument
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.time.LocalDate
import java.util.UUID

/**
 * Data som hentes én gang per brev og deles av alle maler.
 *
 * @property saksbehandlerNavn `"-"` når kommandoen ikke har saksbehandler.
 * @property attestantNavn `null` når kommandoen ikke har attestant. Malen bestemmer hva som vises da.
 * @property idag datoen brevet lages.
 */
data class Felles(
    val sakstype: Sakstype,
    val personalia: PersonaliaPdfInnhold,
    val saksbehandlerNavn: String,
    val attestantNavn: String?,
    val idag: LocalDate,
)

enum class Dokumenttype { VEDTAK, INFORMASJON_VIKTIG, INFORMASJON_ANNET }

class Brevmal<F>(
    val dokumenttype: Dokumenttype,
    val brevtype: Brevtype,
    val lagInnhold: (Felles, F) -> PdfInnhold,
) {
    fun tilDokument(
        pdf: PdfA,
        innhold: PdfInnhold,
        id: UUID,
        opprettet: Tidspunkt,
    ): Dokument.UtenMetadata {
        val tittel = innhold.dokumentMal.tittel
        val json = innhold.toJson()
        return when (dokumenttype) {
            Dokumenttype.VEDTAK -> Dokument.UtenMetadata.Vedtak(
                id = id,
                opprettet = opprettet,
                tittel = tittel,
                generertDokument = pdf,
                generertDokumentJson = json,
                brevtype = brevtype,
            )

            Dokumenttype.INFORMASJON_VIKTIG -> Dokument.UtenMetadata.Informasjon.Viktig(
                id = id,
                opprettet = opprettet,
                tittel = tittel,
                generertDokument = pdf,
                generertDokumentJson = json,
                brevtype = brevtype,
            )

            Dokumenttype.INFORMASJON_ANNET -> Dokument.UtenMetadata.Informasjon.Annet(
                id = id,
                opprettet = opprettet,
                tittel = tittel,
                generertDokument = pdf,
                generertDokumentJson = json,
                brevtype = brevtype,
            )
        }
    }
}

/**
 * En kommando om å lage et brev. Kommandoen bærer dataene malen trenger, og peker på malen med [mal].
 *
 * Hver kommandoklasse implementerer [lagInnhold] som `mal.lagInnhold(felles, this)`. Det er den eneste
 * koblingen mellom kommando og mal; ingen sentral `when` trenger å kjenne kommandotypene.
 */
interface BrevmalCommand : GenererDokumentCommand {
    val saksbehandler: NavIdentBruker?
    val attestant: NavIdentBruker?
    val mal: Brevmal<*>

    fun lagInnhold(felles: Felles): PdfInnhold
}

/**
 * Hele kjeden fra kommando til dokument. Ren funksjon: effektene (personoppslag, navneoppslag og
 * PDF-generering) sendes inn som parametre, og id og tidspunkt kommer utenfra.
 */
fun lagBrev(
    command: BrevmalCommand,
    hentFelles: (BrevmalCommand) -> Either<KunneIkkeLageDokument, Felles>,
    lagPdf: (PdfInnhold) -> Either<KunneIkkeLageDokument, PdfA>,
    id: UUID,
    opprettet: Tidspunkt,
): Either<KunneIkkeLageDokument, Dokument.UtenMetadata> = either {
    val felles = hentFelles(command).bind()
    val innhold = command.lagInnhold(felles)
    val pdf = lagPdf(innhold).bind()
    command.mal.tilDokument(pdf, innhold, id, opprettet)
}
