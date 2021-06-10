package no.nav.su.se.bakover.database.grunnlag

import no.nav.su.se.bakover.domain.vilkår.Vilkår
import java.util.UUID

interface UføreVilkårsvurderingRepo {
    fun lagre(behandlingId: UUID, vilkår: Vilkår.Uførhet)
}
