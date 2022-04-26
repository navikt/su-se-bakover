package no.nav.su.se.bakover.web.routes.regulering

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.regulering.ÅrsakTilManuellRegulering
import no.nav.su.se.bakover.web.routes.grunnlag.GrunnlagsdataOgVilkårsvurderingerJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SimuleringJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SimuleringJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.BeregningJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.toJson
import java.util.UUID

internal data class ReguleringJson(
    val id: UUID,
    val fnr: String,
    val opprettet: Tidspunkt,
    val beregning: BeregningJson?,
    val simulering: SimuleringJson?,
    val sakId: UUID,
    val saksnummer: Saksnummer,
    val reguleringstype: String,
    val årsakForManuell: Set<String>?,
    val periode: PeriodeJson,
    val erFerdigstilt: Boolean,
    val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerJson,
    val saksbehandler: String,
)

internal fun Regulering.toJson() = ReguleringJson(
    id = id,
    fnr = fnr.toString(),
    opprettet = opprettet,
    sakId = sakId,
    saksnummer = saksnummer,
    beregning = beregning?.toJson(),
    simulering = simulering?.toJson(),
    reguleringstype = reguleringstype.toString(),
    årsakForManuell = when (val type = reguleringstype) {
        Reguleringstype.AUTOMATISK -> null
        is Reguleringstype.MANUELL -> type.problemer.toJson()
    },
    erFerdigstilt = this.erFerdigstilt,
    periode = periode.toJson(),
    grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(grunnlagsdata, vilkårsvurderinger),
    saksbehandler = saksbehandler.navIdent,
)

internal fun Set<ÅrsakTilManuellRegulering>.toJson(): Set<String> {
    return map { it.name }.toSet()
}
