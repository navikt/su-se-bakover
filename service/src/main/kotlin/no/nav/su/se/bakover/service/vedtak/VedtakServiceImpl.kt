package no.nav.su.se.bakover.service.vedtak

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
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
    * Syftet er å vise ett "snapshot" av grunnlagsdata vid tidspunktet for ett tidligere vedtak.
    * */
    override fun hentTidligereGrunnlagsdataForVedtak(
        sakId: UUID,
        vedtakId: UUID,
    ): Either<KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak, GjeldendeVedtaksdata> {
        val alleVedtak = vedtakRepo.hentForSakId(sakId)
            .filterIsInstance<VedtakSomKanRevurderes>()

        val vedtak = alleVedtak.find { it.id == vedtakId }
            ?: return KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak.FantIkkeSpecificertVedtak.left()

        val gjeldendeVedtak = alleVedtak
            .filter { it.opprettet.instant < vedtak.opprettet.instant }
            .ifEmpty { return KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak.IngenTidligereVedtak.left() }
            .let { NonEmptyList.fromListUnsafe(it) }

        return GjeldendeVedtaksdata(vedtak.periode, gjeldendeVedtak, clock).right()
    }
}
