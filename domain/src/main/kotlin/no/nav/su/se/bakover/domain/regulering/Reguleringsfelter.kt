package no.nav.su.se.bakover.domain.regulering

import beregning.domain.Beregning
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Stønadsbehandling
import no.nav.su.se.bakover.domain.grunnlag.EksterneGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.grunnlag.StøtterIkkeHentingAvEksternGrunnlag
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import økonomi.domain.simulering.Simulering

interface Reguleringsfelter : Stønadsbehandling {
    override val beregning: Beregning?
    override val simulering: Simulering?
    override val eksterneGrunnlag: EksterneGrunnlag
        get() = StøtterIkkeHentingAvEksternGrunnlag
    val saksbehandler: NavIdentBruker.Saksbehandler
    val reguleringstype: Reguleringstype
    override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Revurdering
    override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering get() = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger
}
