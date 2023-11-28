package no.nav.su.se.bakover.web.routes.revurdering

import common.presentation.attestering.AttesteringJson
import common.presentation.attestering.AttesteringJson.Companion.toJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import no.nav.su.se.bakover.domain.revurdering.AvsluttetRevurdering
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.revurdering.brev.BrevvalgRevurdering
import no.nav.su.se.bakover.domain.revurdering.steg.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.steg.Vurderingstatus
import no.nav.su.se.bakover.web.routes.grunnlag.GrunnlagsdataOgVilkårsvurderingerJson
import no.nav.su.se.bakover.web.routes.sak.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SimuleringJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SimuleringJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.BeregningJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.toJson
import vilkår.formue.domain.FormuegrenserFactory
import java.time.format.DateTimeFormatter

internal sealed class RevurderingJson {
    abstract val id: String
    abstract val sakId: String
    abstract val status: RevurderingsStatus
    abstract val opprettet: String
    abstract val periode: PeriodeJson
    abstract val tilRevurdering: String
    abstract val saksbehandler: String
    abstract val årsak: String
    abstract val begrunnelse: String
    abstract val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerJson
    abstract val attesteringer: List<AttesteringJson>
    abstract val sakstype: String
    abstract val brevvalg: BrevvalgRevurderingJson
}

data class BrevvalgRevurderingJson(
    val valg: String,
    val fritekst: String?,
    val begrunnelse: String?,
    val bestemtAv: String,
)

fun BrevvalgRevurdering.toJson(): BrevvalgRevurderingJson {
    return when (this) {
        BrevvalgRevurdering.IkkeValgt -> {
            BrevvalgRevurderingJson(
                valg = "IKKE_VALGT",
                fritekst = null,
                begrunnelse = null,
                bestemtAv = "",
            )
        }

        is BrevvalgRevurdering.Valgt.IkkeSendBrev -> {
            BrevvalgRevurderingJson(
                valg = "IKKE_SEND",
                fritekst = null,
                begrunnelse = begrunnelse,
                bestemtAv = bestemtAv.toString(),
            )
        }

        is BrevvalgRevurdering.Valgt.SendBrev -> {
            BrevvalgRevurderingJson(
                valg = "SEND",
                fritekst = fritekst,
                begrunnelse = begrunnelse,
                bestemtAv = bestemtAv.toString(),
            )
        }
    }
}

internal enum class RevurderingsStatus {
    OPPRETTET,
    BEREGNET_INNVILGET,
    BEREGNET_OPPHØRT,
    SIMULERT_INNVILGET,
    SIMULERT_OPPHØRT,
    TIL_ATTESTERING_INNVILGET,
    TIL_ATTESTERING_OPPHØRT,
    IVERKSATT_INNVILGET,
    IVERKSATT_OPPHØRT,
    UNDERKJENT_INNVILGET,
    UNDERKJENT_OPPHØRT,
    AVSLUTTET,
    SIMULERT_STANS,
    AVSLUTTET_STANS,
    IVERKSATT_STANS,
    SIMULERT_GJENOPPTAK,
    AVSLUTTET_GJENOPPTAK,
    IVERKSATT_GJENOPPTAK,
}

internal data class OpprettetRevurderingJson(
    override val id: String,
    override val sakId: String,
    override val status: RevurderingsStatus,
    override val opprettet: String,
    override val periode: PeriodeJson,
    override val tilRevurdering: String,
    override val saksbehandler: String,
    override val årsak: String,
    override val begrunnelse: String,
    override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerJson,
    override val attesteringer: List<AttesteringJson>,
    val informasjonSomRevurderes: Map<Revurderingsteg, Vurderingstatus>,
    override val sakstype: String,
    override val brevvalg: BrevvalgRevurderingJson,
) : RevurderingJson()

internal data class BeregnetRevurderingJson(
    override val id: String,
    override val sakId: String,
    override val status: RevurderingsStatus,
    override val opprettet: String,
    override val periode: PeriodeJson,
    override val tilRevurdering: String,
    val beregning: BeregningJson,
    override val saksbehandler: String,
    override val årsak: String,
    override val begrunnelse: String,
    override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerJson,
    override val attesteringer: List<AttesteringJson>,
    override val sakstype: String,
    val informasjonSomRevurderes: Map<Revurderingsteg, Vurderingstatus>,
    override val brevvalg: BrevvalgRevurderingJson,
) : RevurderingJson()

