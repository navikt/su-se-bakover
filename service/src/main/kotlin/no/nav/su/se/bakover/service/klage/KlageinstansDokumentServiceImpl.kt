package no.nav.su.se.bakover.service.klage

import arrow.core.Either
import arrow.core.raise.either
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

class KlageinstansDokumentServiceImpl(
    private val klageRepo: KlageRepo,
    private val journalpostClient: QueryJournalpostClient,
) : KlageinstansDokumentService {
    private val log = LoggerFactory.getLogger(this::class.java)

    override suspend fun hentDokumenterForSak(sakId: UUID): Either<KlageinstansDokumentFeil, List<KlageinstansDokument>> {
        val klager = klageRepo.hentKlager(sakId)
        val journalpostIder = klager
            .flatMap { it.klageinstanshendelserOrEmpty() }
            .flatMap { it.journalposterOrEmpty() }
            .distinct()

        return either {
            if (journalpostIder.isEmpty()) return@either emptyList()

            val seen = mutableSetOf<String>()

            journalpostIder.flatMap { journalpostId ->
                val journalpost = journalpostClient.hentJournalpostMedDokumenter(journalpostId)
                    .mapLeft { it.tilFeil() }
                    .bind()

                journalpost.dokumenter.mapNotNull dokument@{ dokument ->
                    val valgtVariant = velgVariant(dokument.varianter) ?: return@dokument null
                    val key = "${journalpostId}_${dokument.dokumentInfoId}_${valgtVariant.variantFormat}"
                    if (!seen.add(key)) return@dokument null

                    val innhold = journalpostClient.hentDokument(
                        journalpostId = journalpostId,
                        dokumentInfoId = dokument.dokumentInfoId,
                        variantFormat = valgtVariant.variantFormat,
                    ).mapLeft { it.tilFeil() }
                        .bind()

                    KlageinstansDokument(
                        journalpostId = journalpost.journalpostId,
                        journalpostTittel = journalpost.tittel,
                        dokumentInfoId = dokument.dokumentInfoId,
                        dokumentTittel = dokument.tittel,
                        brevkode = dokument.brevkode,
                        dokumentstatus = dokument.dokumentstatus,
                        variantFormat = valgtVariant.variantFormat,
                        bytes = innhold.bytes,
                    )
                }
            }
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

    private fun velgVariant(varianter: List<DokumentVariant>): DokumentVariant? {
        if (varianter.isEmpty()) return null

        val ikkeSladdet = varianter.filterNot {
            it.variantFormat.equals("SLADDET", ignoreCase = true)
        }

        val pdfVarianter = ikkeSladdet.filter {
            val filtype = it.filtype?.uppercase()
            filtype == "PDF" || filtype == "PDFA"
        }

        ikkeSladdet
            .mapNotNull { it.filtype?.uppercase() }
            .filterNot { it == "PDF" || it == "PDFA" }
            .distinct()
            .forEach { filtype ->
                log.error("Fant filtype som ikke støttes: {}", filtype)
            }

        if (pdfVarianter.isEmpty()) return null

        return pdfVarianter.firstOrNull { it.variantFormat.equals("ARKIV", ignoreCase = true) }
            ?: pdfVarianter.firstOrNull { it.variantFormat.equals("ORIGINAL", ignoreCase = true) }
            ?: pdfVarianter.firstOrNull()
    }

    private fun KunneIkkeHenteJournalpost.tilFeil(): KlageinstansDokumentFeil {
        return KlageinstansDokumentFeil.KunneIkkeHenteJournalpost(this)
    }

    private fun KunneIkkeHenteDokument.tilFeil(): KlageinstansDokumentFeil {
        return KlageinstansDokumentFeil.KunneIkkeHenteDokument(this)
    }
}
