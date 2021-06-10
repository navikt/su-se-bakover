package no.nav.su.se.bakover.database.grunnlag

import no.nav.su.se.bakover.domain.vilkår.Vilkår
import java.util.UUID

interface FormueVilkårsvurderingRepo {
    fun lagre(behandlingId: UUID, vilkår: Vilkår.Formue)
}