internal data class SimulertRevurderingJson(
    override val id: String,
    override val sakId: String,
    override val opprettet: String,
    override val periode: PeriodeJson,
    override val tilRevurdering: String,
    override val saksbehandler: String,
    override val årsak: String,
    override val begrunnelse: String,
    override val status: RevurderingsStatus,
    override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerJson,
    override val attesteringer: List<AttesteringJson>,
    override val sakstype: String,
    val beregning: BeregningJson,
    val simulering: SimuleringJson,
    val informasjonSomRevurderes: Map<Revurderingsteg, Vurderingstatus>,
    val tilbakekrevingsbehandling: TilbakekrevingsbehandlingJson?,
    override val brevvalg: BrevvalgRevurderingJson,
) : RevurderingJson()

internal data class TilAttesteringJson(
    override val id: String,
    override val sakId: String,
    override val opprettet: String,
    override val periode: PeriodeJson,
    override val tilRevurdering: String,
    override val saksbehandler: String,
    override val årsak: String,
    override val begrunnelse: String,
    override val status: RevurderingsStatus,
    override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerJson,
    override val attesteringer: List<AttesteringJson>,
    override val sakstype: String,
    val simulering: SimuleringJson?,
    val beregning: BeregningJson,
    val informasjonSomRevurderes: Map<Revurderingsteg, Vurderingstatus>,
    val tilbakekrevingsbehandling: TilbakekrevingsbehandlingJson?,
    override val brevvalg: BrevvalgRevurderingJson,
) : RevurderingJson()

internal data class IverksattRevurderingJson(
    override val id: String,
    override val sakId: String,
    override val opprettet: String,
    override val periode: PeriodeJson,
    override val tilRevurdering: String,
    override val saksbehandler: String,
    override val årsak: String,
    override val begrunnelse: String,
    override val status: RevurderingsStatus,
    override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerJson,
    override val attesteringer: List<AttesteringJson>,
    override val sakstype: String,
    val beregning: BeregningJson,
    val simulering: SimuleringJson?,
    val informasjonSomRevurderes: Map<Revurderingsteg, Vurderingstatus>,
    val tilbakekrevingsbehandling: TilbakekrevingsbehandlingJson?,
    override val brevvalg: BrevvalgRevurderingJson,
) : RevurderingJson()

internal data class UnderkjentRevurderingJson(
    override val id: String,
    override val sakId: String,
    override val opprettet: String,
    override val status: RevurderingsStatus,
    override val periode: PeriodeJson,
    override val tilRevurdering: String,
    override val saksbehandler: String,
    override val årsak: String,
    override val begrunnelse: String,
    override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerJson,
    override val attesteringer: List<AttesteringJson>,
    override val sakstype: String,
    val beregning: BeregningJson,
    val simulering: SimuleringJson?,
    val informasjonSomRevurderes: Map<Revurderingsteg, Vurderingstatus>,
    val tilbakekrevingsbehandling: TilbakekrevingsbehandlingJson?,
    override val brevvalg: BrevvalgRevurderingJson,
) : RevurderingJson()

internal data class AvsluttetRevurderingJson(
    override val id: String,
    override val sakId: String,
    override val opprettet: String,
    override val periode: PeriodeJson,
    override val tilRevurdering: String,
    override val saksbehandler: String,
    override val årsak: String,
    override val begrunnelse: String,
    override val status: RevurderingsStatus,
    override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerJson,
    override val attesteringer: List<AttesteringJson>,
    override val sakstype: String,
    val beregning: BeregningJson?,
    val simulering: SimuleringJson?,
    val informasjonSomRevurderes: Map<Revurderingsteg, Vurderingstatus>,
    val avsluttetTidspunkt: String,
    override val brevvalg: BrevvalgRevurderingJson,
) : RevurderingJson()

