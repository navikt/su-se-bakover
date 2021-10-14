package no.nav.su.se.bakover.domain.søknadsbehandling

import no.nav.su.se.bakover.domain.visitor.Visitor

interface SøknadsbehandlingVisitor : Visitor {
    fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Uavklart)
    fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Innvilget)
    fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Avslag)
    fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Innvilget)
    fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Avslag)
    fun visit(søknadsbehandling: Søknadsbehandling.Simulert)
    fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Innvilget)
    fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Avslag.MedBeregning)
    fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Avslag.UtenBeregning)
    fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Avslag.UtenBeregning)
    fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Avslag.MedBeregning)
    fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Innvilget)
    fun visit(søknadsbehandling: Søknadsbehandling.Iverksatt.Avslag.UtenBeregning)
    fun visit(søknadsbehandling: Søknadsbehandling.Iverksatt.Avslag.MedBeregning)
    fun visit(søknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget)
    fun visit(søknadsbehandling: LukketSøknadsbehandling)
}
