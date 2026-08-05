package no.nav.su.se.bakover.service.notat

import arrow.core.getOrElse
import no.nav.su.se.bakover.domain.notat.JournalførVedtaksnotatClient
import no.nav.su.se.bakover.domain.notat.JournalførVedtaksnotatCommand
import no.nav.su.se.bakover.domain.notat.NotatRepo
import no.nav.su.se.bakover.domain.notat.ReferanseType
import no.nav.su.se.bakover.domain.notat.VedleggRepo
import no.nav.su.se.bakover.domain.sak.SakService
import org.slf4j.LoggerFactory
import java.util.UUID

interface VedtaksnotatJournalføringService {
    fun journalførHvisFinnes(
        sakId: UUID,
        referanseId: UUID,
        referanseType: ReferanseType,
    )

    object Noop : VedtaksnotatJournalføringService {
        override fun journalførHvisFinnes(
            sakId: UUID,
            referanseId: UUID,
            referanseType: ReferanseType,
        ) = Unit
    }
}

class JournalførVedtaksnotatService(
    private val notatRepo: NotatRepo,
    private val vedleggRepo: VedleggRepo,
    private val sakService: SakService,
    private val journalførVedtaksnotatClient: JournalførVedtaksnotatClient,
) : VedtaksnotatJournalføringService {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun journalførHvisFinnes(
        sakId: UUID,
        referanseId: UUID,
        referanseType: ReferanseType,
    ) {
        val notat = notatRepo.hentForReferanse(referanseId, referanseType) ?: return
        if (notat.sakId != sakId) {
            log.warn(
                "Fant notat {} for referanse {} men sakId {} matchet ikke forventet sakId {}. Hopper over journalføring.",
                notat.id,
                referanseId,
                notat.sakId,
                sakId,
            )
            return
        }

        val vedlegg = vedleggRepo.hentForNotat(notat.id)
        if (notat.notat.isBlank() && notat.attestantNotat.isBlank() && vedlegg.isEmpty()) {
            return
        }

        val sakInfo = sakService.hentSakInfo(sakId).getOrElse {
            log.error(
                "Kunne ikke hente sakinfo for sakId {} ved journalføring av vedtaksnotat for referanse {}.",
                sakId,
                referanseId,
            )
            return
        }

        val notatPdf = VedtaksnotatPdfKonverterer.tekstTilPdf(
            tittel = TITEL,
            notat = notat.notat,
            attestantNotat = notat.attestantNotat,
        )
        // Bildevedlegg må konverteres til ekte PDF-er før journalføring - Joark støtter ikke lagring
        // av bildefiler (PNG/JPEG) som arkivvariant.
        val journalførbareVedlegg = vedlegg.map { it.tilJournalførbartVedlegg() }

        journalførVedtaksnotatClient.journalførVedtaksnotat(
            JournalførVedtaksnotatCommand(
                sakstype = sakInfo.type,
                saksnummer = sakInfo.saksnummer,
                fnr = sakInfo.fnr,
                notatId = notat.id,
                tittel = TITEL,
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
            },
            { journalpostId ->
                log.info(
                    "Journalførte vedtaksnotat {} for referanse {} på sak {} som journalpost {}.",
                    notat.id,
                    referanseId,
                    sakId,
                    journalpostId,
                )
            },
        )
    }

    private companion object {
        const val TITEL = "Vedtaksnotat"
    }
}
