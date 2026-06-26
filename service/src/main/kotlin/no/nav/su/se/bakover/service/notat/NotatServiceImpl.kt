package no.nav.su.se.bakover.service.notat

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import io.ktor.http.ContentType
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.antivirus.VirusScanRequest
import no.nav.su.se.bakover.domain.antivirus.VirusScanService
import no.nav.su.se.bakover.domain.notat.Notat
import no.nav.su.se.bakover.domain.notat.NotatFeil
import no.nav.su.se.bakover.domain.notat.NotatHandling
import no.nav.su.se.bakover.domain.notat.NotatMedVedlegg
import no.nav.su.se.bakover.domain.notat.NotatRepo
import no.nav.su.se.bakover.domain.notat.NotatSaksbehandler
import no.nav.su.se.bakover.domain.notat.NotatService
import no.nav.su.se.bakover.domain.notat.NotatVedlegg
import no.nav.su.se.bakover.domain.notat.VedleggRepo
import no.nav.su.se.bakover.domain.notat.leggTilSaksbehandlerhendelse
import no.nav.su.se.bakover.domain.sak.SakService
import java.time.Clock
import java.util.UUID

class NotatServiceImpl(
    private val notatRepo: NotatRepo,
    private val vedleggRepo: VedleggRepo,
    private val sakService: SakService,
    private val virusScanService: VirusScanService,
) : NotatService {
    companion object {
        // 20mb
        const val MAKS_VEDLEGG_STORRELSE_BYTES = 20 * 1024 * 1024
    }

    private val tillatteMimeTyper = setOf(
        ContentType.Image.JPEG.toString(),
        ContentType.Image.PNG.toString(),
        ContentType.Application.Pdf.toString(),
    )

    override fun hentNotaterForSak(sakId: UUID): Either<NotatFeil, List<Notat>> {
        sakService.hentSakInfo(sakId).getOrElse { return NotatFeil.FantIkkeSak.left() }
        return notatRepo.hentForSak(sakId).right()
    }

    override fun hentNotatMedVedlegg(sakId: UUID, notatId: UUID): Either<NotatFeil, NotatMedVedlegg> {
        val notat = notatRepo.hent(notatId) ?: return NotatFeil.FantIkkeNotat.left()
        if (notat.sakId != sakId) return NotatFeil.NotatTilhørerIkkeSak.left()
        return NotatMedVedlegg(
            notat = notat,
            vedlegg = vedleggRepo.hentForNotat(notatId),
        ).right()
    }

    override fun opprettNotat(
        sakId: UUID,
        referanseId: UUID,
        notat: String,
        saksbehandler: NavIdentBruker.Saksbehandler,
        clock: Clock,
    ): Either<NotatFeil, Notat> {
        if (notat.isBlank()) return NotatFeil.TomtNotat.left()
        sakService.hentSakInfo(sakId).getOrElse { return NotatFeil.FantIkkeSak.left() }
        if (notatRepo.eksistererForReferanse(sakId, referanseId)) return NotatFeil.ReferanseIdAlleredeIBruk.left()
        val nå = Tidspunkt.now(clock)
        val nyNotat = Notat(
            id = UUID.randomUUID(),
            sakId = sakId,
            referanseId = referanseId,
            notat = notat,
            opprettet = nå,
            endret = nå,
            saksbehandler = listOf(
                NotatSaksbehandler(
                    navIdent = saksbehandler,
                    tidspunkt = nå,
                    handling = NotatHandling.OPPRETTET,
                ),
            ),
        )
        notatRepo.opprett(nyNotat)
        return nyNotat.right()
    }

    override fun oppdaterNotat(
        sakId: UUID,
        notatId: UUID,
        notat: String,
        saksbehandler: NavIdentBruker.Saksbehandler,
        clock: Clock,
    ): Either<NotatFeil, Notat> {
        if (notat.isBlank()) return NotatFeil.TomtNotat.left()
        val eksisterende = notatRepo.hent(notatId) ?: return NotatFeil.FantIkkeNotat.left()
        if (eksisterende.sakId != sakId) return NotatFeil.NotatTilhørerIkkeSak.left()
        val nå = Tidspunkt.now(clock)
        val oppdatert = eksisterende.copy(
            notat = notat,
            endret = nå,
        ).leggTilSaksbehandlerhendelse(
            NotatSaksbehandler(
                navIdent = saksbehandler,
                tidspunkt = nå,
                handling = NotatHandling.OPPDATERT,
            ),
        )
        notatRepo.oppdater(oppdatert)
        return oppdatert.right()
    }

    override fun leggTilVedlegg(
        sakId: UUID,
        notatId: UUID,
        filnavn: String,
        mimeType: String,
        innhold: ByteArray,
        saksbehandler: NavIdentBruker.Saksbehandler,
        clock: Clock,
    ): Either<NotatFeil, NotatVedlegg> {
        if (mimeType !in tillatteMimeTyper) return NotatFeil.UgyldigMimeType.left()
        if (!matcherFilnavnMimeType(filnavn, mimeType)) return NotatFeil.MimeTypeMatcherIkkeFilnavn.left()
        if (innhold.size > MAKS_VEDLEGG_STORRELSE_BYTES) return NotatFeil.FilForStor.left()
        val notat = notatRepo.hent(notatId) ?: return NotatFeil.FantIkkeNotat.left()
        if (notat.sakId != sakId) return NotatFeil.NotatTilhørerIkkeSak.left()

        virusScanService.scan(
            VirusScanRequest(
                tittel = filnavn,
                fil = innhold,
            ),
        )

        val nå = Tidspunkt.now(clock)
        val vedlegg = NotatVedlegg(
            id = UUID.randomUUID(),
            notatId = notatId,
            filnavn = filnavn,
            mimeType = mimeType,
            innhold = innhold,
            opprettet = nå,
        )
        vedleggRepo.leggTil(vedlegg)
        notatRepo.oppdater(
            notat.copy(
                endret = nå,
            ).leggTilSaksbehandlerhendelse(
                NotatSaksbehandler(
                    navIdent = saksbehandler,
                    tidspunkt = nå,
                    handling = NotatHandling.VEDLEGG_LAGT_TIL,
                ),
            ),
        )
        return vedlegg.right()
    }

    override fun slettVedlegg(
        sakId: UUID,
        notatId: UUID,
        vedleggId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        clock: Clock,
    ): Either<NotatFeil, Unit> {
        val notat = notatRepo.hent(notatId) ?: return NotatFeil.FantIkkeNotat.left()
        if (notat.sakId != sakId) return NotatFeil.NotatTilhørerIkkeSak.left()
        val vedlegg = vedleggRepo.hent(vedleggId) ?: return NotatFeil.FantIkkeVedlegg.left()
        if (vedlegg.notatId != notatId) return NotatFeil.VedleggTilhørerIkkeNotat.left()
        vedleggRepo.slett(vedleggId)
        val nå = Tidspunkt.now(clock)
        notatRepo.oppdater(
            notat.copy(
                endret = nå,
            ).leggTilSaksbehandlerhendelse(
                NotatSaksbehandler(
                    navIdent = saksbehandler,
                    tidspunkt = nå,
                    handling = NotatHandling.VEDLEGG_SLETTET,
                ),
            ),
        )
        return Unit.right()
    }
}

internal fun matcherFilnavnMimeType(
    filnavn: String,
    mimeType: String,
): Boolean {
    val filendelserPerMimeType = mapOf(
        ContentType.Image.JPEG.toString() to setOf("jpg", "jpeg"),
        ContentType.Image.PNG.toString() to setOf("png"),
        ContentType.Application.Pdf.toString() to setOf("pdf"),
    )
    val filendelse = filnavn.substringAfterLast('.', "").lowercase()
    if (filendelse.isBlank()) return false
    return filendelserPerMimeType[mimeType]?.contains(filendelse) == true
}
