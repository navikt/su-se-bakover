package no.nav.su.se.bakover.web.routes.revurdering

import no.nav.su.se.bakover.common.infrastructure.web.periode.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.web.periode.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import no.nav.su.se.bakover.domain.revurdering.AvsluttetRevurdering
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.revurdering.Vurderingstatus
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.web.routes.grunnlag.GrunnlagsdataOgVilkårsvurderingerJson
import no.nav.su.se.bakover.web.routes.sak.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.AttesteringJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.AttesteringJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SimuleringJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SimuleringJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.BeregningJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.toJson
import java.time.format.DateTimeFormatter

internal sealed class RevurderingJson {
    abstract val id: String
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
}

internal enum class RevurderingsStatus {
    OPPRETTET,
    BEREGNET_INNVILGET,
    BEREGNET_OPPHØRT,
    BEREGNET_INGEN_ENDRING,
    SIMULERT_INNVILGET,
    SIMULERT_OPPHØRT,
    TIL_ATTESTERING_INNVILGET,
    TIL_ATTESTERING_OPPHØRT,
    TIL_ATTESTERING_INGEN_ENDRING,
    IVERKSATT_INNVILGET,
    IVERKSATT_OPPHØRT,
    IVERKSATT_INGEN_ENDRING,
    UNDERKJENT_INNVILGET,
    UNDERKJENT_OPPHØRT,
    UNDERKJENT_INGEN_ENDRING,
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
    val fritekstTilBrev: String,
    override val sakstype: String,
) : RevurderingJson()

internal data class BeregnetRevurderingJson(
    override val id: String,
    override val status: RevurderingsStatus,
    override val opprettet: String,
    override val periode: PeriodeJson,
    override val tilRevurdering: String,
    val beregning: BeregningJson,
    override val saksbehandler: String,
    val fritekstTilBrev: String,
    override val årsak: String,
    override val begrunnelse: String,
    override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerJson,
    override val attesteringer: List<AttesteringJson>,
    override val sakstype: String,
    val informasjonSomRevurderes: Map<Revurderingsteg, Vurderingstatus>,
) : RevurderingJson()

internal data class SimulertRevurderingJson(
    override val id: String,
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
    val fritekstTilBrev: String,
    val simulering: SimuleringJson,
    val informasjonSomRevurderes: Map<Revurderingsteg, Vurderingstatus>,
    val simuleringForAvkortingsvarsel: SimuleringJson?,
    val tilbakekrevingsbehandling: TilbakekrevingsbehandlingJson?,
) : RevurderingJson()

internal data class TilAttesteringJson(
    override val id: String,
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
    val fritekstTilBrev: String,
    val skalFøreTilBrevutsending: Boolean,
    val informasjonSomRevurderes: Map<Revurderingsteg, Vurderingstatus>,
    val simuleringForAvkortingsvarsel: SimuleringJson?,
    val tilbakekrevingsbehandling: TilbakekrevingsbehandlingJson?,
) : RevurderingJson()

internal data class IverksattRevurderingJson(
    override val id: String,
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
    val fritekstTilBrev: String,
    val skalFøreTilBrevutsending: Boolean,
    val informasjonSomRevurderes: Map<Revurderingsteg, Vurderingstatus>,
    val simuleringForAvkortingsvarsel: SimuleringJson?,
    val tilbakekrevingsbehandling: TilbakekrevingsbehandlingJson?,
) : RevurderingJson()

internal data class UnderkjentRevurderingJson(
    override val id: String,
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
    val fritekstTilBrev: String,
    val skalFøreTilBrevutsending: Boolean,
    val informasjonSomRevurderes: Map<Revurderingsteg, Vurderingstatus>,
    val simuleringForAvkortingsvarsel: SimuleringJson?,
    val tilbakekrevingsbehandling: TilbakekrevingsbehandlingJson?,
) : RevurderingJson()

internal data class AvsluttetRevurderingJson(
    override val id: String,
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
    val fritekstTilBrev: String,
    val informasjonSomRevurderes: Map<Revurderingsteg, Vurderingstatus>,
    val avsluttetTidspunkt: String,
) : RevurderingJson()

internal data class StansAvUtbetalingJson(
    override val id: String,
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
) : RevurderingJson()

internal data class GjenopptakAvYtelseJson(
    override val id: String,
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
) : RevurderingJson()

