package no.nav.su.se.bakover.service.grunnlag

import no.nav.su.se.bakover.database.grunnlag.VilkårsvurderingRepo
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.util.UUID

interface VilkårsvurderingService {
    fun lagre(behandlingId: UUID, vilkårsvurderinger: Vilkårsvurderinger)
}

internal class VilkårsvurderingServiceImpl(
    private val vilkårsvurderingRepo: VilkårsvurderingRepo,
) : VilkårsvurderingService {
    override fun lagre(behandlingId: UUID, vilkårsvurderinger: Vilkårsvurderinger) {
        vilkårsvurderingRepo.lagre(behandlingId, vilkårsvurderinger.uføre)
    }
}
