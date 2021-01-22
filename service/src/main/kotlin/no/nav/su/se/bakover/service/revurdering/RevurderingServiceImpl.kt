package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.RevurderingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.BeregnetRevurdering
import no.nav.su.se.bakover.domain.behandling.OpprettetRevurdering
import no.nav.su.se.bakover.domain.behandling.Revurdering
import no.nav.su.se.bakover.domain.behandling.SimulertRevurdering
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Beregningsgrunnlag
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import java.util.UUID

internal class RevurderingServiceImpl(
    private val sakService: SakService,
    private val utbetalingService: UtbetalingService,
    private val revurderingRepo: RevurderingRepo
) : RevurderingService {

    override fun opprettRevurdering(sakId: UUID, periode: Periode): Either<RevurderingFeilet, Revurdering> {
        // TODO logikk for å finne ut hva som skal revurderes
        return hentSak(sakId)
            .map { sak ->
                val tilRevurdering = sak.behandlinger()
                    .filter { it.status() == Behandling.BehandlingsStatus.IVERKSATT_INNVILGET }
                    .firstOrNull() { it.beregning()!!.getPeriode() inneholder periode }
                return when (tilRevurdering) {
                    null -> RevurderingFeilet.FantIngentingSomKanRevurderes.left()
                    else -> {
                        val revurdering = OpprettetRevurdering(tilRevurdering = tilRevurdering)
                        revurderingRepo.lagre(revurdering)
                        revurderingRepo.hent(revurdering.id)!!.right()
                    }
                }
            }
    }

    override fun beregnOgSimuler(
        revurderingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        periode: Periode,
        fradrag: List<Fradrag>
    ): Either<RevurderingFeilet, RevurdertBeregning> {
        return when (val revurdering = revurderingRepo.hent(revurderingId)!!) {
            is BeregnetRevurdering, is OpprettetRevurdering, is SimulertRevurdering -> {
                val beregningsgrunnlag = Beregningsgrunnlag.create(
                    beregningsperiode = periode,
                    forventetInntektPerÅr = revurdering.tilRevurdering.behandlingsinformasjon().uførhet?.forventetInntekt?.toDouble()
                        ?: 0.0,
                    fradragFraSaksbehandler = fradrag

                )
                val beregnet = revurdering.beregn(beregningsgrunnlag)
                utbetalingService.simulerUtbetaling(
                    sakId = revurdering.tilRevurdering.sakId,
                    saksbehandler = saksbehandler,
                    beregning = beregnet.beregning
                ).mapLeft {
                    RevurderingFeilet.GeneriskFeil
                }.map {
                    val simulert = beregnet.toSimulert(it.simulering)
                    revurderingRepo.lagre(simulert)
                    RevurdertBeregning(
                        beregning = simulert.tilRevurdering.beregning()!!,
                        revurdert = simulert.beregning
                    )
                }
            }
            else -> {
                throw RuntimeException()
            }
        }
    }

    private fun hentSak(sakId: UUID) = sakService.hentSak(sakId)
        .mapLeft { RevurderingFeilet.FantIkkeSak }
        .map { it }
}

data class RevurdertBeregning(
    val beregning: Beregning,
    val revurdert: Beregning
)
