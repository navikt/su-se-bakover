package no.nav.su.se.bakover.domain.revurdering

import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.revurdering.brev.BrevvalgRevurdering
import no.nav.su.se.bakover.domain.revurdering.revurderes.VedtakSomRevurderesMånedsvis
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.util.UUID

sealed interface AbstraktRevurdering : Behandling {
    val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Revurdering
        get() = GrunnlagsdataOgVilkårsvurderinger.Revurdering(
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
        )

    val tilRevurdering: UUID
    val vedtakSomRevurderesMånedsvis: VedtakSomRevurderesMånedsvis
    val sakinfo: SakInfo
    override val sakId get() = sakinfo.sakId
    override val saksnummer get() = sakinfo.saksnummer
    override val fnr get() = sakinfo.fnr
    override val sakstype get() = sakinfo.type

    abstract override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering
    fun erÅpen(): Boolean

    val brevvalgRevurdering: BrevvalgRevurdering
}
