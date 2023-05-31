package no.nav.su.se.bakover.web.routes.regulering

import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.regulering.AvsluttetRegulering
import no.nav.su.se.bakover.domain.regulering.IverksattRegulering
import no.nav.su.se.bakover.domain.regulering.OpprettetRegulering
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.regulering.ÅrsakTilManuellRegulering
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.web.routes.grunnlag.GrunnlagsdataOgVilkårsvurderingerJson
import no.nav.su.se.bakover.web.routes.sak.toJson
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
    val sakstype: String,
) {
    data class Avsluttet(val tidspunkt: Tidspunkt)
    enum class Status {
        OPPRETTET,
        IVERKSATT,
        AVSLUTTET,
        ;

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
        is AvsluttetRegulering -> ReguleringJson.Status.AVSLUTTET
        is IverksattRegulering -> ReguleringJson.Status.IVERKSATT
        is OpprettetRegulering -> ReguleringJson.Status.OPPRETTET
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
        is AvsluttetRegulering -> ReguleringJson.Avsluttet(this.avsluttetTidspunkt)
        is IverksattRegulering, is OpprettetRegulering -> null
    },
    sakstype = sakstype.toJson(),
)

internal fun Set<ÅrsakTilManuellRegulering>.toJson(): Set<String> {
    return map { it.name }.toSet()
}