internal data class StansAvUtbetalingJson(
    override val id: String,
    override val sakId: String,
    override val opprettet: String,
    override val periode: PeriodeJson,
    override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerJson,
    override val tilRevurdering: String,
    override val saksbehandler: String,
    override val årsak: String,
    override val begrunnelse: String,
    override val status: RevurderingsStatus,
    override val attesteringer: List<AttesteringJson>,
    override val sakstype: String,
    val simulering: SimuleringJson,
    val avsluttetTidspunkt: String? = null,
    override val brevvalg: BrevvalgRevurderingJson,
) : RevurderingJson()

internal data class GjenopptakAvYtelseJson(
    override val id: String,
    override val sakId: String,
    override val opprettet: String,
    override val status: RevurderingsStatus,
    override val periode: PeriodeJson,
    override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerJson,
    override val tilRevurdering: String,
    override val saksbehandler: String,
    override val årsak: String,
    override val begrunnelse: String,
    override val attesteringer: List<AttesteringJson>,
    override val sakstype: String,
    val simulering: SimuleringJson,
    val avsluttetTidspunkt: String? = null,
    override val brevvalg: BrevvalgRevurderingJson,
) : RevurderingJson()

internal fun Revurdering.toJson(formuegrenserFactory: FormuegrenserFactory): RevurderingJson {
    val formatertOpprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet)
    return when (this) {
        is OpprettetRevurdering -> OpprettetRevurderingJson(
            id = id.toString(),
            sakId = sakId.toString(),
            opprettet = formatertOpprettet,
            status = InstansTilStatusMapper(this).status,
            periode = periode.toJson(),
            tilRevurdering = tilRevurdering.toString(),
            saksbehandler = saksbehandler.toString(),
            årsak = revurderingsårsak.årsak.toString(),
            begrunnelse = revurderingsårsak.begrunnelse.toString(),
            grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                formuegrenserFactory = formuegrenserFactory,
            ),
            informasjonSomRevurderes = informasjonSomRevurderes,
            attesteringer = attesteringer.toJson(),
            sakstype = sakstype.toJson(),
            brevvalg = brevvalgRevurdering.toJson(),
        )

        is SimulertRevurdering -> SimulertRevurderingJson(
            id = id.toString(),
            sakId = sakId.toString(),
            opprettet = formatertOpprettet,
            periode = periode.toJson(),
            tilRevurdering = tilRevurdering.toString(),
            beregning = beregning.toJson(),
            saksbehandler = saksbehandler.toString(),
            årsak = revurderingsårsak.årsak.toString(),
            begrunnelse = revurderingsårsak.begrunnelse.toString(),
            status = InstansTilStatusMapper(this).status,
            simulering = simulering.toJson(),
            grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                formuegrenserFactory = formuegrenserFactory,
            ),
            informasjonSomRevurderes = informasjonSomRevurderes,
            attesteringer = attesteringer.toJson(),
            sakstype = sakstype.toJson(),
            tilbakekrevingsbehandling = tilbakekrevingsbehandling.toJson(),
            brevvalg = brevvalgRevurdering.toJson(),
        )

        is RevurderingTilAttestering -> TilAttesteringJson(
            id = id.toString(),
            sakId = sakId.toString(),
            opprettet = formatertOpprettet,
            periode = periode.toJson(),
            tilRevurdering = tilRevurdering.toString(),
            beregning = beregning.toJson(),
            saksbehandler = saksbehandler.toString(),
            årsak = revurderingsårsak.årsak.toString(),
            begrunnelse = revurderingsårsak.begrunnelse.toString(),
            status = InstansTilStatusMapper(this).status,
            simulering = when (this) {
                is RevurderingTilAttestering.Innvilget -> simulering.toJson()
                is RevurderingTilAttestering.Opphørt -> simulering.toJson()
            },
            grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                formuegrenserFactory = formuegrenserFactory,
            ),
            informasjonSomRevurderes = informasjonSomRevurderes,
            attesteringer = attesteringer.toJson(),
            sakstype = sakstype.toJson(),
            tilbakekrevingsbehandling = when (this) {
                is RevurderingTilAttestering.Innvilget -> tilbakekrevingsbehandling.toJson()
                is RevurderingTilAttestering.Opphørt -> tilbakekrevingsbehandling.toJson()
            },
            brevvalg = brevvalgRevurdering.toJson(),
        )

        is IverksattRevurdering -> IverksattRevurderingJson(
            id = id.toString(),
            sakId = sakId.toString(),
            opprettet = formatertOpprettet,
            periode = periode.toJson(),
            tilRevurdering = tilRevurdering.toString(),
            beregning = beregning.toJson(),
            saksbehandler = saksbehandler.toString(),
            årsak = revurderingsårsak.årsak.toString(),
            begrunnelse = revurderingsårsak.begrunnelse.toString(),
            status = InstansTilStatusMapper(this).status,
            simulering = when (this) {
                is IverksattRevurdering.Innvilget -> simulering.toJson()
                is IverksattRevurdering.Opphørt -> simulering.toJson()
            },
            grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                formuegrenserFactory = formuegrenserFactory,
            ),
            informasjonSomRevurderes = informasjonSomRevurderes,
            attesteringer = attesteringer.toJson(),
            sakstype = sakstype.toJson(),
            tilbakekrevingsbehandling = when (this) {
                is IverksattRevurdering.Innvilget -> tilbakekrevingsbehandling.toJson()
                is IverksattRevurdering.Opphørt -> tilbakekrevingsbehandling.toJson()
            },
            brevvalg = brevvalgRevurdering.toJson(),
        )

        is UnderkjentRevurdering -> UnderkjentRevurderingJson(
            id = id.toString(),
            sakId = sakId.toString(),
            opprettet = formatertOpprettet,
            periode = periode.toJson(),
            tilRevurdering = tilRevurdering.toString(),
            beregning = beregning.toJson(),
            saksbehandler = saksbehandler.toString(),
            årsak = revurderingsårsak.årsak.toString(),
            begrunnelse = revurderingsårsak.begrunnelse.toString(),
            status = InstansTilStatusMapper(this).status,
            simulering = when (this) {
                is UnderkjentRevurdering.Innvilget -> simulering.toJson()
                is UnderkjentRevurdering.Opphørt -> simulering.toJson()
            },
            grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                formuegrenserFactory = formuegrenserFactory,
            ),
            informasjonSomRevurderes = informasjonSomRevurderes,
            attesteringer = attesteringer.toJson(),
            sakstype = sakstype.toJson(),
            tilbakekrevingsbehandling = when (this) {
                is UnderkjentRevurdering.Innvilget -> tilbakekrevingsbehandling.toJson()
                is UnderkjentRevurdering.Opphørt -> tilbakekrevingsbehandling.toJson()
            },
            brevvalg = brevvalgRevurdering.toJson(),
        )

        is BeregnetRevurdering -> BeregnetRevurderingJson(
            id = id.toString(),
            sakId = sakId.toString(),
            opprettet = formatertOpprettet,
            periode = periode.toJson(),
            tilRevurdering = tilRevurdering.toString(),
            beregning = beregning.toJson(),
            saksbehandler = saksbehandler.toString(),
            årsak = revurderingsårsak.årsak.toString(),
            begrunnelse = revurderingsårsak.begrunnelse.toString(),
            status = InstansTilStatusMapper(this).status,
            grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                formuegrenserFactory = formuegrenserFactory,
            ),
            informasjonSomRevurderes = informasjonSomRevurderes,
            attesteringer = attesteringer.toJson(),
            sakstype = sakstype.toJson(),
            brevvalg = brevvalgRevurdering.toJson(),
        )

        is AvsluttetRevurdering -> AvsluttetRevurderingJson(
            id = id.toString(),
            sakId = sakId.toString(),
            opprettet = formatertOpprettet,
            periode = periode.toJson(),
            tilRevurdering = tilRevurdering.toString(),
            beregning = beregning?.toJson(),
            saksbehandler = saksbehandler.toString(),
            årsak = revurderingsårsak.årsak.toString(),
            begrunnelse = revurderingsårsak.begrunnelse.toString(),
            status = InstansTilStatusMapper(this).status,
            grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                formuegrenserFactory = formuegrenserFactory,
            ),
            informasjonSomRevurderes = informasjonSomRevurderes,
            simulering = simulering?.toJson(),
            attesteringer = attesteringer.toJson(),
            sakstype = sakstype.toJson(),
            avsluttetTidspunkt = DateTimeFormatter.ISO_INSTANT.format(avsluttetTidspunkt),
            brevvalg = brevvalgRevurdering.toJson(),
        )
    }
}

