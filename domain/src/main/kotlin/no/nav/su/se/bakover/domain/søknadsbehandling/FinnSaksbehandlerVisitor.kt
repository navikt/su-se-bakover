package no.nav.su.se.bakover.domain.søknadsbehandling

import no.nav.su.se.bakover.domain.NavIdentBruker

class FinnSaksbehandlerVisitor : SøknadsbehandlingVisitor {
    var saksbehandler: NavIdentBruker.Saksbehandler? = null

    override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Uavklart) {
        saksbehandler = null
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Innvilget) {
        saksbehandler = null
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Avslag) {
        saksbehandler = null
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Innvilget) {
        saksbehandler = null
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Avslag) {
        saksbehandler = null
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Simulert) {
        saksbehandler = null
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Innvilget) {
        saksbehandler = søknadsbehandling.saksbehandler
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Avslag.MedBeregning) {
        saksbehandler = søknadsbehandling.saksbehandler
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Avslag.UtenBeregning) {
        saksbehandler = søknadsbehandling.saksbehandler
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Avslag.UtenBeregning) {
        saksbehandler = søknadsbehandling.saksbehandler
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Avslag.MedBeregning) {
        saksbehandler = søknadsbehandling.saksbehandler
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Innvilget) {
        saksbehandler = søknadsbehandling.saksbehandler
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Iverksatt.Avslag.UtenBeregning) {
        saksbehandler = søknadsbehandling.saksbehandler
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Iverksatt.Avslag.MedBeregning) {
        saksbehandler = søknadsbehandling.saksbehandler
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget) {
        saksbehandler = søknadsbehandling.saksbehandler
    }
}
