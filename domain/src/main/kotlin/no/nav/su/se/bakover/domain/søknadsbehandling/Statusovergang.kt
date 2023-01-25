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
    ) : Statusovergang<Nothing, Søknadsbehandling.TilAttestering>() {

        override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Avslag) {
            result = søknadsbehandling.tilAttestering(saksbehandler, fritekstTilBrev, clock).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Avslag) {
            result = søknadsbehandling.tilAttestering(saksbehandler, fritekstTilBrev, clock).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Simulert) {
            result = søknadsbehandling.tilAttestering(saksbehandler, fritekstTilBrev, clock).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Avslag.UtenBeregning) {
            result = søknadsbehandling.tilAttestering(saksbehandler, clock).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Avslag.MedBeregning) {
            result = søknadsbehandling.tilAttestering(saksbehandler, clock).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Innvilget) {
            result = søknadsbehandling.tilAttestering(saksbehandler, clock).right()
        }
    }

    class TilUnderkjent(
        private val attestering: Attestering,
    ) : Statusovergang<SaksbehandlerOgAttestantKanIkkeVæreSammePerson, Søknadsbehandling.Underkjent>() {

        override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Avslag.UtenBeregning) {
            evaluerStatusovergang(søknadsbehandling)
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Avslag.MedBeregning) {
            evaluerStatusovergang(søknadsbehandling)
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Innvilget) {
            evaluerStatusovergang(søknadsbehandling)
        }

        private fun evaluerStatusovergang(søknadsbehandling: Søknadsbehandling.TilAttestering) {
            result = when (saksbehandlerOgAttestantErForskjellig(søknadsbehandling, attestering)) {
                true -> søknadsbehandling.tilUnderkjent(attestering).right()
                false -> SaksbehandlerOgAttestantKanIkkeVæreSammePerson.left()
            }
        }

        private fun saksbehandlerOgAttestantErForskjellig(
            søknadsbehandling: Søknadsbehandling.TilAttestering,
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
