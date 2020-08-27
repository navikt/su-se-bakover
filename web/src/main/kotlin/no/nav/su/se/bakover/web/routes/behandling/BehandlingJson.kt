package no.nav.su.se.bakover.web.routes.behandling

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.BehandlingDto
import no.nav.su.se.bakover.domain.dto.DtoConvertable
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.routes.behandling.UbetalingJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknad.SøknadJson
import no.nav.su.se.bakover.web.routes.søknad.toJson
import no.nav.su.se.bakover.web.routes.vilkårsvurdering.VilkårsvurderingJson
import no.nav.su.se.bakover.web.routes.vilkårsvurdering.toJson
import java.time.format.DateTimeFormatter
import java.util.UUID

internal data class BehandlingJson(
    val id: String,
    val vilkårsvurderinger: VilkårsvurderingJson,
    val søknad: SøknadJson,
    val beregning: BeregningJson?,
    val status: String,
    val utbetaling: UbetalingJson?,
    val opprettet: String,
    val attestant: String?,
    val sakId: UUID
)

internal fun BehandlingDto.toJson() = BehandlingJson(
    id = id.toString(),
    vilkårsvurderinger = vilkårsvurderinger.toJson(),
    søknad = søknad.toJson(),
    beregning = beregning?.toJson(),
    status = status.toString(),
    utbetaling = utbetaling?.toJson(),
    opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
    attestant = attestant?.id,
    sakId = sakId
)

internal fun HttpStatusCode.jsonBody(dtoConvertable: DtoConvertable<BehandlingDto>) =
    Resultat.json(this, serialize(dtoConvertable.toDto().toJson()))
