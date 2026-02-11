package no.nav.su.se.bakover.service.klage

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import dokument.domain.DokumentRepo
import dokument.domain.journalføring.DokumentVariant
import dokument.domain.journalføring.KunneIkkeHenteDokument
import dokument.domain.journalføring.KunneIkkeHenteJournalpost
import dokument.domain.journalføring.QueryJournalpostClient
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.domain.klage.Klageinstanshendelser
import no.nav.su.se.bakover.domain.klage.ProsessertKlageinstanshendelse
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import no.nav.su.se.bakover.domain.klage.VurdertKlageFelter
import org.slf4j.LoggerFactory
import java.util.UUID

class DokumentAdresseServiceImpl(
    private val klageRepo: KlageRepo,
    private val journalpostClient: QueryJournalpostClient,
    private val dokumentRepo: DokumentRepo,
) : DokumentAdresseService {
    private val log = LoggerFactory.getLogger(this::class.java)

    override suspend fun hentKlageDokumenterAdresseForSak(sakId: UUID): Either<AdresseServiceFeil, List<JournalpostMedDokumentPdfOgAdresse>> {
        val klager = klageRepo.hentKlager(sakId)
        log.info("Hentet {} klager fra database for sakId={}", klager.size, sakId)
        val journalpostIder = klager
            .flatMap { it.klageinstanshendelserOrEmpty() }
            .flatMap { it.journalposterOrEmpty() }
            .distinct()
        log.info("Fant {} journalpostIder fra klager for sakId={}", journalpostIder.size, sakId)

        return either {
            if (journalpostIder.isEmpty()) return@either emptyList()

            journalpostIder.flatMap { journalpostId ->
                hentDokumenterForJournalpost(journalpostId).bind()
            }
        }.map { dokumenter ->
            dokumenter.sortedWith(
                compareBy<JournalpostMedDokumentPdfOgAdresse> { it.datoOpprettet == null }
                    .thenBy { it.datoOpprettet },
            )
        }
    }

    override suspend fun hentAdresseForDokumentId(
        dokumentId: UUID,
        journalpostId: JournalpostId,
    ): Either<AdresseServiceFeil, List<JournalpostMedDokumentPdfOgAdresse>> {
        val dokument = dokumentRepo.hentDokument(dokumentId) ?: return AdresseServiceFeil.FantIkkeDokument.left()
        val journalpostIdFraDb = dokument.journalpostId ?: run {
            log.error(
                "JournalpostId finnes ikke i dokument_distribusjon for dokumentId={}. journalpostId={}",
                dokumentId,
                journalpostId,
            )
            return@run AdresseServiceFeil.FantIkkeJournalpostForDokument.left()
        }
        if (journalpostIdFraDb != journalpostId.toString()) {
            log.warn(
                "JournalpostId matcher ikke dokument_distribusjon for dokumentId={}. journalpostId={}, journalpostIdFraDb={}",
                dokumentId,
                journalpostId,
                journalpostIdFraDb,
            )
            return AdresseServiceFeil.JournalpostIkkeKnyttetTilDokument.left()
        }
        return hentDokumenterForJournalpost(journalpostId)
    }

    private suspend fun hentDokumenterForJournalpost(
        journalpostId: JournalpostId,
    ): Either<AdresseServiceFeil, List<JournalpostMedDokumentPdfOgAdresse>> = either {
        val journalpost = journalpostClient.hentJournalpostMedDokumenter(journalpostId)
            .mapLeft { it.tilFeil() }
            .bind()
        log.info(
            "Hentet journalpost med dokumenter fra ekstern tjeneste. journalpostId={}, antallDokumenter={}",
            journalpostId,
            journalpost.dokumenter.size,
        )

        journalpost.dokumenter.mapNotNull dokument@{ dokument ->
            val valgtVariant = velgVariant(
                journalpostId = journalpostId,
                dokumentInfoId = dokument.dokumentInfoId,
                varianter = dokument.varianter,
            ) ?: return@dokument null

            val innhold = journalpostClient.hentDokument(
                journalpostId = journalpostId,
                dokumentInfoId = dokument.dokumentInfoId,
                variantFormat = valgtVariant.variantFormat,
            ).mapLeft { it.tilFeil() }
                .bind()
            log.info(
                "Hentet dokument fra ekstern tjeneste. journalpostId={}, dokumentInfoId={}, variantFormat={}",
                journalpostId,
                dokument.dokumentInfoId,
                valgtVariant.variantFormat,
            )

            JournalpostMedDokumentPdfOgAdresse(
                journalpostId = journalpost.journalpostId,
                journalpostTittel = journalpost.tittel,
                datoOpprettet = journalpost.datoOpprettet,
                utsendingsinfo = journalpost.utsendingsinfo,
                dokumentInfoId = dokument.dokumentInfoId,
                dokumentTittel = dokument.tittel,
                brevkode = dokument.brevkode,
                dokumentstatus = dokument.dokumentstatus,
                variantFormat = valgtVariant.variantFormat,
                dokument = innhold.bytes,
            )
        }
    }

    private fun Klage.klageinstanshendelserOrEmpty(): Klageinstanshendelser {
        return when (this) {
            is VurdertKlageFelter -> this.klageinstanshendelser
            is VilkårsvurdertKlage.Utfylt.TilVurderingFelter -> this.klageinstanshendelser
            is VilkårsvurdertKlage.Bekreftet.TilVurderingFelter -> this.klageinstanshendelser
            else -> Klageinstanshendelser.empty()
        }
    }

    private fun ProsessertKlageinstanshendelse.journalposterOrEmpty(): List<JournalpostId> {
        return when (this) {
            is ProsessertKlageinstanshendelse.OmgjoeringskravbehandlingAvsluttet -> journalpostIDer
            is ProsessertKlageinstanshendelse.GjenopptaksbehandlingAvsluttet -> journalpostIDer
            is ProsessertKlageinstanshendelse.KlagebehandlingAvsluttet -> journalpostIDer
            is ProsessertKlageinstanshendelse.AnkebehandlingAvsluttet -> journalpostIDer
            is ProsessertKlageinstanshendelse.AnkeITrygderettenAvsluttet -> journalpostIDer
            is ProsessertKlageinstanshendelse.AnkebehandlingOpprettet -> emptyList()
            is ProsessertKlageinstanshendelse.AnkeITrygderettenOpprettet -> emptyList()
        }
    }

    private fun velgVariant(
        journalpostId: JournalpostId,
        dokumentInfoId: String,
        varianter: List<DokumentVariant>,
    ): DokumentVariant? {
        if (varianter.isEmpty()) return null

        val ikkeSladdet = varianter.filterNot {
            it.variantFormat.equals("SLADDET", ignoreCase = true)
        }

        val pdfVarianter = ikkeSladdet.filter {
            val filtype = it.filtype?.uppercase()
            filtype == "PDF" || filtype == "PDFA"
        }

        val ikkeStottet = ikkeSladdet
            .mapNotNull { it.filtype?.uppercase() }
            .filterNot { it == "PDF" || it == "PDFA" }
            .distinct()

        if (ikkeStottet.isNotEmpty()) {
            log.warn(
                "Fant filtyper som ikke støttes. journalpostId={}, dokumentInfoId={}, filtyper={}",
                journalpostId,
                dokumentInfoId,
                ikkeStottet.joinToString(","),
            )
        }

        if (pdfVarianter.isEmpty()) return null

        return pdfVarianter.firstOrNull { it.variantFormat.equals("ARKIV", ignoreCase = true) }
            ?: pdfVarianter.firstOrNull { it.variantFormat.equals("ORIGINAL", ignoreCase = true) }
            ?: pdfVarianter.firstOrNull()
    }

    private fun KunneIkkeHenteJournalpost.tilFeil(): AdresseServiceFeil {
        return AdresseServiceFeil.KunneIkkeHenteJournalpost(this)
    }

    private fun KunneIkkeHenteDokument.tilFeil(): AdresseServiceFeil {
        return AdresseServiceFeil.KunneIkkeHenteDokument(this)
    }
}
