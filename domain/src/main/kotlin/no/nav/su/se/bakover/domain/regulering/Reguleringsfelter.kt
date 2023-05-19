package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.grunnlag.EksterneGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.grunnlag.StøtterIkkeHentingAvEksternGrunnlag
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering

interface Reguleringsfelter : Behandling {
    override val beregning: Beregning?
    override val simulering: Simulering?
    override val eksterneGrunnlag: EksterneGrunnlag
        get() = StøtterIkkeHentingAvEksternGrunnlag
    val saksbehandler: NavIdentBruker.Saksbehandler
    val reguleringstype: Reguleringstype
    val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger
}
