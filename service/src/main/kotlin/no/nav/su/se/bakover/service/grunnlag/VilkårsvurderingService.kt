package no.nav.su.se.bakover.service.grunnlag

import no.nav.su.se.bakover.database.grunnlag.FormueVilkårsvurderingRepo
import no.nav.su.se.bakover.database.grunnlag.UføreVilkårsvurderingRepo
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.util.UUID

interface VilkårsvurderingService {
    fun lagre(behandlingId: UUID, vilkårsvurderinger: Vilkårsvurderinger.Revurdering)
}

internal class VilkårsvurderingServiceImpl(
    private val uføreVilkårsvurderingRepo: UføreVilkårsvurderingRepo,
    private val formueVilkårsvurderingRepo: FormueVilkårsvurderingRepo,
) : VilkårsvurderingService {
    override fun lagre(behandlingId: UUID, vilkårsvurderinger: Vilkårsvurderinger.Revurdering) {
        uføreVilkårsvurderingRepo.lagre(behandlingId, vilkårsvurderinger.uføre)
        formueVilkårsvurderingRepo.lagre(behandlingId, vilkårsvurderinger.formue)
    }
}
