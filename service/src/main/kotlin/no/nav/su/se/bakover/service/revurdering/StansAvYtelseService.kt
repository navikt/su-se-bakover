package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.oppdrag.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalRequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.VedtakService
import java.time.Clock
import java.util.UUID

internal class StansAvYtelseService(
    private val utbetalingService: UtbetalingService,
    private val revurderingRepo: RevurderingRepo,
    private val vedtakService: VedtakService,
    private val sakService: SakService,
    private val clock: Clock,
) {
    private val observers: MutableList<EventObserver> = mutableListOf()

    fun addObserver(eventObserver: EventObserver) {
        observers.add(eventObserver)
    }

    fun stansAvYtelse(
        request: StansYtelseRequest,
    ): Either<KunneIkkeStanseYtelse, StansAvYtelseRevurdering.SimulertStansAvYtelse> {

        val simulertRevurdering = when (request) {
            is StansYtelseRequest.Oppdater -> {
                val eksisterende = revurderingRepo.hent(request.revurderingId)
                    ?: return KunneIkkeStanseYtelse.FantIkkeRevurdering.left()

                val gjeldendeVedtaksdata = kopierGjeldendeVedtaksdata(request)
                    .getOrHandle { return it.left() }

                val simulering = simuler(request)
                    .getOrHandle { return it.left() }

                when (eksisterende) {
                    is StansAvYtelseRevurdering.SimulertStansAvYtelse -> {
                        eksisterende.copy(
                            periode = gjeldendeVedtaksdata.garantertSammenhengendePeriode(),
                            grunnlagsdata = gjeldendeVedtaksdata.grunnlagsdata,
                            vilkårsvurderinger = gjeldendeVedtaksdata.vilkårsvurderinger,
                            tilRevurdering = gjeldendeVedtaksdata.gjeldendeVedtakPåDato(request.fraOgMed)!!.id,
                            saksbehandler = request.saksbehandler,
                            simulering = simulering.simulering,
                            revurderingsårsak = request.revurderingsårsak,
                        )
                    }
                    else -> return KunneIkkeStanseYtelse.UgyldigTypeForOppdatering(eksisterende::class).left()
                }
            }
            is StansYtelseRequest.Opprett -> {
                val sak = sakService.hentSak(request.sakId)
                    .getOrHandle { return KunneIkkeStanseYtelse.FantIkkeSak.left() }

                if (sak.harÅpenRevurderingForStansAvYtelse()) {
                    return KunneIkkeStanseYtelse.SakHarÅpenRevurderingForStansAvYtelse.left()
                }

                // TODO hent rett fra sak
                val gjeldendeVedtaksdata = kopierGjeldendeVedtaksdata(request)
                    .getOrHandle { return it.left() }

                val simulering = simuler(request)
                    .getOrHandle { return it.left() }

                StansAvYtelseRevurdering.SimulertStansAvYtelse(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(clock),
                    periode = gjeldendeVedtaksdata.garantertSammenhengendePeriode(),
                    grunnlagsdata = gjeldendeVedtaksdata.grunnlagsdata,
                    vilkårsvurderinger = gjeldendeVedtaksdata.vilkårsvurderinger,
                    tilRevurdering = gjeldendeVedtaksdata.gjeldendeVedtakPåDato(request.fraOgMed)!!.id,
                    saksbehandler = request.saksbehandler,
                    simulering = simulering.simulering,
                    revurderingsårsak = request.revurderingsårsak,
                    sakinfo = sak.info()
                )
            }
        }

        revurderingRepo.lagre(simulertRevurdering)
        observers.forEach { observer -> observer.handle(Event.Statistikk.RevurderingStatistikk.Stans(simulertRevurdering)) }

        return simulertRevurdering.right()
    }

    fun iverksettStansAvYtelse(
        revurderingId: UUID,
        attestant: NavIdentBruker.Attestant,
    ): Either<KunneIkkeIverksetteStansYtelse, StansAvYtelseRevurdering.IverksattStansAvYtelse> {
        val revurdering = revurderingRepo.hent(revurderingId)
            ?: return KunneIkkeIverksetteStansYtelse.FantIkkeRevurdering.left()

        return when (revurdering) {
            is StansAvYtelseRevurdering.SimulertStansAvYtelse -> {
                val iverksattRevurdering = revurdering.iverksett(
                    Attestering.Iverksatt(
                        attestant = attestant,
                        opprettet = Tidspunkt.now(clock),
                    ),
                ).getOrHandle { return KunneIkkeIverksetteStansYtelse.SimuleringIndikererFeilutbetaling.left() }

                val stansUtbetaling = utbetalingService.stansUtbetalinger(
                    request = UtbetalRequest.Stans(
                        request = SimulerUtbetalingRequest.Stans(
                            sakId = iverksattRevurdering.sakId,
                            saksbehandler = iverksattRevurdering.attesteringer.hentSisteAttestering().attestant,
                            stansdato = iverksattRevurdering.periode.fraOgMed,
                        ),
                        simulering = iverksattRevurdering.simulering,
                    ),
                ).getOrHandle { return KunneIkkeIverksetteStansYtelse.KunneIkkeUtbetale(it).left() }

                val vedtak = VedtakSomKanRevurderes.from(iverksattRevurdering, stansUtbetaling.id, clock)

                revurderingRepo.lagre(iverksattRevurdering)
                vedtakService.lagre(vedtak)
                observers.forEach { observer ->
                    observer.handle(Event.Statistikk.RevurderingStatistikk.Stans(iverksattRevurdering))
                    observer.handle(Event.Statistikk.Vedtaksstatistikk(vedtak))
                }

                return iverksattRevurdering.right()
            }
            else -> KunneIkkeIverksetteStansYtelse.UgyldigTilstand(
                faktiskTilstand = revurdering::class,
            ).left()
        }
    }

    private fun kopierGjeldendeVedtaksdata(request: StansYtelseRequest): Either<KunneIkkeStanseYtelse, GjeldendeVedtaksdata> {
        return vedtakService.kopierGjeldendeVedtaksdata(
            sakId = request.sakId,
            fraOgMed = request.fraOgMed,
        ).getOrHandle {
            log.error("Kunne ikke opprette revurdering for stans av ytelse, årsak: $it")
            return KunneIkkeStanseYtelse.KunneIkkeOppretteRevurdering.left()
        }.also {
            if (!it.tidslinjeForVedtakErSammenhengende()) {
                log.error("Kunne ikke opprette revurdering for stans av ytelse, årsak: tidslinje er ikke sammenhengende.")
                return KunneIkkeStanseYtelse.KunneIkkeOppretteRevurdering.left()
            }
        }.right()
    }

    private fun simuler(request: StansYtelseRequest): Either<KunneIkkeStanseYtelse, Utbetaling.SimulertUtbetaling> {
        return utbetalingService.simulerStans(
            request = SimulerUtbetalingRequest.Stans(
                sakId = request.sakId,
                saksbehandler = request.saksbehandler,
                stansdato = request.fraOgMed,
            ),
        ).getOrHandle {
            log.warn("Kunne ikke opprette revurdering for stans av ytelse, årsak: $it")
            return KunneIkkeStanseYtelse.SimuleringAvStansFeilet(it).left()
        }.right()
    }
}
