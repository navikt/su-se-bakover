package no.nav.su.se.bakover.domain.revurdering

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.behandling.Stønadsbehandling
import no.nav.su.se.bakover.domain.grunnlag.EksterneGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.grunnlag.StøtterIkkeHentingAvEksternGrunnlag
import no.nav.su.se.bakover.domain.revurdering.brev.BrevvalgRevurdering
import no.nav.su.se.bakover.domain.revurdering.revurderes.VedtakSomRevurderesMånedsvis
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.util.UUID

sealed interface AbstraktRevurdering : Stønadsbehandling {
    override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Revurdering
    override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering get() = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger

    /** TODO jah: Fjern fra domenemodellen? Bør heller bruke [vedtakSomRevurderesMånedsvis]*/
    val tilRevurdering: UUID
    val vedtakSomRevurderesMånedsvis: VedtakSomRevurderesMånedsvis
    val sakinfo: SakInfo

    /**
     * Tidspunktet referer til enten når revurderinger ble opprettet, eller dersom den senere har blitt oppdatert.
     * En oppdatering gjøres i en operasjon og kan endre perioden, årsaken, hva som revurderes, begrunnelsen.
     * Basert på det den endrer vil den hente ny relevant data fra saken og populere felter som grunnlag, vilkår og avkorting.
     */
    val oppdatert: Tidspunkt

    override val sakId get() = sakinfo.sakId
    override val saksnummer get() = sakinfo.saksnummer
    override val fnr get() = sakinfo.fnr
    override val sakstype get() = sakinfo.type

    override val eksterneGrunnlag: EksterneGrunnlag
        get() = StøtterIkkeHentingAvEksternGrunnlag

    fun erÅpen(): Boolean

    val brevvalgRevurdering: BrevvalgRevurdering

    abstract override fun skalSendeVedtaksbrev(): Boolean
}
