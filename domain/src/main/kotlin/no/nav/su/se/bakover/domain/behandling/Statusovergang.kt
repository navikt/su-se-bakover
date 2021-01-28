package no.nav.su.se.bakover.domain.behandling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering

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
        private val beregn: () -> Beregning
    ) : Statusovergang<Nothing, Søknadsbehandling>() {

        override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Innvilget) {
            result = søknadsbehandling.tilBeregnet(beregn()).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Innvilget) {
            result = søknadsbehandling.tilBeregnet(beregn()).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Avslag) {
            result = søknadsbehandling.tilBeregnet(beregn()).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Simulert) {
            result = søknadsbehandling.tilBeregnet(beregn()).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Avslag.MedBeregning) {
            result = søknadsbehandling.tilBeregnet(beregn()).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Innvilget) {
            result = søknadsbehandling.tilBeregnet(beregn()).right()
        }
    }

    object KunneIkkeSimulereBehandling

    class TilSimulert(
        private val simulering: (beregning: Beregning) -> Either<KunneIkkeSimulereBehandling, Simulering>
    ) : Statusovergang<KunneIkkeSimulereBehandling, Søknadsbehandling>() {

        override fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Innvilget) {
            simulering(søknadsbehandling.beregning)
                .mapLeft { result = KunneIkkeSimulereBehandling.left() }
                .map { result = søknadsbehandling.tilSimulert(it).right() }
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Simulert) {
            simulering(søknadsbehandling.beregning)
                .mapLeft { result = KunneIkkeSimulereBehandling.left() }
                .map { result = søknadsbehandling.tilSimulert(it).right() }
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Innvilget) {
            simulering(søknadsbehandling.beregning)
                .mapLeft { result = KunneIkkeSimulereBehandling.left() }
                .map { result = søknadsbehandling.tilSimulert(it).right() }
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

        override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Avslag.UtenBeregning) {
            result = søknadsbehandling.tilAttestering(saksbehandler).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Avslag.MedBeregning) {
            result = søknadsbehandling.tilAttestering(saksbehandler).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Innvilget) {
            result = søknadsbehandling.tilAttestering(saksbehandler).right()
        }
    }

    class TilUnderkjent(
        private val attestering: Attestering
    ) : Statusovergang<Nothing, Søknadsbehandling.Underkjent>() {

        override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Avslag.UtenBeregning) {
            result = søknadsbehandling.tilUnderkjent(attestering).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Avslag.MedBeregning) {
            result = søknadsbehandling.tilUnderkjent(attestering).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Innvilget) {
            result = søknadsbehandling.tilUnderkjent(attestering).right()
        }
    }

    class TilIverksatt(
        private val attestering: Attestering
    ) : Statusovergang<Nothing, Søknadsbehandling.Iverksatt>() {

        override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Avslag.UtenBeregning) {
            result = søknadsbehandling.tilIverksatt(attestering).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Avslag.MedBeregning) {
            result = søknadsbehandling.tilIverksatt(attestering).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Innvilget) {
            result = søknadsbehandling.tilIverksatt(attestering).right()
        }
    }
}

fun <T> statusovergang(
    søknadsbehandling: Søknadsbehandling,
    statusovergang: Statusovergang<Nothing, T>
): T {
    // Kan aldri være Either.Left<Nothing>
    return forsøkStatusovergang(søknadsbehandling, statusovergang).orNull()!!
}

fun <L, T> forsøkStatusovergang(
    søknadsbehandling: Søknadsbehandling,
    statusovergang: Statusovergang<L, T>
): Either<L, T> {
    søknadsbehandling.accept(statusovergang)
    return statusovergang.get()
}