internal fun Revurdering.toJson(satsFactory: SatsFactory): RevurderingJson = when (this) {
    is OpprettetRevurdering -> OpprettetRevurderingJson(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        status = InstansTilStatusMapper(this).status,
        periode = periode.toJson(),
        tilRevurdering = tilRevurdering.toString(),
        saksbehandler = saksbehandler.toString(),
        fritekstTilBrev = fritekstTilBrev,
        årsak = revurderingsårsak.årsak.toString(),
        begrunnelse = revurderingsårsak.begrunnelse.toString(),
        grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            satsFactory = satsFactory,
        ),
        informasjonSomRevurderes = informasjonSomRevurderes,
        attesteringer = attesteringer.toJson(),
        sakstype = sakstype.toJson(),
    )
    is SimulertRevurdering -> SimulertRevurderingJson(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        periode = periode.toJson(),
        tilRevurdering = tilRevurdering.toString(),
        beregning = beregning.toJson(),
        saksbehandler = saksbehandler.toString(),
        fritekstTilBrev = fritekstTilBrev,
        årsak = revurderingsårsak.årsak.toString(),
        begrunnelse = revurderingsårsak.begrunnelse.toString(),
        status = InstansTilStatusMapper(this).status,
        simulering = simulering.toJson(),
        grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            satsFactory = satsFactory,
        ),
        informasjonSomRevurderes = informasjonSomRevurderes,
        attesteringer = attesteringer.toJson(),
        sakstype = sakstype.toJson(),
        simuleringForAvkortingsvarsel = when (this) {
            is SimulertRevurdering.Innvilget -> null
            is SimulertRevurdering.Opphørt -> avkorting.toJson()
        },
        tilbakekrevingsbehandling = tilbakekrevingsbehandling.toJson(),
    )
    is RevurderingTilAttestering -> TilAttesteringJson(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        periode = periode.toJson(),
        tilRevurdering = tilRevurdering.toString(),
        beregning = beregning.toJson(),
        saksbehandler = saksbehandler.toString(),
        fritekstTilBrev = fritekstTilBrev,
        skalFøreTilBrevutsending = when (this) {
            is RevurderingTilAttestering.IngenEndring -> skalFøreTilUtsendingAvVedtaksbrev
            is RevurderingTilAttestering.Innvilget -> true
            is RevurderingTilAttestering.Opphørt -> true
        },
        årsak = revurderingsårsak.årsak.toString(),
        begrunnelse = revurderingsårsak.begrunnelse.toString(),
        status = InstansTilStatusMapper(this).status,
        simulering = when (this) {
            is RevurderingTilAttestering.IngenEndring -> null
            is RevurderingTilAttestering.Innvilget -> simulering.toJson()
            is RevurderingTilAttestering.Opphørt -> simulering.toJson()
        },
        grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            satsFactory = satsFactory,
        ),
        informasjonSomRevurderes = informasjonSomRevurderes,
        attesteringer = attesteringer.toJson(),
        sakstype = sakstype.toJson(),
        simuleringForAvkortingsvarsel = when (this) {
            is RevurderingTilAttestering.IngenEndring -> null
            is RevurderingTilAttestering.Innvilget -> null
            is RevurderingTilAttestering.Opphørt -> avkorting.toJson()
        },
        tilbakekrevingsbehandling = when (this) {
            is RevurderingTilAttestering.IngenEndring -> null
            is RevurderingTilAttestering.Innvilget -> tilbakekrevingsbehandling.toJson()
            is RevurderingTilAttestering.Opphørt -> tilbakekrevingsbehandling.toJson()
        },
    )
    is IverksattRevurdering -> IverksattRevurderingJson(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        periode = periode.toJson(),
        tilRevurdering = tilRevurdering.toString(),
        beregning = beregning.toJson(),
        saksbehandler = saksbehandler.toString(),
        fritekstTilBrev = fritekstTilBrev,
        skalFøreTilBrevutsending = when (this) {
            is IverksattRevurdering.IngenEndring -> skalFøreTilUtsendingAvVedtaksbrev
            is IverksattRevurdering.Innvilget -> true
            is IverksattRevurdering.Opphørt -> true
        },
        årsak = revurderingsårsak.årsak.toString(),
        begrunnelse = revurderingsårsak.begrunnelse.toString(),
        status = InstansTilStatusMapper(this).status,
        simulering = when (this) {
            is IverksattRevurdering.IngenEndring -> null
            is IverksattRevurdering.Innvilget -> simulering.toJson()
            is IverksattRevurdering.Opphørt -> simulering.toJson()
        },
        grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            satsFactory = satsFactory,
        ),
        informasjonSomRevurderes = informasjonSomRevurderes,
        attesteringer = attesteringer.toJson(),
        sakstype = sakstype.toJson(),
        simuleringForAvkortingsvarsel = when (this) {
            is IverksattRevurdering.IngenEndring -> null
            is IverksattRevurdering.Innvilget -> null
            is IverksattRevurdering.Opphørt -> avkorting.toJson()
        },
        tilbakekrevingsbehandling = when (this) {
            is IverksattRevurdering.IngenEndring -> null
            is IverksattRevurdering.Innvilget -> tilbakekrevingsbehandling.toJson()
            is IverksattRevurdering.Opphørt -> tilbakekrevingsbehandling.toJson()
        },
    )
    is UnderkjentRevurdering -> UnderkjentRevurderingJson(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        periode = periode.toJson(),
        tilRevurdering = tilRevurdering.toString(),
        beregning = beregning.toJson(),
        saksbehandler = saksbehandler.toString(),
        fritekstTilBrev = fritekstTilBrev,
        skalFøreTilBrevutsending = when (this) {
            is UnderkjentRevurdering.IngenEndring -> skalFøreTilUtsendingAvVedtaksbrev
            is UnderkjentRevurdering.Innvilget -> true
            is UnderkjentRevurdering.Opphørt -> true
        },
        årsak = revurderingsårsak.årsak.toString(),
        begrunnelse = revurderingsårsak.begrunnelse.toString(),
        status = InstansTilStatusMapper(this).status,
        simulering = when (this) {
            is UnderkjentRevurdering.IngenEndring -> null
            is UnderkjentRevurdering.Innvilget -> simulering.toJson()
            is UnderkjentRevurdering.Opphørt -> simulering.toJson()
        },
        grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            satsFactory = satsFactory,
        ),
        informasjonSomRevurderes = informasjonSomRevurderes,
        attesteringer = attesteringer.toJson(),
        sakstype = sakstype.toJson(),
        simuleringForAvkortingsvarsel = when (this) {
            is UnderkjentRevurdering.IngenEndring -> null
            is UnderkjentRevurdering.Innvilget -> null
            is UnderkjentRevurdering.Opphørt -> avkorting.toJson()
        },
        tilbakekrevingsbehandling = when (this) {
            is UnderkjentRevurdering.IngenEndring -> null
            is UnderkjentRevurdering.Innvilget -> tilbakekrevingsbehandling.toJson()
            is UnderkjentRevurdering.Opphørt -> tilbakekrevingsbehandling.toJson()
        },
    )
    is BeregnetRevurdering -> BeregnetRevurderingJson(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        periode = periode.toJson(),
        tilRevurdering = tilRevurdering.toString(),
        beregning = beregning.toJson(),
        saksbehandler = saksbehandler.toString(),
        fritekstTilBrev = fritekstTilBrev,
        årsak = revurderingsårsak.årsak.toString(),
        begrunnelse = revurderingsårsak.begrunnelse.toString(),
        status = InstansTilStatusMapper(this).status,
        grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            satsFactory = satsFactory,
        ),
        informasjonSomRevurderes = informasjonSomRevurderes,
        attesteringer = attesteringer.toJson(),
        sakstype = sakstype.toJson(),
    )
    is AvsluttetRevurdering -> AvsluttetRevurderingJson(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        periode = periode.toJson(),
        tilRevurdering = tilRevurdering.toString(),
        beregning = beregning?.toJson(),
        saksbehandler = saksbehandler.toString(),
        fritekstTilBrev = fritekstTilBrev,
        årsak = revurderingsårsak.årsak.toString(),
        begrunnelse = revurderingsårsak.begrunnelse.toString(),
        status = InstansTilStatusMapper(this).status,
        grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            satsFactory = satsFactory,
        ),
        informasjonSomRevurderes = informasjonSomRevurderes,
        simulering = simulering?.toJson(),
        attesteringer = attesteringer.toJson(),
        sakstype = sakstype.toJson(),
        avsluttetTidspunkt = DateTimeFormatter.ISO_INSTANT.format(tidspunktAvsluttet),
    )
}

