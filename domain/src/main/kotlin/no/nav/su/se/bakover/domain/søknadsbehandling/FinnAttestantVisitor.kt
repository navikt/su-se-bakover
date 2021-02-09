package no.nav.su.se.bakover.domain.søknadsbehandling

import no.nav.su.se.bakover.domain.NavIdentBruker

class FinnAttestantVisitor : SøknadsbehandlingVisitor {
    var attestant: NavIdentBruker.Attestant? = null

    override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Uavklart) {
        attestant = null
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Innvilget) {
        attestant = null
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Avslag) {
        attestant = null
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Innvilget) {
        attestant = null
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Avslag) {
        attestant = null
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Simulert) {
        attestant = null
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Innvilget) {
        attestant = søknadsbehandling.attestering.attestant
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Avslag.MedBeregning) {
        attestant = søknadsbehandling.attestering.attestant
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Avslag.UtenBeregning) {
        attestant = søknadsbehandling.attestering.attestant
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Avslag.UtenBeregning) {
        attestant = null
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Avslag.MedBeregning) {
        attestant = null
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Innvilget) {
        attestant = null
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Iverksatt.Avslag.UtenBeregning) {
        attestant = søknadsbehandling.attestering.attestant
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Iverksatt.Avslag.MedBeregning) {
        attestant = søknadsbehandling.attestering.attestant
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget) {
        attestant = søknadsbehandling.attestering.attestant
    }
}
