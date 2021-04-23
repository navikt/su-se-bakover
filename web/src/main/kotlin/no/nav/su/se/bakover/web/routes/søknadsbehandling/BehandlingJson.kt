package no.nav.su.se.bakover.web.routes.søknadsbehandling

import no.nav.su.se.bakover.web.routes.grunnlag.GrunnlagsdataJson
import no.nav.su.se.bakover.web.routes.hendelse.HendelseJson
import no.nav.su.se.bakover.web.routes.søknad.SøknadJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.BeregningJson
import java.util.UUID

internal data class BehandlingJson(
    val id: String,
    val behandlingsinformasjon: BehandlingsinformasjonJson,
    val søknad: SøknadJson,
    val beregning: BeregningJson?,
    val status: String,
    val simulering: SimuleringJson?,
    val opprettet: String,
    val attestering: AttesteringJson?,
    val saksbehandler: String?,
    val sakId: UUID,
    val hendelser: List<HendelseJson>? = emptyList(),
    val stønadsperiode: ValgtStønadsperiodeJson?,
    val grunnlag: GrunnlagsdataJson,
)
