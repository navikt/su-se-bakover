package no.nav.su.se.bakover.service.vedtak

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.vedtak.AutomatiskEllerManuelleSak
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.sak.SakService
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

class VedtakServiceImpl(
    private val vedtakRepo: VedtakRepo,
    private val sakService: SakService,
    private val clock: Clock,
) : VedtakService {

    override fun lagre(vedtak: Vedtak) {
        return vedtakRepo.lagre(vedtak)
    }

    override fun lagre(vedtak: Vedtak, sessionContext: TransactionContext) {
        return vedtakRepo.lagre(vedtak, sessionContext)
    }

    override fun hentForVedtakId(vedtakId: UUID): Vedtak? {
        return vedtakRepo.hentForVedtakId(vedtakId)
    }

    override fun hentJournalpostId(vedtakId: UUID): JournalpostId? {
        return vedtakRepo.hentJournalpostId(vedtakId)
    }

    override fun hentListeOverSakidSomKanReguleres(fomDato: LocalDate): List<AutomatiskEllerManuelleSak> {
        return vedtakRepo.hentVedtakSomKanReguleres(fomDato).distinct()
    }

    override fun kopierGjeldendeVedtaksdataForRegulering(
        sakId: UUID,
        fraOgMed: LocalDate,
    ): Either<KunneIkkeKopiereGjeldendeVedtaksdata, GjeldendeVedtaksdata> {
        val sak = sakService.hentSak(sakId).getOrHandle {
            return KunneIkkeKopiereGjeldendeVedtaksdata.FantIkkeSak.left()
        }

        val vedtakSomKanRevurderes = sak.vedtakListe
            .filterIsInstance<VedtakSomKanRevurderes>()
            // .filterNot { vedtak -> vedtak is VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering } // fjerne de som ikke kan reguleres?
            .ifEmpty { return KunneIkkeKopiereGjeldendeVedtaksdata.FantIngenVedtak.left() }
            .let { NonEmptyList.fromListUnsafe(it) }

        val senesteTilOgMedDato = vedtakSomKanRevurderes.maxOf { it.periode.tilOgMed }
        val periode = Periode.tryCreate(fraOgMed, senesteTilOgMedDato).getOrHandle {
            return KunneIkkeKopiereGjeldendeVedtaksdata.UgyldigPeriode(it).left()
        }

        return GjeldendeVedtaksdata(periode, vedtakSomKanRevurderes, clock).right()
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
        val sak = sakService.hentSak(sakId).getOrHandle {
            return KunneIkkeKopiereGjeldendeVedtaksdata.FantIkkeSak.left()
        }

        val vedtakSomKanRevurderes = sak.vedtakListe
            .filterIsInstance<VedtakSomKanRevurderes>()
            .ifEmpty { return KunneIkkeKopiereGjeldendeVedtaksdata.FantIngenVedtak.left() }
            .let { NonEmptyList.fromListUnsafe(it) }

        val senesteTilOgMedDato = vedtakSomKanRevurderes.maxOf { it.periode.tilOgMed }
        val periode = Periode.tryCreate(fraOgMed, senesteTilOgMedDato).getOrHandle {
            return KunneIkkeKopiereGjeldendeVedtaksdata.UgyldigPeriode(it).left()
        }

        return GjeldendeVedtaksdata(periode, vedtakSomKanRevurderes, clock).right()
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
