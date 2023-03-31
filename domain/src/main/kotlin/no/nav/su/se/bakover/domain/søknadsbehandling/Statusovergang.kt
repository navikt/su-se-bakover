package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import java.time.Clock

abstract class Statusovergang<L, T> : StatusovergangVisitor {

    protected lateinit var result: Either<L, T>
    fun get(): Either<L, T> = result

    class TilAttestering(
        private val saksbehandler: NavIdentBruker.Saksbehandler,
        private val fritekstTilBrev: String,
        private val clock: Clock,
    ) : Statusovergang<Nothing, SøknadsbehandlingTilAttestering>() {

        override fun visit(søknadsbehandling: VilkårsvurdertSøknadsbehandling.Avslag) {
            result = søknadsbehandling.tilAttesteringForSaksbehandler(saksbehandler, fritekstTilBrev, clock).right()
        }

        override fun visit(søknadsbehandling: BeregnetSøknadsbehandling.Avslag) {
            result = søknadsbehandling.tilAttestering(saksbehandler, fritekstTilBrev, clock).right()
        }

        override fun visit(søknadsbehandling: SimulertSøknadsbehandling) {
            result = søknadsbehandling.tilAttestering(saksbehandler, fritekstTilBrev, clock).right()
        }

        override fun visit(søknadsbehandling: UnderkjentSøknadsbehandling.Avslag.UtenBeregning) {
            result = søknadsbehandling.tilAttestering(saksbehandler, clock).right()
        }

        override fun visit(søknadsbehandling: UnderkjentSøknadsbehandling.Avslag.MedBeregning) {
            result = søknadsbehandling.tilAttestering(saksbehandler, clock).right()
        }

        override fun visit(søknadsbehandling: UnderkjentSøknadsbehandling.Innvilget) {
            result = søknadsbehandling.tilAttestering(saksbehandler, clock).right()
        }
    }

    class TilUnderkjent(
        private val attestering: Attestering,
    ) : Statusovergang<SaksbehandlerOgAttestantKanIkkeVæreSammePerson, UnderkjentSøknadsbehandling>() {

        override fun visit(søknadsbehandling: SøknadsbehandlingTilAttestering.Avslag.UtenBeregning) {
            evaluerStatusovergang(søknadsbehandling)
        }

        override fun visit(søknadsbehandling: SøknadsbehandlingTilAttestering.Avslag.MedBeregning) {
            evaluerStatusovergang(søknadsbehandling)
        }

        override fun visit(søknadsbehandling: SøknadsbehandlingTilAttestering.Innvilget) {
            evaluerStatusovergang(søknadsbehandling)
        }

        private fun evaluerStatusovergang(søknadsbehandling: SøknadsbehandlingTilAttestering) {
            result = when (saksbehandlerOgAttestantErForskjellig(søknadsbehandling, attestering)) {
                true -> søknadsbehandling.tilUnderkjent(attestering).right()
                false -> SaksbehandlerOgAttestantKanIkkeVæreSammePerson.left()
            }
        }

        private fun saksbehandlerOgAttestantErForskjellig(
            søknadsbehandling: SøknadsbehandlingTilAttestering,
            attestering: Attestering,
        ): Boolean = søknadsbehandling.saksbehandler.navIdent != attestering.attestant.navIdent
    }

    object SaksbehandlerOgAttestantKanIkkeVæreSammePerson
}

fun <T> statusovergang(
    søknadsbehandling: Søknadsbehandling,
    statusovergang: Statusovergang<Nothing, T>,
): T {
    // Kan aldri være Either.Left<Nothing>
    return forsøkStatusovergang(søknadsbehandling, statusovergang).getOrNull()!!
}

fun <L, T> forsøkStatusovergang(
    søknadsbehandling: Søknadsbehandling,
    statusovergang: Statusovergang<L, T>,
): Either<L, T> {
    søknadsbehandling.accept(statusovergang)
    return statusovergang.get()
}
