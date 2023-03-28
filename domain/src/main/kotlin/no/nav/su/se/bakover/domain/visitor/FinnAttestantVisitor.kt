package no.nav.su.se.bakover.domain.visitor

import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.domain.revurdering.AvsluttetRevurdering
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.revurdering.visitors.RevurderingVisitor
import no.nav.su.se.bakover.domain.søknadsbehandling.BeregnetSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SimulertSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingVisitor
import no.nav.su.se.bakover.domain.søknadsbehandling.UnderkjentSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling

// TODO jah: Slett denne klassen og andre visitors og flytt logikken nærmere der den bør bo.
class FinnAttestantVisitor : SøknadsbehandlingVisitor, RevurderingVisitor {
    var attestant: NavIdentBruker.Attestant? = null

    override fun visit(søknadsbehandling: VilkårsvurdertSøknadsbehandling.Uavklart) {}
    override fun visit(søknadsbehandling: VilkårsvurdertSøknadsbehandling.Innvilget) {}
    override fun visit(søknadsbehandling: VilkårsvurdertSøknadsbehandling.Avslag) {}
    override fun visit(søknadsbehandling: BeregnetSøknadsbehandling.Innvilget) {}
    override fun visit(søknadsbehandling: BeregnetSøknadsbehandling.Avslag) {}
    override fun visit(søknadsbehandling: SimulertSøknadsbehandling) {}
    override fun visit(søknadsbehandling: UnderkjentSøknadsbehandling.Innvilget) {
        attestant = søknadsbehandling.attesteringer.hentSisteAttestering().attestant
    }

    override fun visit(søknadsbehandling: UnderkjentSøknadsbehandling.Avslag.MedBeregning) {
        attestant = søknadsbehandling.attesteringer.hentSisteAttestering().attestant
    }

    override fun visit(søknadsbehandling: UnderkjentSøknadsbehandling.Avslag.UtenBeregning) {
        attestant = søknadsbehandling.attesteringer.hentSisteAttestering().attestant
    }

    override fun visit(søknadsbehandling: SøknadsbehandlingTilAttestering.Avslag.UtenBeregning) {}
    override fun visit(søknadsbehandling: SøknadsbehandlingTilAttestering.Avslag.MedBeregning) {}
    override fun visit(søknadsbehandling: SøknadsbehandlingTilAttestering.Innvilget) {}

    override fun visit(søknadsbehandling: IverksattSøknadsbehandling.Avslag.UtenBeregning) {
        attestant = søknadsbehandling.attesteringer.hentSisteAttestering().attestant
    }

    override fun visit(søknadsbehandling: IverksattSøknadsbehandling.Avslag.MedBeregning) {
        attestant = søknadsbehandling.attesteringer.hentSisteAttestering().attestant
    }

    override fun visit(søknadsbehandling: IverksattSøknadsbehandling.Innvilget) {
        attestant = søknadsbehandling.attesteringer.hentSisteAttestering().attestant
    }

    override fun visit(søknadsbehandling: LukketSøknadsbehandling) {
        attestant = FinnAttestantVisitor().let {
            søknadsbehandling.underliggendeSøknadsbehandling.accept(it)
            it.attestant
        }
    }

    override fun visit(revurdering: OpprettetRevurdering) {}
    override fun visit(revurdering: BeregnetRevurdering.Opphørt) {}
    override fun visit(revurdering: BeregnetRevurdering.Innvilget) {}
    override fun visit(revurdering: SimulertRevurdering.Opphørt) {}
    override fun visit(revurdering: SimulertRevurdering.Innvilget) {}
    override fun visit(revurdering: RevurderingTilAttestering.Opphørt) {}
    override fun visit(revurdering: RevurderingTilAttestering.Innvilget) {}
    override fun visit(revurdering: IverksattRevurdering.Innvilget) {
        attestant = revurdering.attestering.attestant
    }

    override fun visit(revurdering: IverksattRevurdering.Opphørt) {
        attestant = revurdering.attestering.attestant
    }

    override fun visit(revurdering: UnderkjentRevurdering.Innvilget) {
        attestant = revurdering.attestering.attestant
    }

    override fun visit(revurdering: UnderkjentRevurdering.Opphørt) {
        attestant = revurdering.attestering.attestant
    }

    override fun visit(revurdering: AvsluttetRevurdering) {}
}
