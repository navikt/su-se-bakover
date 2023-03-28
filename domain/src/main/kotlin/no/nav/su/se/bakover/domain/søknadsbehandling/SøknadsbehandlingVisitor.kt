package no.nav.su.se.bakover.domain.søknadsbehandling

import no.nav.su.se.bakover.domain.visitor.Visitor

interface SøknadsbehandlingVisitor : Visitor {
    fun visit(søknadsbehandling: VilkårsvurdertSøknadsbehandling.Uavklart)
    fun visit(søknadsbehandling: VilkårsvurdertSøknadsbehandling.Innvilget)
    fun visit(søknadsbehandling: VilkårsvurdertSøknadsbehandling.Avslag)
    fun visit(søknadsbehandling: BeregnetSøknadsbehandling.Innvilget)
    fun visit(søknadsbehandling: BeregnetSøknadsbehandling.Avslag)
    fun visit(søknadsbehandling: SimulertSøknadsbehandling)
    fun visit(søknadsbehandling: UnderkjentSøknadsbehandling.Innvilget)
    fun visit(søknadsbehandling: UnderkjentSøknadsbehandling.Avslag.MedBeregning)
    fun visit(søknadsbehandling: UnderkjentSøknadsbehandling.Avslag.UtenBeregning)
    fun visit(søknadsbehandling: SøknadsbehandlingTilAttestering.Avslag.UtenBeregning)
    fun visit(søknadsbehandling: SøknadsbehandlingTilAttestering.Avslag.MedBeregning)
    fun visit(søknadsbehandling: SøknadsbehandlingTilAttestering.Innvilget)
    fun visit(søknadsbehandling: IverksattSøknadsbehandling.Avslag.UtenBeregning)
    fun visit(søknadsbehandling: IverksattSøknadsbehandling.Avslag.MedBeregning)
    fun visit(søknadsbehandling: IverksattSøknadsbehandling.Innvilget)
    fun visit(søknadsbehandling: LukketSøknadsbehandling)
}
