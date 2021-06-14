package no.nav.su.se.bakover.service.grunnlag

import no.nav.su.se.bakover.database.grunnlag.FormueVilkårsvurderingRepo
import no.nav.su.se.bakover.database.grunnlag.UføreVilkårsvurderingRepo
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.util.UUID

interface VilkårsvurderingService {
    // TODO Skal vi lagre alt, selv om vi bare har endret en ting, eller skal vi ha en lagreUføre og en lagreFormue
    fun lagre(behandlingId: UUID, vilkårsvurderinger: Vilkårsvurderinger)
}

internal class VilkårsvurderingServiceImpl(
    private val uføreVilkårsvurderingRepo: UføreVilkårsvurderingRepo,
    private val formueVilkårsvurderingRepo: FormueVilkårsvurderingRepo,
) : VilkårsvurderingService {
    override fun lagre(behandlingId: UUID, vilkårsvurderinger: Vilkårsvurderinger) {
        uføreVilkårsvurderingRepo.lagre(behandlingId, vilkårsvurderinger.uføre)
        formueVilkårsvurderingRepo.lagre(behandlingId, vilkårsvurderinger.formue)
    }
}