internal class InstansTilStatusMapper(revurdering: AbstraktRevurdering) {
    val status = when (revurdering) {
        is BeregnetRevurdering.IngenEndring -> RevurderingsStatus.BEREGNET_INGEN_ENDRING
        is BeregnetRevurdering.Innvilget -> RevurderingsStatus.BEREGNET_INNVILGET
        is BeregnetRevurdering.Opphørt -> RevurderingsStatus.BEREGNET_OPPHØRT
        is IverksattRevurdering.IngenEndring -> RevurderingsStatus.IVERKSATT_INGEN_ENDRING
        is IverksattRevurdering.Innvilget -> RevurderingsStatus.IVERKSATT_INNVILGET
        is IverksattRevurdering.Opphørt -> RevurderingsStatus.IVERKSATT_OPPHØRT
        is OpprettetRevurdering -> RevurderingsStatus.OPPRETTET
        is RevurderingTilAttestering.IngenEndring -> RevurderingsStatus.TIL_ATTESTERING_INGEN_ENDRING
        is RevurderingTilAttestering.Innvilget -> RevurderingsStatus.TIL_ATTESTERING_INNVILGET
        is RevurderingTilAttestering.Opphørt -> RevurderingsStatus.TIL_ATTESTERING_OPPHØRT
        is SimulertRevurdering.Innvilget -> RevurderingsStatus.SIMULERT_INNVILGET
        is SimulertRevurdering.Opphørt -> RevurderingsStatus.SIMULERT_OPPHØRT
        is UnderkjentRevurdering.IngenEndring -> RevurderingsStatus.UNDERKJENT_INGEN_ENDRING
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

internal fun AbstraktRevurdering.toJson(satsFactory: SatsFactory): RevurderingJson {
    return when (this) {
        is Revurdering -> this.toJson(satsFactory)
        is StansAvYtelseRevurdering -> this.toJson(satsFactory)
        is GjenopptaYtelseRevurdering -> this.toJson(satsFactory)
    }
}

internal fun StansAvYtelseRevurdering.toJson(satsFactory: SatsFactory): RevurderingJson {
    return when (this) {
        is StansAvYtelseRevurdering.IverksattStansAvYtelse -> StansAvUtbetalingJson(
            id = id.toString(),
            opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
            periode = periode.toJson(),
            grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                satsFactory = satsFactory,
            ),
            tilRevurdering = tilRevurdering.toString(),
            saksbehandler = saksbehandler.toString(),
            årsak = revurderingsårsak.årsak.toString(),
            begrunnelse = revurderingsårsak.begrunnelse.toString(),
            status = InstansTilStatusMapper(this).status,
            simulering = simulering.toJson(),
            attesteringer = attesteringer.toJson(),
            sakstype = sakstype.toJson(),
        )
        is StansAvYtelseRevurdering.SimulertStansAvYtelse -> {
            StansAvUtbetalingJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                periode = periode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    satsFactory = satsFactory,
                ),
                tilRevurdering = tilRevurdering.toString(),
                saksbehandler = saksbehandler.toString(),
                årsak = revurderingsårsak.årsak.toString(),
                begrunnelse = revurderingsårsak.begrunnelse.toString(),
                status = InstansTilStatusMapper(this).status,
                simulering = simulering.toJson(),
                attesteringer = emptyList(),
                sakstype = sakstype.toJson(),
            )
        }
        is StansAvYtelseRevurdering.AvsluttetStansAvYtelse -> StansAvUtbetalingJson(
            id = id.toString(),
            opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
            periode = periode.toJson(),
            grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                satsFactory = satsFactory,
            ),
            tilRevurdering = tilRevurdering.toString(),
            saksbehandler = saksbehandler.toString(),
            årsak = revurderingsårsak.årsak.toString(),
            begrunnelse = revurderingsårsak.begrunnelse.toString(),
            status = InstansTilStatusMapper(this).status,
            simulering = simulering.toJson(),
            attesteringer = emptyList(),
            sakstype = sakstype.toJson(),
            avsluttetTidspunkt = DateTimeFormatter.ISO_INSTANT.format(tidspunktAvsluttet),
        )
    }
}

