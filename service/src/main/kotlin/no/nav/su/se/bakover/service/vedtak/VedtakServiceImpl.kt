package no.nav.su.se.bakover.service.vedtak

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.sak.SakService
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

internal class VedtakServiceImpl(
    private val vedtakRepo: VedtakRepo,
    private val sakService: SakService,
    private val clock: Clock,
    private val satsFactory: SatsFactory,
) : VedtakService {

    override fun lagre(vedtak: Vedtak) {
        return vedtakRepo.lagre(vedtak)
    }

    override fun lagre(vedtak: Vedtak, sessionContext: TransactionContext) {
        return vedtakRepo.lagre(vedtak, sessionContext)
    }

    override fun hentForVedtakId(vedtakId: UUID): Vedtak? {
        return vedtakRepo.hentVedtakForId(vedtakId)
    }

    override fun hentForRevurderingId(revurderingId: UUID): Vedtak? {
        return vedtakRepo.hentForRevurderingId(revurderingId)
    }

    override fun hentJournalpostId(vedtakId: UUID): JournalpostId? {
        return vedtakRepo.hentJournalpostId(vedtakId)
    }

    override fun hentAktiveFnr(fomDato: LocalDate): List<Fnr> {
        return vedtakRepo.hentAktive(fomDato).map {
            it.behandling.fnr
        }.sortedWith(compareBy(Fnr::toString)).distinct()
    }

    override fun kopierGjeldendeVedtaksdata(
        sakId: UUID,
        fraOgMed: LocalDate,
    ): Either<KunneIkkeKopiereGjeldendeVedtaksdata, GjeldendeVedtaksdata> {
        return sakService.hentSak(sakId)
            .mapLeft { KunneIkkeKopiereGjeldendeVedtaksdata.FantIkkeSak }
            .flatMap { sak ->
                sak.kopierGjeldendeVedtaksdata(fraOgMed, clock)
                    .mapLeft {
                        when (it) {
                            is Sak.KunneIkkeHenteGjeldendeVedtaksdata.FinnesIngenVedtakSomKanRevurderes -> {
                                KunneIkkeKopiereGjeldendeVedtaksdata.FantIngenVedtak
                            }
                            is Sak.KunneIkkeHenteGjeldendeVedtaksdata.UgyldigPeriode -> {
                                KunneIkkeKopiereGjeldendeVedtaksdata.UgyldigPeriode(it.feil)
                            }
                        }
                    }
            }
    }

    override fun hentForUtbetaling(utbetalingId: UUID30): VedtakSomKanRevurderes? {
        return vedtakRepo.hentForUtbetaling(utbetalingId)
    }

    /*
    * Hensikten er å vise et "snapshot" av grunnlagsdata ved tidspunktet for et tidligere vedtak.
    * */
    override fun historiskGrunnlagForVedtaksperiode(
        sakId: UUID,
        vedtakId: UUID,
    ): Either<KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak, GjeldendeVedtaksdata> {
        val alleVedtak = vedtakRepo.hentForSakId(sakId)
            .filterIsInstance<VedtakSomKanRevurderes>()

        val vedtak = alleVedtak.find { it.id == vedtakId }
            ?: return KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak.FantIkkeVedtak.left()

        val gjeldendeVedtak = alleVedtak
            .filter { it.opprettet.instant < vedtak.opprettet.instant }
            .ifEmpty { return KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak.IngenTidligereVedtak.left() }
            .let { NonEmptyList.fromListUnsafe(it) }

        return GjeldendeVedtaksdata(vedtak.periode, gjeldendeVedtak, clock).right()
    }
}
