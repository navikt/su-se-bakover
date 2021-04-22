package no.nav.su.se.bakover.database.grunnlag

import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurdering
import java.util.UUID

interface VilkårsvurderingRepo {
    fun lagre(behandlingId: UUID, vilkårsvurdering: Vilkårsvurdering<Grunnlag.Uføregrunnlag>)
}