internal class InstansTilStatusMapper(revurdering: AbstraktRevurdering) {
    val status = when (revurdering) {
        is BeregnetRevurdering.Innvilget -> RevurderingsStatus.BEREGNET_INNVILGET
        is BeregnetRevurdering.Opphørt -> RevurderingsStatus.BEREGNET_OPPHØRT
        is IverksattRevurdering.Innvilget -> RevurderingsStatus.IVERKSATT_INNVILGET
        is IverksattRevurdering.Opphørt -> RevurderingsStatus.IVERKSATT_OPPHØRT
        is OpprettetRevurdering -> RevurderingsStatus.OPPRETTET
        is RevurderingTilAttestering.Innvilget -> RevurderingsStatus.TIL_ATTESTERING_INNVILGET
        is RevurderingTilAttestering.Opphørt -> RevurderingsStatus.TIL_ATTESTERING_OPPHØRT
        is SimulertRevurdering.Innvilget -> RevurderingsStatus.SIMULERT_INNVILGET
        is SimulertRevurdering.Opphørt -> RevurderingsStatus.SIMULERT_OPPHØRT
        is UnderkjentRevurdering.Innvilget -> RevurderingsStatus.UNDERKJENT_INNVILGET
        is UnderkjentRevurdering.Opphørt -> RevurderingsStatus.UNDERKJENT_OPPHØRT
        is StansAvYtelseRevurdering.IverksattStansAvYtelse -> RevurderingsStatus.IVERKSATT_STANS
        is StansAvYtelseRevurdering.SimulertStansAvYtelse -> RevurderingsStatus.SIMULERT_STANS
        is GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse -> RevurderingsStatus.IVERKSATT_GJENOPPTAK
        is GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse -> RevurderingsStatus.SIMULERT_GJENOPPTAK
        is AvsluttetRevurdering -> RevurderingsStatus.AVSLUTTET
        is GjenopptaYtelseRevurdering.AvsluttetGjenoppta -> RevurderingsStatus.AVSLUTTET_GJENOPPTAK
        is StansAvYtelseRevurdering.AvsluttetStansAvYtelse -> RevurderingsStatus.AVSLUTTET_STANS
    }
}

