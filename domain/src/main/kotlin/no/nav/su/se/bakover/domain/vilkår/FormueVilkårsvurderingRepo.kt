package no.nav.su.se.bakover.domain.vilkår

import java.util.UUID

interface FormueVilkårsvurderingRepo {
    fun lagre(behandlingId: UUID, vilkår: Vilkår.Formue)
}
