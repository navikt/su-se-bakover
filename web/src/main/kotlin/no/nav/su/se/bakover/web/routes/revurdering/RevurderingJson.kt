package no.nav.su.se.bakover.web.routes.revurdering

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.domain.grunnlag.throwIfMultiple
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.BeslutningEtterForhåndsvarsling
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
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
import no.nav.su.se.bakover.web.routes.grunnlag.GrunnlagsdataOgVilkårsvurderingerJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.AttesteringJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.AttesteringJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SimuleringJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SimuleringJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.BeregningJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.toJson
import no.nav.su.se.bakover.web.routes.vedtak.VedtakJson
import no.nav.su.se.bakover.web.routes.vedtak.toJson
import java.time.format.DateTimeFormatter

sealed class RevurderingJson

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
    SIMULERT_STANS,
    IVERKSATT_STANS,
    SIMULERT_GJENOPPTAK,
    IVERKSATT_GJENOPPTAK,
}

internal data class RevurdertBeregningJson(
    val revurdert: BeregningJson,
)

internal data class OpprettetRevurderingJson(
    val id: String,
    val opprettet: String,
    val periode: PeriodeJson,
    val tilRevurdering: VedtakJson,
    val saksbehandler: String,
    val fritekstTilBrev: String,
    val årsak: String,
    val begrunnelse: String,
    val forhåndsvarsel: ForhåndsvarselJson?,
    val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerJson,
    val informasjonSomRevurderes: Map<Revurderingsteg, Vurderingstatus>,
    val attesteringer: List<AttesteringJson>,
) : RevurderingJson() {
    @JsonInclude
    val status = RevurderingsStatus.OPPRETTET
}

internal data class BeregnetRevurderingJson(
    val id: String,
    val opprettet: String,
    val periode: PeriodeJson,
    val tilRevurdering: VedtakJson,
    val beregninger: RevurdertBeregningJson,
    val saksbehandler: String,
    val fritekstTilBrev: String,
    val årsak: String,
    val begrunnelse: String,
    val status: RevurderingsStatus,
    val forhåndsvarsel: ForhåndsvarselJson?,
    val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerJson,
    val informasjonSomRevurderes: Map<Revurderingsteg, Vurderingstatus>,
    val attesteringer: List<AttesteringJson>,
) : RevurderingJson()

internal data class SimulertRevurderingJson(
    val id: String,
    val opprettet: String,
    val periode: PeriodeJson,
    val tilRevurdering: VedtakJson,
    val beregninger: RevurdertBeregningJson,
    val saksbehandler: String,
    val fritekstTilBrev: String,
    val årsak: String,
    val begrunnelse: String,
    val status: RevurderingsStatus,
    val forhåndsvarsel: ForhåndsvarselJson?,
    val simulering: SimuleringJson,
    val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerJson,
    val informasjonSomRevurderes: Map<Revurderingsteg, Vurderingstatus>,
    val attesteringer: List<AttesteringJson>,
) : RevurderingJson()

internal data class TilAttesteringJson(
    val id: String,
    val opprettet: String,
    val periode: PeriodeJson,
    val tilRevurdering: VedtakJson,
    val beregninger: RevurdertBeregningJson,
    val saksbehandler: String,
    val fritekstTilBrev: String,
    val skalFøreTilBrevutsending: Boolean,
    val årsak: String,
    val begrunnelse: String,
    val status: RevurderingsStatus,
    val forhåndsvarsel: ForhåndsvarselJson?,
    val simulering: SimuleringJson?,
    val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerJson,
    val informasjonSomRevurderes: Map<Revurderingsteg, Vurderingstatus>,
    val attesteringer: List<AttesteringJson>,
) : RevurderingJson()

internal data class IverksattRevurderingJson(
    val id: String,
    val opprettet: String,
    val periode: PeriodeJson,
    val tilRevurdering: VedtakJson,
    val beregninger: RevurdertBeregningJson,
    val saksbehandler: String,
    val fritekstTilBrev: String,
    val skalFøreTilBrevutsending: Boolean,
    val årsak: String,
    val begrunnelse: String,
    val status: RevurderingsStatus,
    val forhåndsvarsel: ForhåndsvarselJson?,
    val simulering: SimuleringJson?,
    val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerJson,
    val informasjonSomRevurderes: Map<Revurderingsteg, Vurderingstatus>,
    val attesteringer: List<AttesteringJson>,
) : RevurderingJson()

