package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.RevurderingRepo
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.BeregnetRevurdering
import no.nav.su.se.bakover.domain.behandling.OpprettetRevurdering
import no.nav.su.se.bakover.domain.behandling.Revurdering
import no.nav.su.se.bakover.domain.behandling.SimulertRevurdering
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Beregningsgrunnlag
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import java.util.UUID

internal class RevurderingServiceImpl(
    private val behandlingRepo: BehandlingRepo,
    private val utbetalingService: UtbetalingService,
    private val revurderingRepo: RevurderingRepo
) : RevurderingService {

    override fun opprettRevurdering(behandlingId: UUID): Revurdering {
        // TODO logikk for å finne ut hva som skal revurderes
        val revurdering = OpprettetRevurdering(tilRevurdering = behandlingRepo.hentBehandling(behandlingId)!!)
        revurderingRepo.lagre(revurdering)
        return revurderingRepo.hent(revurdering.id)!!
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
}

data class RevurdertBeregning(
    val beregning: Beregning,
    val revurdert: Beregning
)
