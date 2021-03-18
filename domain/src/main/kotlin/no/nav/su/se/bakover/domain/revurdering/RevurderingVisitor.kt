package no.nav.su.se.bakover.domain.revurdering

import no.nav.su.se.bakover.domain.visitor.Visitor

interface RevurderingVisitor : Visitor {
    fun visit(revurdering: OpprettetRevurdering)
    fun visit(revurdering: BeregnetRevurdering)
    fun visit(revurdering: SimulertRevurdering)
    fun visit(revurdering: RevurderingTilAttestering)
    fun visit(revurdering: IverksattRevurdering)
    fun visit(revurdering: UnderkjentRevurdering)
}
