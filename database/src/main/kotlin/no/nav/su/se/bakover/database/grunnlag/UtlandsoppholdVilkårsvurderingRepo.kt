package no.nav.su.se.bakover.database.grunnlag

import no.nav.su.se.bakover.domain.vilkår.OppholdIUtlandetVilkår
import java.util.UUID

interface UtlandsoppholdVilkårsvurderingRepo {
    fun lagre(behandlingId: UUID, vilkår: OppholdIUtlandetVilkår)
}
