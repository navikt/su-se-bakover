package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.domain.visitor.Visitor

interface VedtakVisitor : Visitor {
    fun visit(vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling)
    fun visit(vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering)
    fun visit(vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRegulering)
    fun visit(vedtak: VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering)
    fun visit(vedtak: Avslagsvedtak.AvslagVilkår)
    fun visit(vedtak: Avslagsvedtak.AvslagBeregning)
    fun visit(vedtak: VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse)
    fun visit(vedtak: VedtakSomKanRevurderes.EndringIYtelse.GjenopptakAvYtelse)
}
