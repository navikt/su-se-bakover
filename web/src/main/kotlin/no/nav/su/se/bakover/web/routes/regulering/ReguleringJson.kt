package no.nav.su.se.bakover.web.routes.regulering

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.PeriodeJson
import no.nav.su.se.bakover.common.periode.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.regulering.ReguleringMerknad
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.regulering.ÅrsakTilManuellRegulering
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.web.routes.grunnlag.GrunnlagsdataOgVilkårsvurderingerJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SimuleringJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SimuleringJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.BeregningJson
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
    val reguleringsstatus: Status,
    val periode: PeriodeJson,
    val erFerdigstilt: Boolean,
    val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerJson,
    val saksbehandler: String,
    val avsluttet: Avsluttet?,
) {
    data class Avsluttet(val tidspunkt: Tidspunkt)
    enum class Status {
        OPPRETTET,
        IVERKSATT,
        AVSLUTTET;

        override fun toString(): String {
            return this.name
        }
    }
}

internal fun Regulering.toJson(satsFactory: SatsFactory) = ReguleringJson(
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
    reguleringsstatus = when (this) {
        is Regulering.AvsluttetRegulering -> ReguleringJson.Status.AVSLUTTET
        is Regulering.IverksattRegulering -> ReguleringJson.Status.IVERKSATT
        is Regulering.OpprettetRegulering -> ReguleringJson.Status.OPPRETTET
    },
    erFerdigstilt = this.erFerdigstilt,
    periode = periode.toJson(),
    grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        satsFactory = satsFactory,
    ),
    saksbehandler = saksbehandler.navIdent,
    avsluttet = when (this) {
        is Regulering.AvsluttetRegulering -> ReguleringJson.Avsluttet(this.avsluttetTidspunkt)
        is Regulering.IverksattRegulering, is Regulering.OpprettetRegulering -> null
    },
)

internal fun Set<ÅrsakTilManuellRegulering>.toJson(): Set<String> {
    return map { it.name }.toSet()
}

internal fun Pair<Regulering, List<ReguleringMerknad>>.toJson(satsFactory: SatsFactory) = ReguleringStatusJson(this.first.toJson(satsFactory), this.second.map { it.name })
internal data class ReguleringStatusJson(val regulering: ReguleringJson, val merknad: List<String>)
