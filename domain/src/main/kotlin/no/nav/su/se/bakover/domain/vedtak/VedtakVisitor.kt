package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.domain.visitor.Visitor

interface VedtakVisitor : Visitor {
    fun visit(vedtak: VedtakInnvilgetSøknadsbehandling)
    fun visit(vedtak: VedtakInnvilgetRevurdering)
    fun visit(vedtak: VedtakInnvilgetRegulering)
    fun visit(vedtak: VedtakOpphørtRevurdering)
    fun visit(vedtak: VedtakAvslagVilkår)
    fun visit(vedtak: VedtakAvslagBeregning)
    fun visit(vedtak: VedtakStansAvYtelse)
    fun visit(vedtak: VedtakGjenopptakAvYtelse)
}
