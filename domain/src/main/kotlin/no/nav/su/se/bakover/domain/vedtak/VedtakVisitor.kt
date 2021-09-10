package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.domain.visitor.Visitor

interface VedtakVisitor : Visitor {
    fun visit(vedtak: Vedtak.EndringIYtelse.InnvilgetSøknadsbehandling)
    fun visit(vedtak: Vedtak.EndringIYtelse.InnvilgetRevurdering)
    fun visit(vedtak: Vedtak.EndringIYtelse.OpphørtRevurdering)
    fun visit(vedtak: Vedtak.Avslag.AvslagVilkår)
    fun visit(vedtak: Vedtak.Avslag.AvslagBeregning)
    fun visit(vedtak: Vedtak.IngenEndringIYtelse)
}
