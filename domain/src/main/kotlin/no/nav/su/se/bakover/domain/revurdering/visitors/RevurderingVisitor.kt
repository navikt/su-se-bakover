package no.nav.su.se.bakover.domain.revurdering.visitors

import no.nav.su.se.bakover.domain.revurdering.AvsluttetRevurdering
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.visitor.Visitor

// TODO jah: Slett denne klassen og andre visitors og flytt logikken nærmere der den bør bo.
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
