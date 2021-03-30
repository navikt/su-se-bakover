package no.nav.su.se.bakover.web.routes.revurdering

import com.fasterxml.jackson.annotation.JsonInclude
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.web.routes.behandling.AttesteringJson
import no.nav.su.se.bakover.web.routes.behandling.UnderkjennelseJson
import no.nav.su.se.bakover.web.routes.behandling.beregning.BeregningJson
import no.nav.su.se.bakover.web.routes.behandling.beregning.PeriodeJson
import no.nav.su.se.bakover.web.routes.behandling.beregning.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.behandling.beregning.toJson
import no.nav.su.se.bakover.web.routes.grunnlag.GrunnlagsdataJson
import no.nav.su.se.bakover.web.routes.grunnlag.toJson
import java.time.format.DateTimeFormatter

sealed class RevurderingJson

internal enum class RevurderingsStatus {
    OPPRETTET,
    BEREGNET_INNVILGET,
    BEREGNET_AVSLAG,
    SIMULERT,
    TIL_ATTESTERING,
    IVERKSATT,
    UNDERKJENT
}

internal data class RevurdertBeregningJson(
    val beregning: BeregningJson,
    val revurdert: BeregningJson
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
    val grunnlag: GrunnlagsdataJson,
) : RevurderingJson() {
    @JsonInclude
    val status = RevurderingsStatus.OPPRETTET
}

internal sealed class BeregnetRevurderingJson : RevurderingJson() {
    abstract val id: String
    abstract val opprettet: String
    abstract val periode: PeriodeJson
    abstract val tilRevurdering: VedtakJson
    abstract val beregninger: RevurdertBeregningJson
    abstract val saksbehandler: String
    abstract val fritekstTilBrev: String
    abstract val årsak: String
    abstract val begrunnelse: String
    abstract val grunnlag: GrunnlagsdataJson

    data class Innvilget(
        override val id: String,
        override val opprettet: String,
        override val periode: PeriodeJson,
        override val tilRevurdering: VedtakJson,
        override val beregninger: RevurdertBeregningJson,
        override val saksbehandler: String,
        override val fritekstTilBrev: String,
        override val årsak: String,
        override val begrunnelse: String,
        override val grunnlag: GrunnlagsdataJson,
    ) : BeregnetRevurderingJson() {
        @JsonInclude
        val status = RevurderingsStatus.BEREGNET_INNVILGET
    }

    data class Avslag(
        override val id: String,
        override val opprettet: String,
        override val periode: PeriodeJson,
        override val tilRevurdering: VedtakJson,
        override val beregninger: RevurdertBeregningJson,
        override val saksbehandler: String,
        override val fritekstTilBrev: String,
        override val årsak: String,
        override val begrunnelse: String,
        override val grunnlag: GrunnlagsdataJson
    ) : BeregnetRevurderingJson() {
        @JsonInclude
        val status = RevurderingsStatus.BEREGNET_AVSLAG
    }
}

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
    val grunnlag: GrunnlagsdataJson
) : RevurderingJson() {
    @JsonInclude
    val status = RevurderingsStatus.SIMULERT
}

internal data class TilAttesteringJson(
    val id: String,
    val opprettet: String,
    val periode: PeriodeJson,
    val tilRevurdering: VedtakJson,
    val beregninger: RevurdertBeregningJson,
    val saksbehandler: String,
    val fritekstTilBrev: String,
    val årsak: String,

    val begrunnelse: String,
    val grunnlag: GrunnlagsdataJson
) : RevurderingJson() {
    @JsonInclude
    val status = RevurderingsStatus.TIL_ATTESTERING
}

internal data class IverksattRevurderingJson(
    val id: String,
    val opprettet: String,
    val periode: PeriodeJson,
    val tilRevurdering: VedtakJson,
    val beregninger: RevurdertBeregningJson,
    val saksbehandler: String,
    val attestant: String,
    val fritekstTilBrev: String,
    val årsak: String,
    val begrunnelse: String,
    val grunnlag: GrunnlagsdataJson
) : RevurderingJson() {
    @JsonInclude
    val status = RevurderingsStatus.IVERKSATT
}