internal data class UnderkjentRevurderingJson(
    val id: String,
    val opprettet: String,
    val periode: PeriodeJson,
    val tilRevurdering: VedtakJson,
    val beregninger: RevurdertBeregningJson,
    val saksbehandler: String,
    val fritekstTilBrev: String,
    val skalFøreTilBrevutsending: Boolean,
    val årsak: String,
    val begrunnelse: String,
    val status: RevurderingsStatus,
    val forhåndsvarsel: ForhåndsvarselJson?,
    val simulering: SimuleringJson?,
    val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerJson,
    val informasjonSomRevurderes: Map<Revurderingsteg, Vurderingstatus>,
    val attesteringer: List<AttesteringJson>,
) : RevurderingJson()

internal data class StansAvUtbetalingJson(
    val id: String,
    val opprettet: String,
    val periode: PeriodeJson,
    val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerJson,
    val tilRevurdering: VedtakJson,
    val saksbehandler: String,
    val årsak: String,
    val begrunnelse: String,
    val status: RevurderingsStatus,
    val simulering: SimuleringJson,
    val attesteringer: List<AttesteringJson>,
) : RevurderingJson()

internal data class GjenopptakAvYtelseJson(
    val id: String,
    val opprettet: String,
    val periode: PeriodeJson,
    val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerJson,
    val tilRevurdering: VedtakJson,
    val saksbehandler: String,
    val årsak: String,
    val begrunnelse: String,
    val status: RevurderingsStatus,
    val simulering: SimuleringJson,
    val attesteringer: List<AttesteringJson>,
) : RevurderingJson()

internal fun Forhåndsvarsel.toJson() = when (this) {
    is Forhåndsvarsel.IngenForhåndsvarsel -> ForhåndsvarselJson.IngenForhåndsvarsel
    is Forhåndsvarsel.SkalForhåndsvarsles.Besluttet -> ForhåndsvarselJson.SkalVarslesBesluttet(
        begrunnelse = begrunnelse,
        beslutningEtterForhåndsvarsling = valg,
    )
    is Forhåndsvarsel.SkalForhåndsvarsles.Sendt -> ForhåndsvarselJson.SkalVarslesSendt
}

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ForhåndsvarselJson.IngenForhåndsvarsel::class, name = "INGEN_FORHÅNDSVARSEL"),
    JsonSubTypes.Type(value = ForhåndsvarselJson.SkalVarslesSendt::class, name = "SKAL_FORHÅNDSVARSLES_SENDT"),
    JsonSubTypes.Type(value = ForhåndsvarselJson.SkalVarslesBesluttet::class, name = "SKAL_FORHÅNDSVARSLES_BESLUTTET"),
)
internal sealed class ForhåndsvarselJson {
    object IngenForhåndsvarsel : ForhåndsvarselJson() {
        override fun equals(other: Any?) = other is IngenForhåndsvarsel
    }

    object SkalVarslesSendt : ForhåndsvarselJson() {
        override fun equals(other: Any?) = other is SkalVarslesSendt
    }

    data class SkalVarslesBesluttet(
        val begrunnelse: String,
        val beslutningEtterForhåndsvarsling: BeslutningEtterForhåndsvarsling,
    ) : ForhåndsvarselJson()
}

