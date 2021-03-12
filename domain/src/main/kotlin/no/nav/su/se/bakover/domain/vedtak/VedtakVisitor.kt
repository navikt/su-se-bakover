package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.domain.visitor.Visitor

interface VedtakVisitor : Visitor {
    fun visit(vedtak: Vedtak.InnvilgetStønad)
    fun visit(vedtak: Vedtak.AvslåttStønad.UtenBeregning)
    fun visit(vedtak: Vedtak.AvslåttStønad.MedBeregning)
}
