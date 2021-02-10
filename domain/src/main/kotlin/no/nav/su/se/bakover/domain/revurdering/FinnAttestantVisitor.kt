package no.nav.su.se.bakover.domain.revurdering

import no.nav.su.se.bakover.domain.NavIdentBruker

class FinnAttestantVisitor : RevurderingVisitor {
    var attestant: NavIdentBruker.Attestant? = null

    override fun visit(revurdering: OpprettetRevurdering) {}
    override fun visit(revurdering: BeregnetRevurdering) {}
    override fun visit(revurdering: SimulertRevurdering) {}
    override fun visit(revurdering: RevurderingTilAttestering) {}
    override fun visit(revurdering: IverksattRevurdering) { attestant = revurdering.attestant }
}
