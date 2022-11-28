package no.nav.su.se.bakover.domain.revurdering

import no.nav.su.se.bakover.domain.visitor.Visitor

interface RevurderingVisitor : Visitor {
    fun visit(revurdering: OpprettetRevurdering)
    fun visit(revurdering: BeregnetRevurdering.Innvilget)
    fun visit(revurdering: BeregnetRevurdering.Opphørt)
    fun visit(revurdering: SimulertRevurdering.Innvilget)
    fun visit(revurdering: SimulertRevurdering.Opphørt)
    fun visit(revurdering: RevurderingTilAttestering.Innvilget)
    fun visit(revurdering: RevurderingTilAttestering.Opphørt)
    fun visit(revurdering: IverksattRevurdering.Innvilget)
    fun visit(revurdering: IverksattRevurdering.Opphørt)
    fun visit(revurdering: UnderkjentRevurdering.Innvilget)
    fun visit(revurdering: UnderkjentRevurdering.Opphørt)
    fun visit(revurdering: AvsluttetRevurdering)
}
