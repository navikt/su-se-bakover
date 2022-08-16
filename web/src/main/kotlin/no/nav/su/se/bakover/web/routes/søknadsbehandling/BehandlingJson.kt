package no.nav.su.se.bakover.web.routes.søknadsbehandling

import no.nav.su.se.bakover.web.routes.grunnlag.GrunnlagsdataOgVilkårsvurderingerJson
import no.nav.su.se.bakover.web.routes.søknad.SøknadJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.BeregningJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.StønadsperiodeJson
import java.util.UUID

internal data class BehandlingJson(
    val id: String,
    val søknad: SøknadJson,
    val beregning: BeregningJson?,
    val status: String,
    val simulering: SimuleringJson?,
    val opprettet: String,
    val attesteringer: List<AttesteringJson>,
    val saksbehandler: String?,
    val fritekstTilBrev: String,
    val sakId: UUID,
    val stønadsperiode: StønadsperiodeJson?,
    val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerJson,
    val erLukket: Boolean,
    val simuleringForAvkortingsvarsel: SimuleringJson?,
    val sakstype: String,
)
