package no.nav.su.se.bakover.service.kontrollsamtale

import VedleggValidering.matcherFilnavnMimeType
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.antivirus.VirusScanRequest
import no.nav.su.se.bakover.domain.antivirus.VirusScanService
import no.nav.su.se.bakover.domain.kontrollnotat.KontrollsamtaleNotatVedlegg
import no.nav.su.se.bakover.domain.kontrollnotat.KontrollsamtaleNotatVedleggRepo
import java.time.Clock
import java.util.UUID

class KontrollsamtaleNotatVedleggServiceImpl(
    private val kontrollsamtaleNotatVedleggRepo: KontrollsamtaleNotatVedleggRepo,
    private val virusScanService: VirusScanService,
    private val clock: Clock,
) : KontrollsamtaleNotatVedleggService {

    override fun leggTilVedlegg(
        kontrollsamtaleId: UUID,
        filnavn: String,
        mimeType: String,
        innhold: ByteArray,
    ): Either<KontrollsamtaleNotatVedleggService.KontrollsamtaleNotatVedleggFeil, KontrollsamtaleNotatVedlegg> {
        if (mimeType !in VedleggValidering.tillatteMimeTyper) return KontrollsamtaleNotatVedleggService.KontrollsamtaleNotatVedleggFeil.UgyldigMimeType.left()
        if (!matcherFilnavnMimeType(filnavn, mimeType)) return KontrollsamtaleNotatVedleggService.KontrollsamtaleNotatVedleggFeil.MimeTypeMatcherIkkeFilnavn.left()
        if (innhold.size > VedleggValidering.MAKS_VEDLEGG_STORRELSE_BYTES) return KontrollsamtaleNotatVedleggService.KontrollsamtaleNotatVedleggFeil.FilForStor.left()

        virusScanService.scan(VirusScanRequest(tittel = filnavn, fil = innhold))

        val nå = Tidspunkt.now(clock)
        val vedlegg = KontrollsamtaleNotatVedlegg(
            id = UUID.randomUUID(),
            kontrollsamtaleId = kontrollsamtaleId,
            filnavn = filnavn,
            mimeType = mimeType,
            innhold = innhold,
            opprettet = nå,
        )
        kontrollsamtaleNotatVedleggRepo.lagre(vedlegg)
        return vedlegg.right()
    }

    override fun hentVedlegg(kontrollsamtaleId: UUID): List<KontrollsamtaleNotatVedlegg> {
        return kontrollsamtaleNotatVedleggRepo.hentForKontrollsamtale(kontrollsamtaleId)
    }

    override fun slettVedlegg(
        vedleggId: UUID,
    ) {
        kontrollsamtaleNotatVedleggRepo.slett(vedleggId)
    }
}