internal fun AbstraktRevurdering.toJson(formuegrenserFactory: FormuegrenserFactory): RevurderingJson {
    return when (this) {
        is Revurdering -> this.toJson(formuegrenserFactory)
        is StansAvYtelseRevurdering -> this.toJson(formuegrenserFactory)
        is GjenopptaYtelseRevurdering -> this.toJson(formuegrenserFactory)
    }
}

internal fun StansAvYtelseRevurdering.toJson(formuegrenserFactory: FormuegrenserFactory): RevurderingJson {
    val formatertOpprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet)
    return when (this) {
        is StansAvYtelseRevurdering.IverksattStansAvYtelse -> StansAvUtbetalingJson(
            id = id.toString(),
            sakId = sakId.toString(),
            opprettet = formatertOpprettet,
            periode = periode.toJson(),
            grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                formuegrenserFactory = formuegrenserFactory,
            ),
            tilRevurdering = tilRevurdering.toString(),
            saksbehandler = saksbehandler.toString(),
            årsak = revurderingsårsak.årsak.toString(),
            begrunnelse = revurderingsårsak.begrunnelse.toString(),
            status = InstansTilStatusMapper(this).status,
            simulering = simulering.toJson(),
            attesteringer = attesteringer.toJson(),
            sakstype = sakstype.toJson(),
            brevvalg = brevvalgRevurdering.toJson(),
        )

        is StansAvYtelseRevurdering.SimulertStansAvYtelse -> {
            StansAvUtbetalingJson(
                id = id.toString(),
                sakId = sakId.toString(),
                opprettet = formatertOpprettet,
                periode = periode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    formuegrenserFactory = formuegrenserFactory,
                ),
                tilRevurdering = tilRevurdering.toString(),
                saksbehandler = saksbehandler.toString(),
                årsak = revurderingsårsak.årsak.toString(),
                begrunnelse = revurderingsårsak.begrunnelse.toString(),
                status = InstansTilStatusMapper(this).status,
                simulering = simulering.toJson(),
                attesteringer = emptyList(),
                sakstype = sakstype.toJson(),
                brevvalg = brevvalgRevurdering.toJson(),
            )
        }

        is StansAvYtelseRevurdering.AvsluttetStansAvYtelse -> StansAvUtbetalingJson(
            id = id.toString(),
            sakId = sakId.toString(),
            opprettet = formatertOpprettet,
            periode = periode.toJson(),
            grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                formuegrenserFactory = formuegrenserFactory,
            ),
            tilRevurdering = tilRevurdering.toString(),
            saksbehandler = saksbehandler.toString(),
            årsak = revurderingsårsak.årsak.toString(),
            begrunnelse = revurderingsårsak.begrunnelse.toString(),
            status = InstansTilStatusMapper(this).status,
            simulering = simulering.toJson(),
            attesteringer = emptyList(),
            sakstype = sakstype.toJson(),
            avsluttetTidspunkt = DateTimeFormatter.ISO_INSTANT.format(avsluttetTidspunkt),
            brevvalg = brevvalgRevurdering.toJson(),
        )
    }
}

