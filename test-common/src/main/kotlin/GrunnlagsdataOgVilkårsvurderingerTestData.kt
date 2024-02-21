package no.nav.su.se.bakover.test

import behandling.søknadsbehandling.domain.GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling
import behandling.søknadsbehandling.domain.VilkårsvurderingerSøknadsbehandling
import no.nav.su.se.bakover.test.eksterneGrunnlag.eksternGrunnlagHentet
import vilkår.vurderinger.domain.EksterneGrunnlag
import vilkår.vurderinger.domain.Grunnlagsdata

fun nyGrunnlagsdataOgVilkårsvurderingerSøknadsbehandling(
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligMedFradrag(),
    vilkårsvurderinger: VilkårsvurderingerSøknadsbehandling = vilkårsvurderingerSøknadsbehandlingInnvilget(),
    eksterneGrunnlag: EksterneGrunnlag = eksternGrunnlagHentet(),
): GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling = GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling(
    grunnlagsdata = grunnlagsdata,
    vilkårsvurderinger = vilkårsvurderinger,
    eksterneGrunnlag = eksterneGrunnlag,
)
