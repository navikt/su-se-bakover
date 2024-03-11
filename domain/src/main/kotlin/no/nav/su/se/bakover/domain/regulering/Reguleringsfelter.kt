package no.nav.su.se.bakover.domain.regulering

import behandling.domain.Stønadsbehandling
import behandling.revurdering.domain.GrunnlagsdataOgVilkårsvurderingerRevurdering
import behandling.revurdering.domain.VilkårsvurderingerRevurdering
import beregning.domain.Beregning
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import vilkår.vurderinger.domain.EksterneGrunnlag
import vilkår.vurderinger.domain.StøtterIkkeHentingAvEksternGrunnlag
import økonomi.domain.simulering.Simulering

interface Reguleringsfelter : Stønadsbehandling {
    override val id: ReguleringId
    override val beregning: Beregning?
    override val simulering: Simulering?
    override val eksterneGrunnlag: EksterneGrunnlag
        get() = StøtterIkkeHentingAvEksternGrunnlag
    val saksbehandler: NavIdentBruker.Saksbehandler
    val reguleringstype: Reguleringstype
    override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerRevurdering
    override val vilkårsvurderinger: VilkårsvurderingerRevurdering get() = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger
}
