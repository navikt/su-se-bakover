package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet

abstract class Statusovergang<L, T> : StatusovergangVisitor {

    protected lateinit var result: Either<L, T>
    fun get(): Either<L, T> = result

    class TilVilkårsvurdert(
        private val behandlingsinformasjon: Behandlingsinformasjon
    ) : Statusovergang<Nothing, Søknadsbehandling.Vilkårsvurdert>() {

        override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Uavklart) {
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
    ) : Statusovergang<Nothing, Søknadsbehandling.Beregnet>() {

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

    class TilSimulert(
        private val simulering: (beregning: Beregning) -> Either<SimuleringFeilet, Simulering>
    ) : Statusovergang<SimuleringFeilet, Søknadsbehandling.Simulert>() {

        override fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Innvilget) {
            simulering(søknadsbehandling.beregning)
                .mapLeft { result = it.left() }
                .map { result = søknadsbehandling.tilSimulert(it).right() }
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Simulert) {
            simulering(søknadsbehandling.beregning)
                .mapLeft { result = it.left() }
                .map { result = søknadsbehandling.tilSimulert(it).right() }
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Innvilget) {
            simulering(søknadsbehandling.beregning)
                .mapLeft { result = it.left() }
                .map { result = søknadsbehandling.tilSimulert(it).right() }
        }
    }

    class TilAttestering(
        private val saksbehandler: NavIdentBruker.Saksbehandler,
        private val fritekstTilBrev: String,
    ) : Statusovergang<Nothing, Søknadsbehandling.TilAttestering>() {

        override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Avslag) {
            result = søknadsbehandling.tilAttestering(saksbehandler, fritekstTilBrev).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Avslag) {
            result = søknadsbehandling.tilAttestering(saksbehandler, fritekstTilBrev).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Simulert) {
            result = søknadsbehandling.tilAttestering(saksbehandler, fritekstTilBrev).right()
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
            attestering: Attestering
        ): Boolean = søknadsbehandling.saksbehandler.navIdent != attestering.attestant.navIdent
    }

    object SaksbehandlerOgAttestantKanIkkeVæreSammePerson

    class TilIverksatt(
        private val attestering: Attestering,
        private val innvilget: (søknadsbehandling: Søknadsbehandling.TilAttestering.Innvilget) -> Either<KunneIkkeIverksette, UUID30>
    ) : Statusovergang<KunneIkkeIverksette, Søknadsbehandling.Iverksatt>() {

        override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Avslag.UtenBeregning) {
            result = if (saksbehandlerOgAttestantErForskjellig(søknadsbehandling, attestering)) {
                søknadsbehandling.tilIverksatt(attestering).right()
            } else {
                KunneIkkeIverksette.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
            }
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Avslag.MedBeregning) {
            result = if (saksbehandlerOgAttestantErForskjellig(søknadsbehandling, attestering)) {
                søknadsbehandling.tilIverksatt(attestering).right()
            } else {
                KunneIkkeIverksette.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
            }
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Innvilget) {
            result = if (saksbehandlerOgAttestantErForskjellig(søknadsbehandling, attestering)) {
                innvilget(søknadsbehandling)
                    .mapLeft { it }
                    .map { søknadsbehandling.tilIverksatt(attestering) }
            } else {
                KunneIkkeIverksette.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
            }
        }

        private fun saksbehandlerOgAttestantErForskjellig(
            søknadsbehandling: Søknadsbehandling.TilAttestering,
            attestering: Attestering
        ): Boolean = søknadsbehandling.saksbehandler.navIdent != attestering.attestant.navIdent
    }

    class OppdaterStønadsperiode(
        private val stønadsperiode: Stønadsperiode,
    ) : Statusovergang<Nothing, Søknadsbehandling.Vilkårsvurdert>() {

        override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Uavklart) {
            result = søknadsbehandling.copy(stønadsperiode = stønadsperiode, vilkårsvurderinger = søknadsbehandling.vilkårsvurderinger.oppdaterStønadsperiode(stønadsperiode)).tilVilkårsvurdert(søknadsbehandling.behandlingsinformasjon).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Innvilget) {
            result = søknadsbehandling.copy(stønadsperiode = stønadsperiode, vilkårsvurderinger = søknadsbehandling.vilkårsvurderinger.oppdaterStønadsperiode(stønadsperiode)).tilVilkårsvurdert(søknadsbehandling.behandlingsinformasjon).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Avslag) {
            result = søknadsbehandling.copy(stønadsperiode = stønadsperiode, vilkårsvurderinger = søknadsbehandling.vilkårsvurderinger.oppdaterStønadsperiode(stønadsperiode)).tilVilkårsvurdert(søknadsbehandling.behandlingsinformasjon).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Innvilget) {
            result = søknadsbehandling.copy(stønadsperiode = stønadsperiode, vilkårsvurderinger = søknadsbehandling.vilkårsvurderinger.oppdaterStønadsperiode(stønadsperiode)).tilVilkårsvurdert(søknadsbehandling.behandlingsinformasjon).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Avslag) {
            result = søknadsbehandling.copy(stønadsperiode = stønadsperiode, vilkårsvurderinger = søknadsbehandling.vilkårsvurderinger.oppdaterStønadsperiode(stønadsperiode)).tilVilkårsvurdert(søknadsbehandling.behandlingsinformasjon).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Simulert) {
            result = søknadsbehandling.copy(stønadsperiode = stønadsperiode, vilkårsvurderinger = søknadsbehandling.vilkårsvurderinger.oppdaterStønadsperiode(stønadsperiode)).tilVilkårsvurdert(søknadsbehandling.behandlingsinformasjon).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Innvilget) {
            result = søknadsbehandling.copy(stønadsperiode = stønadsperiode, vilkårsvurderinger = søknadsbehandling.vilkårsvurderinger.oppdaterStønadsperiode(stønadsperiode)).tilVilkårsvurdert(søknadsbehandling.behandlingsinformasjon).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Avslag.MedBeregning) {
            result = søknadsbehandling.copy(stønadsperiode = stønadsperiode, vilkårsvurderinger = søknadsbehandling.vilkårsvurderinger.oppdaterStønadsperiode(stønadsperiode)).tilVilkårsvurdert(søknadsbehandling.behandlingsinformasjon).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Avslag.UtenBeregning) {
            result = søknadsbehandling.copy(stønadsperiode = stønadsperiode, vilkårsvurderinger = søknadsbehandling.vilkårsvurderinger.oppdaterStønadsperiode(stønadsperiode)).tilVilkårsvurdert(søknadsbehandling.behandlingsinformasjon).right()
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