internal fun Revurdering.toJson(): RevurderingJson = when (this) {
    is OpprettetRevurdering -> OpprettetRevurderingJson(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        periode = periode.toJson(),
        tilRevurdering = tilRevurdering.toJson(),
        saksbehandler = saksbehandler.toString(),
        fritekstTilBrev = fritekstTilBrev,
        årsak = revurderingsårsak.årsak.toString(),
        begrunnelse = revurderingsårsak.begrunnelse.toString(),
        forhåndsvarsel = forhåndsvarsel?.toJson(),
        grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
            grunnlagsdata,
            vilkårsvurderinger,
        ),
        informasjonSomRevurderes = informasjonSomRevurderes,
        attesteringer = attesteringer.toJson(),
    )
    is SimulertRevurdering -> SimulertRevurderingJson(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        periode = periode.toJson(),
        tilRevurdering = tilRevurdering.toJson(),
        beregninger = RevurdertBeregningJson(
            revurdert = beregning.toJson(),
        ),
        saksbehandler = saksbehandler.toString(),
        fritekstTilBrev = fritekstTilBrev,
        årsak = revurderingsårsak.årsak.toString(),
        begrunnelse = revurderingsårsak.begrunnelse.toString(),
        status = InstansTilStatusMapper(this).status,
        forhåndsvarsel = forhåndsvarsel?.toJson(),
        simulering = simulering.toJson(),
        grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
            grunnlagsdata,
            vilkårsvurderinger,
        ),
        informasjonSomRevurderes = informasjonSomRevurderes,
        attesteringer = attesteringer.toJson(),
    )
    is RevurderingTilAttestering -> TilAttesteringJson(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        periode = periode.toJson(),
        tilRevurdering = tilRevurdering.toJson(),
        beregninger = RevurdertBeregningJson(
            revurdert = beregning.toJson(),
        ),
        saksbehandler = saksbehandler.toString(),
        fritekstTilBrev = fritekstTilBrev,
        skalFøreTilBrevutsending = when (this) {
            is RevurderingTilAttestering.IngenEndring -> skalFøreTilBrevutsending
            is RevurderingTilAttestering.Innvilget -> true
            is RevurderingTilAttestering.Opphørt -> true
        },
        årsak = revurderingsårsak.årsak.toString(),
        begrunnelse = revurderingsårsak.begrunnelse.toString(),
        status = InstansTilStatusMapper(this).status,
        forhåndsvarsel = forhåndsvarsel?.toJson(),
        simulering = when (this) {
            is RevurderingTilAttestering.IngenEndring -> null
            is RevurderingTilAttestering.Innvilget -> simulering.toJson()
            is RevurderingTilAttestering.Opphørt -> simulering.toJson()
        },
        grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
            grunnlagsdata,
            vilkårsvurderinger,
        ),
        informasjonSomRevurderes = informasjonSomRevurderes,
        attesteringer = attesteringer.toJson(),
    )
    is IverksattRevurdering -> IverksattRevurderingJson(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        periode = periode.toJson(),
        tilRevurdering = tilRevurdering.toJson(),
        beregninger = RevurdertBeregningJson(
            revurdert = beregning.toJson(),
        ),
        saksbehandler = saksbehandler.toString(),
        fritekstTilBrev = fritekstTilBrev,
        skalFøreTilBrevutsending = when (this) {
            is IverksattRevurdering.IngenEndring -> skalFøreTilBrevutsending
            is IverksattRevurdering.Innvilget -> true
            is IverksattRevurdering.Opphørt -> true
        },
        årsak = revurderingsårsak.årsak.toString(),
        begrunnelse = revurderingsårsak.begrunnelse.toString(),
        status = InstansTilStatusMapper(this).status,
        forhåndsvarsel = forhåndsvarsel?.toJson(),
        simulering = when (this) {
            is IverksattRevurdering.IngenEndring -> null
            is IverksattRevurdering.Innvilget -> simulering.toJson()
            is IverksattRevurdering.Opphørt -> simulering.toJson()
        },
        grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
            grunnlagsdata,
            vilkårsvurderinger,
        ),
        informasjonSomRevurderes = informasjonSomRevurderes,
        attesteringer = attesteringer.toJson(),
    )
    is UnderkjentRevurdering -> UnderkjentRevurderingJson(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        periode = periode.toJson(),
        tilRevurdering = tilRevurdering.toJson(),
        beregninger = RevurdertBeregningJson(
            revurdert = beregning.toJson(),
        ),
        saksbehandler = saksbehandler.toString(),
        fritekstTilBrev = fritekstTilBrev,
        skalFøreTilBrevutsending = when (this) {
            is UnderkjentRevurdering.IngenEndring -> skalFøreTilBrevutsending
            is UnderkjentRevurdering.Innvilget -> true
            is UnderkjentRevurdering.Opphørt -> true
        },
        årsak = revurderingsårsak.årsak.toString(),
        begrunnelse = revurderingsårsak.begrunnelse.toString(),
        status = InstansTilStatusMapper(this).status,
        forhåndsvarsel = forhåndsvarsel?.toJson(),
        simulering = when (this) {
            is UnderkjentRevurdering.IngenEndring -> null
            is UnderkjentRevurdering.Innvilget -> simulering.toJson()
            is UnderkjentRevurdering.Opphørt -> simulering.toJson()
        },
        grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
            grunnlagsdata,
            vilkårsvurderinger,
        ),
        informasjonSomRevurderes = informasjonSomRevurderes,
        attesteringer = attesteringer.toJson(),
    )
    is BeregnetRevurdering -> BeregnetRevurderingJson(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        periode = periode.toJson(),
        tilRevurdering = tilRevurdering.toJson(),
        beregninger = RevurdertBeregningJson(
            revurdert = beregning.toJson(),
        ),
        saksbehandler = saksbehandler.toString(),
        fritekstTilBrev = fritekstTilBrev,
        årsak = revurderingsårsak.årsak.toString(),
        begrunnelse = revurderingsårsak.begrunnelse.toString(),
        status = InstansTilStatusMapper(this).status,
        forhåndsvarsel = forhåndsvarsel?.toJson(),
        grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
            grunnlagsdata,
            vilkårsvurderinger,
        ),
        informasjonSomRevurderes = informasjonSomRevurderes,
        attesteringer = attesteringer.toJson(),
    )
}.also {
    // TODO jah: Vi skal ikke pre-utfylle Bosituasjon for revurdering med mer enn ett element.
    //  vi ønsker å gjøre denne sjekken backend for å ha bedre kontroll + oversikt (logger)
    grunnlagsdata.bosituasjon.throwIfMultiple()
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
    }
}

