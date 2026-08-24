package no.nav.su.se.bakover.service.notat

import arrow.core.getOrElse
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.notat.JournalførVedtaksnotatClient
import no.nav.su.se.bakover.domain.notat.JournalførVedtaksnotatCommand
import no.nav.su.se.bakover.domain.notat.NotatFeil
import no.nav.su.se.bakover.domain.notat.NotatService
import no.nav.su.se.bakover.domain.notat.ReferanseType
import no.nav.su.se.bakover.domain.sak.SakService
import org.slf4j.LoggerFactory
import java.util.UUID

interface VedtaksnotatJournalføringService {
    fun journalførHvisFinnes(
        sakId: UUID,
        referanseId: UUID,
        referanseType: ReferanseType,
    ): JournalpostId?
}

class JournalførVedtaksnotatService(
    private val notatService: NotatService,
    private val sakService: SakService,
    private val journalførVedtaksnotatClient: JournalførVedtaksnotatClient,
) : VedtaksnotatJournalføringService {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun journalførHvisFinnes(
        sakId: UUID,
        referanseId: UUID,
        referanseType: ReferanseType,
    ): JournalpostId? {
        val notatMedVedlegg = notatService.hentNotatMedVedleggForReferanse(
            sakId = sakId,
            referanseId = referanseId,
            referanseType = referanseType,
        ).getOrElse { feil ->
            when (feil) {
                is NotatFeil.FantIkkeNotat -> return null
                else -> {
                    log.error(
                        "Feil ved henting av notat for referanse {} på sak {}: {}",
                        referanseId,
                        sakId,
                        feil,
                    )
                    return null
                }
            }
        }

        val notat = notatMedVedlegg.notat
        val vedlegg = notatMedVedlegg.vedlegg

        if (notat.notat.isBlank() && notat.attestantNotat.isBlank() && vedlegg.isEmpty()) {
            return null
        }

        val sakInfo = sakService.hentSakInfo(sakId).getOrElse {
            log.error(
                "Kunne ikke hente sakinfo for sakId {} ved journalføring av vedtaksnotat for referanse {}.",
                sakId,
                referanseId,
            )
            return null
        }

        val notatPdf = VedtaksnotatPdfKonverterer.tekstTilPdf(
            tittel = PDF_TITTEL,
            notat = notat.notat,
            attestantNotat = notat.attestantNotat,
        )
        // Bildevedlegg må konverteres til ekte PDF-er før journalføring - Joark støtter ikke lagring
        // av bildefiler (PNG/JPEG) som arkivvariant.
        val journalførbareVedlegg = vedlegg.map { it.tilJournalførbartVedlegg() }

        return journalførVedtaksnotatClient.journalførVedtaksnotat(
            JournalførVedtaksnotatCommand(
                sakstype = sakInfo.type,
                saksnummer = sakInfo.saksnummer,
                fnr = sakInfo.fnr,
                notatId = notat.id,
                tittel = PDF_TITTEL,
                notat = notat.notat,
                attestantNotat = notat.attestantNotat,
                notatPdf = notatPdf,
                vedlegg = journalførbareVedlegg,
                datoDokument = notat.endret,
            ),
        ).fold(
            { feil ->
                log.error(
                    "Kunne ikke journalføre vedtaksnotat {} for referanse {} på sak {}. Feil: {}",
                    notat.id,
                    referanseId,
                    sakId,
                    feil,
                )
                null
            },
            { journalpostId ->
                log.info(
                    "Journalførte vedtaksnotat {} for referanse {} på sak {} som journalpost {}.",
                    notat.id,
                    referanseId,
                    sakId,
                    journalpostId,
                )
                journalpostId
            },
        )
    }

    private companion object {
        const val PDF_TITTEL = "Vedtaksnotat"
    }
}
