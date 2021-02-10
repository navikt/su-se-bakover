package no.nav.su.se.bakover.domain.revurdering

interface RevurderingVisitor {
    fun visit(revurdering: OpprettetRevurdering)
    fun visit(revurdering: BeregnetRevurdering)
    fun visit(revurdering: SimulertRevurdering)
    fun visit(revurdering: RevurderingTilAttestering)
    fun visit(revurdering: IverksattRevurdering)
}
