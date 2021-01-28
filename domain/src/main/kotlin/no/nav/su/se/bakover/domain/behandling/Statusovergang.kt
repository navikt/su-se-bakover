package no.nav.su.se.bakover.domain.behandling

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Beregningsgrunnlag
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import java.util.UUID

sealed class StatusovergangFeilet {
    object Feil : StatusovergangFeilet()
}

abstract class Statusovergang<L, T> : StatusovergangVisitor {

    protected lateinit var result: Either<L, T>
    fun get(): Either<L, T> = result

    class TilVilkårsvurdert(
        private val behandlingsinformasjon: Behandlingsinformasjon
    ) : Statusovergang<Nothing, Søknadsbehandling>() {

        override fun visit(søknadsbehandling: Søknadsbehandling.Opprettet) {
            // TODO when to patch/update behandlingsinformasjon for this style?
            result = søknadsbehandling.tilVilkårsvurdert(behandlingsinformasjon).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Innvilget) {
            result = søknadsbehandling.tilVilkårsvurdert(behandlingsinformasjon).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Avslag) {
            result = søknadsbehandling.tilVilkårsvurdert(behandlingsinformasjon).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Innvilget) {
            result = søknadsbehandling.tilVilkårsvurdert(behandlingsinformasjon).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Avslag) {
            result = søknadsbehandling.tilVilkårsvurdert(behandlingsinformasjon).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Simulert) {
            result = søknadsbehandling.tilVilkårsvurdert(behandlingsinformasjon).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Innvilget) {
            result = søknadsbehandling.tilVilkårsvurdert(behandlingsinformasjon).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Avslag.MedBeregning) {
            result = søknadsbehandling.tilVilkårsvurdert(behandlingsinformasjon).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Avslag.UtenBeregning) {
            result = søknadsbehandling.tilVilkårsvurdert(behandlingsinformasjon).right()
        }
    }

    class TilBeregnet(
        private val periode: Periode,
        private val fradrag: List<Fradrag>
    ) : Statusovergang<Nothing, Søknadsbehandling>() {

        override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Innvilget) {
            result = søknadsbehandling.tilBeregnet(beregn(søknadsbehandling)).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Innvilget) {
            result = søknadsbehandling.tilBeregnet(beregn(søknadsbehandling)).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Avslag) {
            result = søknadsbehandling.tilBeregnet(beregn(søknadsbehandling)).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Simulert) {
            result = søknadsbehandling.tilBeregnet(beregn(søknadsbehandling)).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Avslag.MedBeregning) {
            result = søknadsbehandling.tilBeregnet(beregn(søknadsbehandling)).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Innvilget) {
            result = søknadsbehandling.tilBeregnet(beregn(søknadsbehandling)).right()
        }

        private fun beregn(
            søknadsbehandling: Søknadsbehandling
        ): Beregning {
            val beregningsgrunnlag = Beregningsgrunnlag.create(
                beregningsperiode = Periode.create(periode.getFraOgMed(), periode.getTilOgMed()),
                forventetInntektPerÅr = søknadsbehandling.behandlingsinformasjon.uførhet?.forventetInntekt?.toDouble()
                    ?: 0.0,
                fradragFraSaksbehandler = fradrag
            )
            val strategy = søknadsbehandling.behandlingsinformasjon.bosituasjon!!.getBeregningStrategy()
            return strategy.beregn(beregningsgrunnlag)
        }
    }

    object KunneIkkeSimulereBehandling

    class TilSimulert(
        private val saksbehandler: NavIdentBruker,
        private val simulering: (sakId: UUID, saksbehandler: NavIdentBruker, beregning: Beregning) -> Either<KunneIkkeSimulereBehandling, Utbetaling.SimulertUtbetaling>

    ) : Statusovergang<KunneIkkeSimulereBehandling, Søknadsbehandling>() {

        override fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Innvilget) {
            simulering(søknadsbehandling.sakId, saksbehandler, søknadsbehandling.beregning)
                .mapLeft { result = KunneIkkeSimulereBehandling.left() }
                .map { result = søknadsbehandling.tilSimulert(it.simulering).right() }
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Simulert) {
            simulering(søknadsbehandling.sakId, saksbehandler, søknadsbehandling.beregning)
                .mapLeft { result = KunneIkkeSimulereBehandling.left() }
                .map { result = søknadsbehandling.tilSimulert(it.simulering).right() }
        }
    }

    class TilAttestering(
        private val saksbehandler: NavIdentBruker.Saksbehandler
    ) : Statusovergang<Nothing, Søknadsbehandling.TilAttestering>() {

        override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Avslag) {
            result = søknadsbehandling.tilAttestering(saksbehandler).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Avslag) {
            result = søknadsbehandling.tilAttestering(saksbehandler).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Simulert) {
            result = søknadsbehandling.tilAttestering(saksbehandler).right()
        }
    }

    class TilUnderkjent(
        private val attestering: Attestering
    ) : Statusovergang<Nothing, Søknadsbehandling.Underkjent>() {

        override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering) {
            result = søknadsbehandling.tilUnderkjent(attestering).right()
        }
    }

    class TilIverksatt(
        private val attestering: Attestering
    ) : Statusovergang<Nothing, Søknadsbehandling.Iverksatt>() {

        override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering) {
            result = søknadsbehandling.tilIverksatt(attestering).right()
        }
    }
}

fun <T> statusovergang(
    søknadsbehandling: Søknadsbehandling,
    statusovergang: Statusovergang<Nothing, T>
): T {
    return forsøkStatusovergang(søknadsbehandling, statusovergang).getOrHandle {
        throw IllegalStateException("Det skjedde en feil ved statusovergang: $it")
    }
}

fun <L, T> forsøkStatusovergang(
    søknadsbehandling: Søknadsbehandling,
    statusovergang: Statusovergang<L, T>
): Either<L, T> {
    søknadsbehandling.accept(statusovergang)
    return statusovergang.get()
    // .mapLeft { SaksbehandlingService.Feil } // TODO prolly do some mapping
    // .map { it }
}
