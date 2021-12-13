package no.nav.su.se.bakover.domain.vilkår

import java.util.UUID

interface UføreVilkårsvurderingRepo {
    fun lagre(behandlingId: UUID, vilkår: Vilkår.Uførhet)
}
