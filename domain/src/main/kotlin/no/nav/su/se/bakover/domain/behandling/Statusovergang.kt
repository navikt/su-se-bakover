package no.nav.su.se.bakover.domain.behandling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Beregningsgrunnlag
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import java.util.UUID

sealed class StatusovergangFeilet {
    object Feil : StatusovergangFeilet()
}

abstract class Statusovergang<T> : StatusovergangVisitor {

    protected lateinit var result: Either<StatusovergangFeilet, T>
    fun get(): Either<StatusovergangFeilet, T> = result

    class TilVilkårsvurdert(
        private val behandlingsinformasjon: Behandlingsinformasjon
    ) : Statusovergang<Saksbehandling>() {

        override fun visit(saksbehandling: Saksbehandling.Søknadsbehandling.Opprettet) {
            // TODO when to patch/update behandlingsinformasjon for this style?
            result = saksbehandling.tilVilkårsvurdert(behandlingsinformasjon).right()
        }

        override fun visit(saksbehandling: Saksbehandling.Søknadsbehandling.Vilkårsvurdert) {
            result = saksbehandling.tilVilkårsvurdert(behandlingsinformasjon).right()
        }

        override fun visit(saksbehandling: Saksbehandling.Søknadsbehandling.Beregnet) {
            result = saksbehandling.tilVilkårsvurdert(behandlingsinformasjon).right()
        }

        override fun visit(saksbehandling: Saksbehandling.Søknadsbehandling.Simulert) {
            result = saksbehandling.tilVilkårsvurdert(behandlingsinformasjon).right()
        }
    }

    class TilBeregnet(
        private val periode: Periode,
        private val fradrag: List<Fradrag>
    ) : Statusovergang<Saksbehandling>() {

        override fun visit(saksbehandling: Saksbehandling.Søknadsbehandling.Vilkårsvurdert) {
            result = saksbehandling.tilBeregnet(beregn(saksbehandling)).right()
        }

        override fun visit(saksbehandling: Saksbehandling.Søknadsbehandling.Beregnet) {
            result = saksbehandling.tilBeregnet(beregn(saksbehandling)).right()
        }

        override fun visit(saksbehandling: Saksbehandling.Søknadsbehandling.Simulert) {
            result = saksbehandling.tilBeregnet(beregn(saksbehandling)).right()
        }

        private fun beregn(
            saksbehandling: Saksbehandling.Søknadsbehandling
        ): Beregning {
            val beregningsgrunnlag = Beregningsgrunnlag.create(
                beregningsperiode = Periode.create(periode.getFraOgMed(), periode.getTilOgMed()),
                forventetInntektPerÅr = saksbehandling.behandlingsinformasjon.uførhet?.forventetInntekt?.toDouble()
                    ?: 0.0,
                fradragFraSaksbehandler = fradrag
            )
            val strategy = saksbehandling.behandlingsinformasjon.bosituasjon!!.getBeregningStrategy()
            return strategy.beregn(beregningsgrunnlag)
        }
    }

    class TilSimulert(
        private val saksbehandler: NavIdentBruker,
        private val simulering: (sakId: UUID, saksbehandler: NavIdentBruker, beregning: Beregning) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>

    ) : Statusovergang<Saksbehandling>() {

        override fun visit(saksbehandling: Saksbehandling.Søknadsbehandling.Beregnet) {
            simulering(saksbehandling.sakId, saksbehandler, saksbehandling.beregning)
                .mapLeft { StatusovergangFeilet.Feil.left() }
                .map { result = saksbehandling.tilSimulert(it.simulering).right() }
        }

        override fun visit(saksbehandling: Saksbehandling.Søknadsbehandling.Simulert) {
            simulering(saksbehandling.sakId, saksbehandler, saksbehandling.beregning)
                .mapLeft { StatusovergangFeilet.Feil.left() }
                .map { result = saksbehandling.tilSimulert(it.simulering).right() }
        }
    }
}
