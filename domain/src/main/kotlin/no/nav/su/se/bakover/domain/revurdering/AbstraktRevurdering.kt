package no.nav.su.se.bakover.domain.revurdering

import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.util.UUID

sealed class AbstraktRevurdering : Behandling {
    val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Revurdering
        get() = GrunnlagsdataOgVilkårsvurderinger.Revurdering(
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
        )

    abstract val tilRevurdering: UUID
    abstract val sakinfo: SakInfo
    override val sakId by lazy { sakinfo.sakId }
    override val saksnummer by lazy { sakinfo.saksnummer }
    override val fnr by lazy { sakinfo.fnr }
    override val sakstype: Sakstype by lazy { sakinfo.type }

    abstract override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering
    abstract fun erÅpen(): Boolean

    abstract val brevvalgRevurdering: BrevvalgRevurdering
}
