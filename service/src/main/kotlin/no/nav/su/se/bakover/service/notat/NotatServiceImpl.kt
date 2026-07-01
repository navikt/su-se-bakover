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
import no.nav.su.se.bakover.domain.notat.NotatHendelse
import no.nav.su.se.bakover.domain.notat.NotatMedVedlegg
import no.nav.su.se.bakover.domain.notat.NotatRepo
import no.nav.su.se.bakover.domain.notat.NotatResponse
import no.nav.su.se.bakover.domain.notat.NotatService
import no.nav.su.se.bakover.domain.notat.NotatVedlegg
import no.nav.su.se.bakover.domain.notat.ReferanseType
import no.nav.su.se.bakover.domain.notat.VedleggRepo
import no.nav.su.se.bakover.domain.notat.leggTilHendelse
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.HentRequest
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingTilAttestering
import java.time.Clock
import java.util.UUID

class NotatServiceImpl(
    private val notatRepo: NotatRepo,
    private val vedleggRepo: VedleggRepo,
    private val sakService: SakService,
    private val virusScanService: VirusScanService,
    private val revurderingService: RevurderingService,
    private val søknadsbehandlingService: SøknadsbehandlingService,
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

    override fun hentNotataForReferanse(
        sakId: UUID,
        referanseId: UUID,
        referanseType: ReferanseType,
    ): Either<NotatFeil, NotatResponse> {
        sakService.hentSakInfo(sakId).getOrElse { return NotatFeil.FantIkkeSak.left() }
        val notat = notatRepo.hentForReferanse(referanseId, referanseType) ?: return NotatFeil.FantIkkeNotat.left()
        if (notat.sakId != sakId) return NotatFeil.NotatTilhørerIkkeSak.left()
        val vedlegg = vedleggRepo.hentForNotat(notat.id)
        return notat.mapTilResponse(vedlegg.size).right()
    }

    override fun opprettNotat(
        sakId: UUID,
        referanseId: UUID,
        referanseType: ReferanseType,
        saksbehandler: NavIdentBruker.Saksbehandler,
        clock: Clock,
    ): Either<NotatFeil, Notat> {
        sakService.hentSakInfo(sakId).getOrElse { return NotatFeil.FantIkkeSak.left() }
        if (notatRepo.eksistererForReferanse(sakId, referanseId)) return NotatFeil.ReferanseIdAlleredeIBruk.left()
        val nå = Tidspunkt.now(clock)
        val nyNotat = Notat(
            id = UUID.randomUUID(),
            sakId = sakId,
            referanseId = referanseId,
            referanseType = referanseType,
            notat = "",
            opprettet = nå,
            endret = nå,
            hendelser = listOf(
                NotatHendelse(
                    navIdent = saksbehandler,
                    tidspunkt = nå,
                    handling = NotatHandling.OPPRETTET,
                ),
            ),
        )
        notatRepo.opprett(nyNotat)
        return nyNotat.right()
    }

    override fun oppdaterNotatSaksbehandler(
        sakId: UUID,
        notatId: UUID,
        notat: String,
        saksbehandler: NavIdentBruker.Saksbehandler,
        clock: Clock,
    ): Either<NotatFeil, Notat> {
        if (notat.isBlank()) return NotatFeil.TomtNotat.left()
        val eksisterende = notatRepo.hent(notatId) ?: return NotatFeil.FantIkkeNotat.left()
        if (eksisterende.sakId != sakId) return NotatFeil.NotatTilhørerIkkeSak.left()
        kanEndreForSaksbehandler(eksisterende.referanseId, eksisterende.referanseType).getOrElse { return it.left() }

        val nå = Tidspunkt.now(clock)
        val oppdatert = eksisterende.copy(
            notat = notat,
            endret = nå,
        ).leggTilHendelse(
            NotatHendelse(
                navIdent = saksbehandler,
                tidspunkt = nå,
                handling = NotatHandling.OPPDATERT,
            ),
        )
        notatRepo.oppdaterNotatSaksbehandler(oppdatert)
        return oppdatert.right()
    }

    override fun oppdaterNotatAttestant(
        sakId: UUID,
        notatId: UUID,
        attestantNotat: String,
        attestant: NavIdentBruker.Attestant,
        clock: Clock,
    ): Either<NotatFeil, Notat> {
        if (attestantNotat.isBlank()) return NotatFeil.TomtNotat.left()
        val eksisterende = notatRepo.hent(notatId) ?: return NotatFeil.FantIkkeNotat.left()
        if (eksisterende.sakId != sakId) return NotatFeil.NotatTilhørerIkkeSak.left()
        kanEndreForAttestant(eksisterende.referanseId, eksisterende.referanseType).getOrElse { return it.left() }

        val nå = Tidspunkt.now(clock)
        val oppdatert = eksisterende.copy(
            attestantNotat = attestantNotat,
            endret = nå,
        ).leggTilHendelse(
            NotatHendelse(
                navIdent = attestant,
                tidspunkt = nå,
                handling = NotatHandling.OPPDATERT,
            ),
        )
        notatRepo.oppdaterAttestantNotat(oppdatert)
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
        kanEndreVedlegg(notat.referanseId, notat.referanseType).getOrElse { return it.left() }

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
        notatRepo.oppdaterNotatSaksbehandler(
            notat.copy(
                endret = nå,
            ).leggTilHendelse(
                NotatHendelse(
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
        kanEndreVedlegg(notat.referanseId, notat.referanseType).getOrElse { return it.left() }
        vedleggRepo.slett(vedleggId)
        val nå = Tidspunkt.now(clock)
        notatRepo.oppdaterNotatSaksbehandler(
            notat.copy(
                endret = nå,
            ).leggTilHendelse(
                NotatHendelse(
                    navIdent = saksbehandler,
                    tidspunkt = nå,
                    handling = NotatHandling.VEDLEGG_SLETTET,
                ),
            ),
        )
        return Unit.right()
    }

    private fun kanEndreForSaksbehandler(referanseId: UUID, referanseType: ReferanseType): Either<NotatFeil, Unit> {
        return when (referanseType) {
            ReferanseType.SØKNAD -> {
                val behandling =
                    søknadsbehandlingService.hent(HentRequest(behandlingId = SøknadsbehandlingId(referanseId)))
                        .getOrElse { return NotatFeil.FantIkkeBehandling.left() }
                if (!behandling.erÅpen()) return NotatFeil.BehandlingErIkkeÅpen.left()
                if (behandling is SøknadsbehandlingTilAttestering) return NotatFeil.BehandlingErTilAttestering.left()
                Unit.right()
            }

            ReferanseType.REVURDERING -> {
                val rev = revurderingService.hentRevurdering(RevurderingId(referanseId))
                    ?: return NotatFeil.FantIkkeBehandling.left()
                if (!rev.erÅpen()) return NotatFeil.BehandlingErIkkeÅpen.left()
                if (rev is RevurderingTilAttestering) return NotatFeil.BehandlingErTilAttestering.left()
                Unit.right()
            }
        }
    }

    private fun kanEndreForAttestant(referanseId: UUID, referanseType: ReferanseType): Either<NotatFeil, Unit> {
        return when (referanseType) {
            ReferanseType.SØKNAD -> {
                val behandling =
                    søknadsbehandlingService.hent(HentRequest(behandlingId = SøknadsbehandlingId(referanseId)))
                        .getOrElse { return NotatFeil.FantIkkeBehandling.left() }
                if (behandling !is SøknadsbehandlingTilAttestering) return NotatFeil.BehandlingErIkkeTilAttestering.left()
                Unit.right()
            }

            ReferanseType.REVURDERING -> {
                val rev = revurderingService.hentRevurdering(RevurderingId(referanseId))
                    ?: return NotatFeil.FantIkkeBehandling.left()
                if (rev !is RevurderingTilAttestering) return NotatFeil.BehandlingErIkkeTilAttestering.left()
                Unit.right()
            }
        }
    }

    private fun kanEndreVedlegg(referanseId: UUID, referanseType: ReferanseType): Either<NotatFeil, Unit> {
        return when (referanseType) {
            ReferanseType.SØKNAD -> {
                val behandling =
                    søknadsbehandlingService.hent(HentRequest(behandlingId = SøknadsbehandlingId(referanseId)))
                        .getOrElse { return NotatFeil.FantIkkeBehandling.left() }
                if (!behandling.erÅpen()) return NotatFeil.BehandlingErIkkeÅpen.left()
                Unit.right()
            }

            ReferanseType.REVURDERING -> {
                val rev = revurderingService.hentRevurdering(RevurderingId(referanseId))
                    ?: return NotatFeil.FantIkkeBehandling.left()
                if (!rev.erÅpen()) return NotatFeil.BehandlingErIkkeÅpen.left()
                Unit.right()
            }
        }
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
