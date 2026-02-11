package no.nav.su.se.bakover.web.routes.regulering.json

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.regulering.AvsluttetRegulering
import no.nav.su.se.bakover.domain.regulering.IverksattRegulering
import no.nav.su.se.bakover.domain.regulering.ManuellReguleringVisning
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.regulering.ReguleringUnderBehandling
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.web.routes.grunnlag.GrunnlagsdataOgVilkårsvurderingerJson
import no.nav.su.se.bakover.web.routes.grunnlag.toJson
import no.nav.su.se.bakover.web.routes.regulering.json.EksternSupplementReguleringJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.regulering.json.ÅrsakTilManuellReguleringJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.sak.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SimuleringJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SimuleringJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.BeregningJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.toJson
import vilkår.formue.domain.FormuegrenserFactory
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
    val årsakForManuell: List<ÅrsakTilManuellReguleringJson>,
    val supplement: EksternSupplementReguleringJson,
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
        BEREGNET,
        ATTESTERING,
        IVERKSATT,
        AVSLUTTET,
        ;

        override fun toString(): String {
            return this.name
        }
    }
}

internal fun Regulering.toJson(formuegrenserFactory: FormuegrenserFactory) = ReguleringJson(
    id = id.value,
    fnr = fnr.toString(),
    opprettet = opprettet,
    sakId = sakId,
    saksnummer = saksnummer,
    beregning = beregning?.toJson(),
    simulering = simulering?.toJson(),
    reguleringstype = when (reguleringstype) {
        is Reguleringstype.AUTOMATISK -> "AUTOMATISK"
        is Reguleringstype.MANUELL -> "MANUELL"
    },
    årsakForManuell = when (val type = reguleringstype) {
        Reguleringstype.AUTOMATISK -> emptyList()
        is Reguleringstype.MANUELL -> type.problemer.toJson()
    },
    supplement = this.eksternSupplementRegulering.toJson(),
    reguleringsstatus = when (this) {
        is AvsluttetRegulering -> ReguleringJson.Status.AVSLUTTET
        is IverksattRegulering -> ReguleringJson.Status.IVERKSATT
        is ReguleringUnderBehandling.OpprettetRegulering -> ReguleringJson.Status.OPPRETTET
        is ReguleringUnderBehandling.BeregnetRegulering -> ReguleringJson.Status.BEREGNET
        is ReguleringUnderBehandling.TilAttestering -> ReguleringJson.Status.ATTESTERING
    },
    erFerdigstilt = this.erFerdigstilt,
    periode = periode.toJson(),
    grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        formuegrenserFactory = formuegrenserFactory,
    ),
    saksbehandler = saksbehandler.navIdent,
    avsluttet = when (this) {
        is AvsluttetRegulering -> ReguleringJson.Avsluttet(this.avsluttetTidspunkt)
        is IverksattRegulering, is ReguleringUnderBehandling -> null
    },
    sakstype = sakstype.toJson(),
)

internal data class ManuellReguleringVisningJson(
    val gjeldendeVedtaksdata: GrunnlagsdataOgVilkårsvurderingerJson,
    val regulering: ReguleringJson,
)
internal fun ManuellReguleringVisning.toJson(formuegrenserFactory: FormuegrenserFactory) = ManuellReguleringVisningJson(
    gjeldendeVedtaksdata = gjeldendeVedtaksdata.toJson(formuegrenserFactory),
    regulering = regulering.toJson(formuegrenserFactory),
)