internal fun AbstraktRevurdering.toJson(): RevurderingJson {
    return when (this) {
        is Revurdering -> this.toJson()
        is StansAvYtelseRevurdering -> this.toJson()
        is GjenopptaYtelseRevurdering -> this.toJson()
    }
}

internal fun StansAvYtelseRevurdering.toJson(): RevurderingJson {
    return when (this) {
        is StansAvYtelseRevurdering.IverksattStansAvYtelse -> StansAvUtbetalingJson(
            id = id.toString(),
            opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
            periode = periode.toJson(),
            grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
                grunnlagsdata,
                vilkårsvurderinger,
            ),
            tilRevurdering = tilRevurdering.toJson(),
            saksbehandler = saksbehandler.toString(),
            årsak = revurderingsårsak.årsak.toString(),
            begrunnelse = revurderingsårsak.begrunnelse.toString(),
            status = InstansTilStatusMapper(this).status,
            simulering = simulering.toJson(),
            attesteringer = attesteringer.toJson(),
        )
        is StansAvYtelseRevurdering.SimulertStansAvYtelse -> {
            StansAvUtbetalingJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                periode = periode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
                    grunnlagsdata,
                    vilkårsvurderinger,
                ),
                tilRevurdering = tilRevurdering.toJson(),
                saksbehandler = saksbehandler.toString(),
                årsak = revurderingsårsak.årsak.toString(),
                begrunnelse = revurderingsårsak.begrunnelse.toString(),
                status = InstansTilStatusMapper(this).status,
                simulering = simulering.toJson(),
                attesteringer = emptyList(),
            )
        }
    }
}

internal fun GjenopptaYtelseRevurdering.toJson(): RevurderingJson {
    return when (this) {
        is GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse -> {
            GjenopptakAvYtelseJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                periode = periode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
                    grunnlagsdata,
                    vilkårsvurderinger,
                ),
                tilRevurdering = tilRevurdering.toJson(),
                saksbehandler = saksbehandler.toString(),
                årsak = revurderingsårsak.årsak.toString(),
                begrunnelse = revurderingsårsak.begrunnelse.toString(),
                status = InstansTilStatusMapper(this).status,
                simulering = simulering.toJson(),
                attesteringer = attesteringer.toJson(),
            )
        }
        is GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse -> GjenopptakAvYtelseJson(
            id = id.toString(),
            opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
            periode = periode.toJson(),
            grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
                grunnlagsdata,
                vilkårsvurderinger,
            ),
            tilRevurdering = tilRevurdering.toJson(),
            saksbehandler = saksbehandler.toString(),
            årsak = revurderingsårsak.årsak.toString(),
            begrunnelse = revurderingsårsak.begrunnelse.toString(),
            status = InstansTilStatusMapper(this).status,
            simulering = simulering.toJson(),
            attesteringer = emptyList(),
        )
    }
}
