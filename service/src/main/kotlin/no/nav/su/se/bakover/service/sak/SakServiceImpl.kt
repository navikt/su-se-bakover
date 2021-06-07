package no.nav.su.se.bakover.service.sak

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.sak.SakRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NySak
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.revurdering.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.UUID

internal class SakServiceImpl(
    private val sakRepo: SakRepo,
) : SakService {
    private val log = LoggerFactory.getLogger(this::class.java)
    val observers: MutableList<EventObserver> = mutableListOf()

    override fun hentSak(sakId: UUID): Either<FantIkkeSak, Sak> {
        return sakRepo.hentSak(sakId)?.right() ?: FantIkkeSak.left()
    }

    override fun hentSak(fnr: Fnr): Either<FantIkkeSak, Sak> {
        return sakRepo.hentSak(fnr)?.right() ?: FantIkkeSak.left()
    }

    override fun hentSak(saksnummer: Saksnummer): Either<FantIkkeSak, Sak> {
        return sakRepo.hentSak(saksnummer)?.right() ?: FantIkkeSak.left()
    }

    override fun opprettSak(sak: NySak) {
        sakRepo.opprettSak(sak).also {
            hentSak(sak.id).fold(
                ifLeft = { log.error("Opprettet sak men feilet ved henting av den.") },
                ifRight = {
                    observers.forEach { observer -> observer.handle(Event.Statistikk.SakOpprettet(it)) }
                },
            )
        }
    }

    override fun kopierGjeldendeVedtaksdata(sakId: UUID, fraOgMed: LocalDate): Either<KunneIkkeKopiereGjeldendeVedtaksdata, GjeldendeVedtaksdata> {
        val sak = sakRepo.hentSak(sakId) ?: return KunneIkkeKopiereGjeldendeVedtaksdata.FantIkkeSak.left()

        val vedtakSomKanRevurderes = sak.vedtakListe
            .filterIsInstance<VedtakSomKanRevurderes>()
            .ifEmpty { return KunneIkkeKopiereGjeldendeVedtaksdata.FantIngenVedtak.left() }
            .let { NonEmptyList.fromListUnsafe(it) }

        val senesteTilOgMedDato = vedtakSomKanRevurderes.maxOf { it.periode.tilOgMed }
        val periode = Periode.tryCreate(fraOgMed, senesteTilOgMedDato).getOrHandle {
            return KunneIkkeKopiereGjeldendeVedtaksdata.UgyldigPeriode(it).left()
        }

        return GjeldendeVedtaksdata(periode, vedtakSomKanRevurderes).right()
    }
}
