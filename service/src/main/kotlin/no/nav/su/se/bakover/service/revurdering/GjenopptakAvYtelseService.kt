package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vedtak.lagTidslinje
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.VedtakService
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

class GjenopptakAvYtelseService(
    private val utbetalingService: UtbetalingService,
    private val revurderingRepo: RevurderingRepo,
    private val clock: Clock,
    private val vedtakRepo: VedtakRepo,
    private val vedtakService: VedtakService,
    private val sakService: SakService,
) {
    fun gjenopptaYtelse(request: GjenopptaYtelseRequest): Either<KunneIkkeGjenopptaYtelse, GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse> {
        val sisteVedtak = vedtakRepo.hentForSakId(request.sakId)
            .filterIsInstance<VedtakSomKanRevurderes>()
            .lagTidslinje(
                periode = Periode.create(
                    fraOgMed = LocalDate.MIN,
                    tilOgMed = LocalDate.MAX,
                ),
                clock = clock,
            ).tidslinje.lastOrNull()?.originaltVedtak ?: return KunneIkkeGjenopptaYtelse.FantIngenVedtak.left()

        if (sisteVedtak !is Vedtak.EndringIYtelse.StansAvYtelse) {
            return KunneIkkeGjenopptaYtelse.SisteVedtakErIkkeStans.left()
        } else {
            val simulertRevurdering = when (request) {
                is GjenopptaYtelseRequest.Oppdater -> {
                    val update = revurderingRepo.hent(request.revurderingId)
                        ?: return KunneIkkeGjenopptaYtelse.FantIkkeRevurdering.left()

                    val gjeldendeVedtaksdata: GjeldendeVedtaksdata = kopierGjeldendeVedtaksdata(
                        sakId = request.sakId,
                        fraOgMed = sisteVedtak.periode.fraOgMed,
                    ).getOrHandle { return it.left() }

                    val simulering = simuler(request).getOrHandle { return it.left() }

                    when (update) {
                        is GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse -> {
                            GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse(
                                id = update.id,
                                opprettet = update.opprettet,
                                periode = gjeldendeVedtaksdata.periode,
                                grunnlagsdata = gjeldendeVedtaksdata.grunnlagsdata,
                                vilkårsvurderinger = gjeldendeVedtaksdata.vilkårsvurderinger,
                                tilRevurdering = gjeldendeVedtaksdata.gjeldendeVedtakPåDato(sisteVedtak.periode.fraOgMed)!!,
                                saksbehandler = request.saksbehandler,
                                simulering = simulering.simulering,
                                revurderingsårsak = request.revurderingsårsak,
                            )
                        }
                        else -> return KunneIkkeGjenopptaYtelse.UgyldigTypeForOppdatering(update::class).left()
                    }
                }
                is GjenopptaYtelseRequest.Opprett -> {
                    if (sakService.hentSak(request.sakId)
                        .getOrHandle { return KunneIkkeGjenopptaYtelse.FantIkkeSak.left() }
                        .harÅpenRevurderingForGjenopptakAvYtelse()
                    ) return KunneIkkeGjenopptaYtelse.SakHarÅpenRevurderingForGjenopptakAvYtelse.left()

                    val gjeldendeVedtaksdata: GjeldendeVedtaksdata = kopierGjeldendeVedtaksdata(
                        sakId = request.sakId,
                        fraOgMed = sisteVedtak.periode.fraOgMed,
                    ).getOrHandle { return it.left() }

                    val simulering = simuler(request).getOrHandle { return it.left() }

                    GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(clock),
                        periode = gjeldendeVedtaksdata.periode,
                        grunnlagsdata = gjeldendeVedtaksdata.grunnlagsdata,
                        vilkårsvurderinger = gjeldendeVedtaksdata.vilkårsvurderinger,
                        tilRevurdering = gjeldendeVedtaksdata.gjeldendeVedtakPåDato(sisteVedtak.periode.fraOgMed)!!,
                        saksbehandler = request.saksbehandler,
                        simulering = simulering.simulering,
                        revurderingsårsak = request.revurderingsårsak,
                    )
                }
            }

            revurderingRepo.lagre(simulertRevurdering)

            return simulertRevurdering.right()
        }
    }

    fun iverksettGjenopptakAvYtelse(
        revurderingId: UUID,
        attestant: NavIdentBruker.Attestant,
    ): Either<KunneIkkeIverksetteGjenopptakAvYtelse, GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse> {
        val revurdering = revurderingRepo.hent(revurderingId)
            ?: return KunneIkkeIverksetteGjenopptakAvYtelse.FantIkkeRevurdering.left()

        return when (revurdering) {
            is GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse -> {
                val iverksattRevurdering = revurdering.iverksett(
                    Attestering.Iverksatt(
                        attestant,
                        Tidspunkt.now(clock),
                    ),
                ).getOrHandle { return KunneIkkeIverksetteGjenopptakAvYtelse.SimuleringIndikererFeilutbetaling.left() }

                val stansUtbetaling = utbetalingService.gjenopptaUtbetalinger(
                    sakId = iverksattRevurdering.sakId,
                    attestant = iverksattRevurdering.attesteringer.hentSisteAttestering().attestant,
                    simulering = revurdering.simulering,
                ).getOrHandle { return KunneIkkeIverksetteGjenopptakAvYtelse.KunneIkkeUtbetale(it).left() }

                val vedtak = Vedtak.from(iverksattRevurdering, stansUtbetaling.id, clock)

                revurderingRepo.lagre(iverksattRevurdering)
                vedtakRepo.lagre(vedtak)

                return iverksattRevurdering.right()
            }
            else -> KunneIkkeIverksetteGjenopptakAvYtelse.UgyldigTilstand(
                faktiskTilstand = revurdering::class,
            ).left()
        }
    }

    private fun kopierGjeldendeVedtaksdata(
        sakId: UUID,
        fraOgMed: LocalDate,
    ): Either<KunneIkkeGjenopptaYtelse, GjeldendeVedtaksdata> {
        return vedtakService.kopierGjeldendeVedtaksdata(
            sakId = sakId,
            fraOgMed = fraOgMed,
        ).getOrHandle {
            log.error("Kunne ikke opprette revurdering for gjenopptak av ytelse, årsak: $it")
            return KunneIkkeGjenopptaYtelse.KunneIkkeOppretteRevurdering.left()
        }.also {
            if (!it.tidslinjeForVedtakErSammenhengende()) {
                log.error("Kunne ikke opprette revurdering for gjenopptak av ytelse, årsak: tidslinje er ikke sammenhengende.")
                return KunneIkkeGjenopptaYtelse.KunneIkkeOppretteRevurdering.left()
            }
        }.right()
    }

    private fun simuler(request: GjenopptaYtelseRequest): Either<KunneIkkeGjenopptaYtelse, Utbetaling.SimulertUtbetaling> {
        return utbetalingService.simulerGjenopptak(
            sakId = request.sakId,
            saksbehandler = request.saksbehandler,
        ).getOrHandle {
            log.warn("Kunne ikke opprette revurdering for gjenopptak av ytelse, årsak: $it")
            return KunneIkkeGjenopptaYtelse.KunneIkkeSimulere(it).left()
        }.right()
    }
}
