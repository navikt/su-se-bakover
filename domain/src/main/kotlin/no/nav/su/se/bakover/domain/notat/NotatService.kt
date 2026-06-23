package no.nav.su.se.bakover.domain.notat

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.sak.SakService
import java.time.Clock
import java.util.UUID

interface NotatRepo {
    fun opprett(notat: Notat)
    fun oppdater(notat: Notat)
    fun hent(notatId: UUID): Notat?
    fun hentForSak(sakId: UUID): List<Notat>
    fun eksistererForReferanse(sakId: UUID, referanseId: UUID): Boolean
}

interface VedleggRepo {
    fun leggTil(vedlegg: NotatVedlegg)
    fun slett(vedleggId: UUID)
    fun hent(vedleggId: UUID): NotatVedlegg?
    fun hentForNotat(notatId: UUID): List<NotatVedlegg>
}

sealed interface NotatFeil {
    data object FantIkkeSak : NotatFeil
    data object FantIkkeNotat : NotatFeil
    data object FantIkkeVedlegg : NotatFeil
    data object VedleggTilhørerIkkeNotat : NotatFeil
    data object NotatTilhørerIkkeSak : NotatFeil
    data object TomtNotat : NotatFeil
    data object ReferanseIdAlleredeIBruk : NotatFeil
}

interface NotatService {
    fun hentNotaterForSak(sakId: UUID): Either<NotatFeil, List<Notat>>

    fun hentNotatMedVedlegg(sakId: UUID, notatId: UUID): Either<NotatFeil, NotatMedVedlegg>

    fun opprettNotat(
        sakId: UUID,
        referanseId: UUID,
        notat: String,
        saksbehandler: NavIdentBruker.Saksbehandler,
        clock: Clock,
    ): Either<NotatFeil, Notat>

    fun oppdaterNotat(
        sakId: UUID,
        notatId: UUID,
        notat: String,
        saksbehandler: NavIdentBruker.Saksbehandler,
        clock: Clock,
    ): Either<NotatFeil, Notat>

    fun leggTilVedlegg(
        sakId: UUID,
        notatId: UUID,
        filnavn: String,
        innhold: ByteArray,
        saksbehandler: NavIdentBruker.Saksbehandler,
        clock: Clock,
    ): Either<NotatFeil, NotatVedlegg>

    fun slettVedlegg(
        sakId: UUID,
        notatId: UUID,
        vedleggId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        clock: Clock,
    ): Either<NotatFeil, Unit>
}

class NotatServiceImpl(
    private val notatRepo: NotatRepo,
    private val vedleggRepo: VedleggRepo,
    private val sakService: SakService,
) : NotatService {

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
            saksbehandler = NotatSaksbehandler(
                navIdent = saksbehandler,
                tidspunkt = nå,
                handling = NotatHandling.OPPRETTET,
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
            saksbehandler = NotatSaksbehandler(
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
        innhold: ByteArray,
        saksbehandler: NavIdentBruker.Saksbehandler,
        clock: Clock,
    ): Either<NotatFeil, NotatVedlegg> {
        val notat = notatRepo.hent(notatId) ?: return NotatFeil.FantIkkeNotat.left()
        if (notat.sakId != sakId) return NotatFeil.NotatTilhørerIkkeSak.left()
        val nå = Tidspunkt.now(clock)
        val vedlegg = NotatVedlegg(
            id = UUID.randomUUID(),
            notatId = notatId,
            filnavn = filnavn,
            innhold = innhold,
            opprettet = nå,
        )
        vedleggRepo.leggTil(vedlegg)
        notatRepo.oppdater(
            notat.copy(
                endret = nå,
                saksbehandler = NotatSaksbehandler(
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
                saksbehandler = NotatSaksbehandler(
                    navIdent = saksbehandler,
                    tidspunkt = nå,
                    handling = NotatHandling.VEDLEGG_SLETTET,
                ),
            ),
        )
        return Unit.right()
    }
}