internal fun GjenopptaYtelseRevurdering.toJson(satsFactory: SatsFactory): RevurderingJson {
    return when (this) {
        is GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse -> {
            GjenopptakAvYtelseJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                periode = periode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    satsFactory = satsFactory,
                ),
                tilRevurdering = tilRevurdering.toString(),
                saksbehandler = saksbehandler.toString(),
                årsak = revurderingsårsak.årsak.toString(),
                begrunnelse = revurderingsårsak.begrunnelse.toString(),
                status = InstansTilStatusMapper(this).status,
                simulering = simulering.toJson(),
                attesteringer = attesteringer.toJson(),
                sakstype = sakstype.toJson(),
            )
        }
        is GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse -> GjenopptakAvYtelseJson(
            id = id.toString(),
            opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
            periode = periode.toJson(),
            grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                satsFactory = satsFactory,
            ),
            tilRevurdering = tilRevurdering.toString(),
            saksbehandler = saksbehandler.toString(),
            årsak = revurderingsårsak.årsak.toString(),
            begrunnelse = revurderingsårsak.begrunnelse.toString(),
            status = InstansTilStatusMapper(this).status,
            simulering = simulering.toJson(),
            attesteringer = emptyList(),
            sakstype = sakstype.toJson(),
        )
        is GjenopptaYtelseRevurdering.AvsluttetGjenoppta -> GjenopptakAvYtelseJson(
            id = id.toString(),
            opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
            periode = periode.toJson(),
            grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                satsFactory = satsFactory,
            ),
            tilRevurdering = tilRevurdering.toString(),
            saksbehandler = saksbehandler.toString(),
            årsak = revurderingsårsak.årsak.toString(),
            begrunnelse = revurderingsårsak.begrunnelse.toString(),
            status = InstansTilStatusMapper(this).status,
            simulering = simulering.toJson(),
            attesteringer = emptyList(),
            sakstype = sakstype.toJson(),
            avsluttetTidspunkt = DateTimeFormatter.ISO_INSTANT.format(tidspunktAvsluttet),
        )
    }
}
