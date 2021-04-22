package no.nav.su.se.bakover.service.grunnlag

import no.nav.su.se.bakover.database.grunnlag.VilkårsvurderingRepo
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import java.util.UUID

interface VilkårsvurderingService {
    fun lagre(behandlingId: UUID, grunnlag: Vilkår.Vurdert.Uførhet)
}

internal class VilkårsvurderingServiceImpl(
    private val vilkårsvurderingRepo: VilkårsvurderingRepo,
) : VilkårsvurderingService {
    override fun lagre(behandlingId: UUID, grunnlag: Vilkår.Vurdert.Uførhet) {
        vilkårsvurderingRepo.lagre(behandlingId, grunnlag)
    }
}
