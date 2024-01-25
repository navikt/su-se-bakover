package no.nav.su.se.bakover.vedtak.application

import arrow.core.Either
import arrow.core.left
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.InnvilgetForMåned
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vedtak.Vedtaksammendrag
import no.nav.su.se.bakover.domain.vedtak.tilInnvilgetForMåned
import no.nav.su.se.bakover.vedtak.domain.KunneIkkeStarteNyBehandling
import no.nav.su.se.bakover.vedtak.domain.Vedtak
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import java.time.LocalDate
import java.util.UUID

class VedtakServiceImpl(
    private val vedtakRepo: VedtakRepo,
    private val sakService: SakService,
) : VedtakService {

    override fun lagre(vedtak: Vedtak) {
        return vedtakRepo.lagre(vedtak)
    }

    override fun lagreITransaksjon(vedtak: Vedtak, tx: TransactionContext) {
        return vedtakRepo.lagreITransaksjon(vedtak, tx)
    }

    override fun hentForVedtakId(vedtakId: UUID): Vedtak? {
        return vedtakRepo.hentVedtakForId(vedtakId)
    }

    override fun hentForRevurderingId(revurderingId: RevurderingId): Vedtak? {
        return vedtakRepo.hentForRevurderingId(revurderingId)
    }

    override fun hentJournalpostId(vedtakId: UUID): JournalpostId? {
        return vedtakRepo.hentJournalpostId(vedtakId)
    }

    override fun hentInnvilgetFnrForMåned(måned: Måned): InnvilgetForMåned {
        return vedtakRepo.hentForMåned(måned).tilInnvilgetForMåned(måned)
    }

    override fun hentForUtbetaling(utbetalingId: UUID30): VedtakSomKanRevurderes? {
        return vedtakRepo.hentForUtbetaling(utbetalingId)
    }

    override fun hentForFødselsnumreOgFraOgMedMåned(fødselsnumre: List<Fnr>, fraOgMed: Måned): List<Vedtaksammendrag> {
        return vedtakRepo.hentForFødselsnumreOgFraOgMedMåned(fødselsnumre, fraOgMed)
    }

    override fun hentSøknadsbehandlingsvedtakFraOgMed(fraOgMed: LocalDate): List<UUID> {
        return vedtakRepo.hentSøknadsbehandlingsvedtakFraOgMed(fraOgMed)
    }

    override fun startNyBehandlingFor(vedtakId: UUID): Either<KunneIkkeStarteNyBehandling, Søknadsbehandling> {
        val vedtak = vedtakRepo.hentVedtakForId(vedtakId) ?: return KunneIkkeStarteNyBehandling.FantIkkeVedtak.left()

        vedtak.kanStarteNyBehandling().ifTrue {
            TODO()
            /*
            val sak = sakService.hentSakForVedtak(vedtakId) ?: return KunneIkkeStarteNyBehandling.FantIkkeSak.left()
            sak.opprettNySøknadsbehandling(
                søknadId =,
                clock =,
                saksbehandler = NavIdentBruker.Saksbehandler(navIdent = ""),
                oppdaterOppgave = { oppgaveId: OppgaveId, saksbehandler: NavIdentBruker.Saksbehandler -> },
            )
        */
        }

        return KunneIkkeStarteNyBehandling.VedtakKanIkkeStarteEnNyBehandling.left()
    }
}