internal fun GjenopptaYtelseRevurdering.toJson(formuegrenserFactory: FormuegrenserFactory): RevurderingJson {
    val formatertOpprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet)
    return when (this) {
        is GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse -> {
            GjenopptakAvYtelseJson(
                id = id.toString(),
                sakId = sakId.toString(),
                opprettet = formatertOpprettet,
                periode = periode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    formuegrenserFactory = formuegrenserFactory,
                ),
                tilRevurdering = tilRevurdering.toString(),
                saksbehandler = saksbehandler.toString(),
                årsak = revurderingsårsak.årsak.toString(),
                begrunnelse = revurderingsårsak.begrunnelse.toString(),
                status = InstansTilStatusMapper(this).status,
                simulering = simulering.toJson(),
                attesteringer = attesteringer.toJson(),
                sakstype = sakstype.toJson(),
                brevvalg = brevvalgRevurdering.toJson(),
            )
        }

        is GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse -> GjenopptakAvYtelseJson(
            id = id.toString(),
            sakId = sakId.toString(),
            opprettet = formatertOpprettet,
            periode = periode.toJson(),
            grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                formuegrenserFactory = formuegrenserFactory,
            ),
            tilRevurdering = tilRevurdering.toString(),
            saksbehandler = saksbehandler.toString(),
            årsak = revurderingsårsak.årsak.toString(),
            begrunnelse = revurderingsårsak.begrunnelse.toString(),
            status = InstansTilStatusMapper(this).status,
            simulering = simulering.toJson(),
            attesteringer = emptyList(),
            sakstype = sakstype.toJson(),
            brevvalg = brevvalgRevurdering.toJson(),
        )

        is GjenopptaYtelseRevurdering.AvsluttetGjenoppta -> GjenopptakAvYtelseJson(
            id = id.toString(),
            sakId = sakId.toString(),
            opprettet = formatertOpprettet,
            periode = periode.toJson(),
            grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                formuegrenserFactory = formuegrenserFactory,
            ),
            tilRevurdering = tilRevurdering.toString(),
            saksbehandler = saksbehandler.toString(),
            årsak = revurderingsårsak.årsak.toString(),
            begrunnelse = revurderingsårsak.begrunnelse.toString(),
            status = InstansTilStatusMapper(this).status,
            simulering = simulering.toJson(),
            attesteringer = emptyList(),
            sakstype = sakstype.toJson(),
            avsluttetTidspunkt = DateTimeFormatter.ISO_INSTANT.format(avsluttetTidspunkt),
            brevvalg = brevvalgRevurdering.toJson(),
        )
    }
}
