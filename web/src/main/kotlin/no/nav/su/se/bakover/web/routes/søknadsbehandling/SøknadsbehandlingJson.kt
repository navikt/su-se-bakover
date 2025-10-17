package no.nav.su.se.bakover.web.routes.søknadsbehandling

import common.presentation.attestering.AttesteringJson
import no.nav.su.se.bakover.common.infrastructure.StønadsperiodeJson
import no.nav.su.se.bakover.web.routes.grunnlag.EksterneGrunnlagJson
import no.nav.su.se.bakover.web.routes.grunnlag.GrunnlagsdataOgVilkårsvurderingerJson
import no.nav.su.se.bakover.web.routes.søknad.SøknadJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.BeregningJson
import java.util.UUID

internal data class SøknadsbehandlingJson(
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
    val sakstype: String,
    val aldersvurdering: AldersvurderingJson?,
    val eksterneGrunnlag: EksterneGrunnlagJson,
    val omgjøringsårsak: String?,
    val omgjøringsgrunn: String?,
)