internal data class UnderkjentRevurderingJson(
    val id: String,
    val opprettet: String,
    val periode: PeriodeJson,
    val tilRevurdering: VedtakJson,
    val beregninger: RevurdertBeregningJson,
    val saksbehandler: String,
    val attestering: AttesteringJson,
    val fritekstTilBrev: String,
    val årsak: String,
    val begrunnelse: String,
    val grunnlag: GrunnlagsdataJson,
) : RevurderingJson() {
    @JsonInclude
    val status = RevurderingsStatus.UNDERKJENT
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
        grunnlag = grunnlagsdata.toJson(),
    )
    is SimulertRevurdering -> SimulertRevurderingJson(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        periode = periode.toJson(),
        tilRevurdering = tilRevurdering.toJson(),
        beregninger = RevurdertBeregningJson(
            beregning = tilRevurdering.beregning.toJson(),
            revurdert = beregning.toJson(),
        ),
        saksbehandler = saksbehandler.toString(),
        fritekstTilBrev = fritekstTilBrev,
        årsak = revurderingsårsak.årsak.toString(),
        begrunnelse = revurderingsårsak.begrunnelse.toString(),
        grunnlag = grunnlagsdata.toJson(),
    )
    is RevurderingTilAttestering -> TilAttesteringJson(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        periode = periode.toJson(),
        tilRevurdering = tilRevurdering.toJson(),
        beregninger = RevurdertBeregningJson(
            beregning = tilRevurdering.beregning.toJson(),
            revurdert = beregning.toJson(),
        ),
        saksbehandler = saksbehandler.toString(),
        fritekstTilBrev = fritekstTilBrev,
        årsak = revurderingsårsak.årsak.toString(),
        begrunnelse = revurderingsårsak.begrunnelse.toString(),
        grunnlag = grunnlagsdata.toJson(),
    )
    is IverksattRevurdering -> IverksattRevurderingJson(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        periode = periode.toJson(),
        tilRevurdering = tilRevurdering.toJson(),
        beregninger = RevurdertBeregningJson(
            beregning = tilRevurdering.beregning.toJson(),
            revurdert = beregning.toJson(),
        ),
        saksbehandler = saksbehandler.toString(),
        attestant = attestering.attestant.toString(),
        fritekstTilBrev = fritekstTilBrev,
        årsak = revurderingsårsak.årsak.toString(),
        begrunnelse = revurderingsårsak.begrunnelse.toString(),
        grunnlag = grunnlagsdata.toJson(),
    )
    is BeregnetRevurdering.Innvilget -> BeregnetRevurderingJson.Innvilget(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        periode = periode.toJson(),
        tilRevurdering = tilRevurdering.toJson(),
        beregninger = RevurdertBeregningJson(
            beregning = tilRevurdering.beregning.toJson(),
            revurdert = beregning.toJson(),
        ),
        saksbehandler = saksbehandler.toString(),
        fritekstTilBrev = fritekstTilBrev,
        årsak = revurderingsårsak.årsak.toString(),
        begrunnelse = revurderingsårsak.begrunnelse.toString(),
        grunnlag = grunnlagsdata.toJson(),
    )
    is BeregnetRevurdering.Avslag -> BeregnetRevurderingJson.Avslag(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        periode = periode.toJson(),
        tilRevurdering = tilRevurdering.toJson(),
        beregninger = RevurdertBeregningJson(
            beregning = tilRevurdering.beregning.toJson(),
            revurdert = beregning.toJson(),
        ),
        saksbehandler = saksbehandler.toString(),
        fritekstTilBrev = fritekstTilBrev,
        årsak = revurderingsårsak.årsak.toString(),
        begrunnelse = revurderingsårsak.begrunnelse.toString(),
        grunnlag = grunnlagsdata.toJson(),
    )
    is UnderkjentRevurdering -> UnderkjentRevurderingJson(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        periode = periode.toJson(),
        tilRevurdering = tilRevurdering.toJson(),
        beregninger = RevurdertBeregningJson(
            beregning = tilRevurdering.beregning.toJson(),
            revurdert = beregning.toJson()
        ),
        saksbehandler = saksbehandler.toString(),
        grunnlag = grunnlagsdata.toJson(),
        attestering = when (val attestering = attestering) {
            is Attestering.Iverksatt -> AttesteringJson(
                attestant = attestering.attestant.navIdent,
                underkjennelse = null,
            )
            is Attestering.Underkjent -> AttesteringJson(
                attestant = attestering.attestant.navIdent,
                underkjennelse = UnderkjennelseJson(
                    grunn = attestering.grunn.toString(),
                    kommentar = attestering.kommentar,
                ),
            )
        },
        fritekstTilBrev = fritekstTilBrev,
        årsak = revurderingsårsak.årsak.toString(),
        begrunnelse = revurderingsårsak.begrunnelse.toString(),
    )
}
