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
    ) : Statusovergang<ValideringsfeilAttestering, SøknadsbehandlingTilAttestering>() {

        override fun visit(søknadsbehandling: VilkårsvurdertSøknadsbehandling.Avslag) {
            result = søknadsbehandling.tilAttesteringForSaksbehandler(saksbehandler, fritekstTilBrev, clock)
        }

        override fun visit(søknadsbehandling: BeregnetSøknadsbehandling.Avslag) {
            result = søknadsbehandling.tilAttestering(saksbehandler, fritekstTilBrev, clock)
        }

        override fun visit(søknadsbehandling: SimulertSøknadsbehandling) {
            result = søknadsbehandling.tilAttestering(saksbehandler, fritekstTilBrev, clock)
        }

        override fun visit(søknadsbehandling: UnderkjentSøknadsbehandling.Avslag.UtenBeregning) {
            result = søknadsbehandling.tilAttestering(saksbehandler, clock)
        }

        override fun visit(søknadsbehandling: UnderkjentSøknadsbehandling.Avslag.MedBeregning) {
            result = søknadsbehandling.tilAttestering(saksbehandler, clock)
        }

        override fun visit(søknadsbehandling: UnderkjentSøknadsbehandling.Innvilget) {
            result = søknadsbehandling.tilAttestering(saksbehandler, clock)
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
    statusovergang: Statusovergang<ValideringsfeilAttestering, T>,
): Either<ValideringsfeilAttestering, T> {
    // Kan aldri være Either.Left<Nothing>
    return forsøkStatusovergang(søknadsbehandling, statusovergang)
}

fun <L, T> forsøkStatusovergang(
    søknadsbehandling: Søknadsbehandling,
    statusovergang: Statusovergang<L, T>,
): Either<L, T> {
    søknadsbehandling.accept(statusovergang)
    return statusovergang.get()
}
