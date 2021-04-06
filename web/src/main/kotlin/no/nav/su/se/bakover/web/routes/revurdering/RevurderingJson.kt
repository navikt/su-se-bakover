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
import java.time.format.DateTimeFormatter

sealed class RevurderingJson

internal enum class RevurderingsStatus {
    OPPRETTET,
    BEREGNET_INNVILGET,
    BEREGNET_AVSLAG,
    BEREGNET_OPPHØRT,
    SIMULERT_INNVILGET,
    SIMULERT_OPPHØRT,
    TIL_ATTESTERING_INNVILGET,
    TIL_ATTESTERING_OPPHØRT,
    IVERKSATT_INNVILGET,
    IVERKSATT_OPPHØRT,
    UNDERKJENT_INNVILGET,
    UNDERKJENT_OPPHØRT,
}

internal data class RevurdertBeregningJson(
    val beregning: BeregningJson,
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
    ) : BeregnetRevurderingJson() {
        @JsonInclude
        val status = RevurderingsStatus.BEREGNET_AVSLAG
    }

    data class Opphørt(
        override val id: String,
        override val opprettet: String,
        override val periode: PeriodeJson,
        override val tilRevurdering: VedtakJson,
        override val beregninger: RevurdertBeregningJson,
        override val saksbehandler: String,
        override val fritekstTilBrev: String,
        override val årsak: String,
        override val begrunnelse: String,
    ) : BeregnetRevurderingJson() {
        @JsonInclude
        val status = RevurderingsStatus.BEREGNET_OPPHØRT
    }
}

internal sealed class SimulertRevurderingJson : RevurderingJson() {
    abstract val id: String
    abstract val opprettet: String
    abstract val periode: PeriodeJson
    abstract val tilRevurdering: VedtakJson
    abstract val beregninger: RevurdertBeregningJson
    abstract val saksbehandler: String
    abstract val fritekstTilBrev: String
    abstract val årsak: String
    abstract val begrunnelse: String

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
    ) : SimulertRevurderingJson() {
        @JsonInclude
        val status = RevurderingsStatus.SIMULERT_INNVILGET
    }

    data class Opphørt(
        override val id: String,
        override val opprettet: String,
        override val periode: PeriodeJson,
        override val tilRevurdering: VedtakJson,
        override val beregninger: RevurdertBeregningJson,
        override val saksbehandler: String,
        override val fritekstTilBrev: String,
        override val årsak: String,
        override val begrunnelse: String,
    ) : SimulertRevurderingJson() {
        @JsonInclude
        val status = RevurderingsStatus.SIMULERT_OPPHØRT
    }
}

internal sealed class TilAttesteringJson : RevurderingJson() {
    abstract val id: String
    abstract val opprettet: String
    abstract val periode: PeriodeJson
    abstract val tilRevurdering: VedtakJson
    abstract val beregninger: RevurdertBeregningJson
    abstract val saksbehandler: String
    abstract val fritekstTilBrev: String
    abstract val årsak: String
    abstract val begrunnelse: String

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
    ) : TilAttesteringJson() {
        @JsonInclude
        val status = RevurderingsStatus.TIL_ATTESTERING_INNVILGET
    }

    data class Opphørt(
        override val id: String,
        override val opprettet: String,
        override val periode: PeriodeJson,
        override val tilRevurdering: VedtakJson,
        override val beregninger: RevurdertBeregningJson,
        override val saksbehandler: String,
        override val fritekstTilBrev: String,
        override val årsak: String,
        override val begrunnelse: String,
    ) : TilAttesteringJson() {
        @JsonInclude
        val status = RevurderingsStatus.TIL_ATTESTERING_OPPHØRT
    }
}

internal sealed class IverksattRevurderingJson : RevurderingJson() {
    abstract val id: String
    abstract val opprettet: String
    abstract val periode: PeriodeJson
    abstract val tilRevurdering: VedtakJson
    abstract val beregninger: RevurdertBeregningJson
    abstract val saksbehandler: String
    abstract val fritekstTilBrev: String
    abstract val årsak: String
    abstract val begrunnelse: String
    abstract val attestant: String

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
        override val attestant: String,
    ) : IverksattRevurderingJson() {
        @JsonInclude
        val status = RevurderingsStatus.IVERKSATT_INNVILGET
    }

    data class Opphørt(
        override val id: String,
        override val opprettet: String,
        override val periode: PeriodeJson,
        override val tilRevurdering: VedtakJson,
        override val beregninger: RevurdertBeregningJson,
        override val saksbehandler: String,
        override val fritekstTilBrev: String,
        override val årsak: String,
        override val begrunnelse: String,
        override val attestant: String,
    ) : IverksattRevurderingJson() {
        @JsonInclude
        val status = RevurderingsStatus.IVERKSATT_OPPHØRT
    }
}

internal sealed class UnderkjentRevurderingJson : RevurderingJson() {
    abstract val id: String
    abstract val opprettet: String
    abstract val periode: PeriodeJson
    abstract val tilRevurdering: VedtakJson
    abstract val beregninger: RevurdertBeregningJson
    abstract val saksbehandler: String
    abstract val fritekstTilBrev: String
    abstract val årsak: String
    abstract val begrunnelse: String
    abstract val attestering: AttesteringJson

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
        override val attestering: AttesteringJson,

    ) : UnderkjentRevurderingJson() {
        @JsonInclude
        val status = RevurderingsStatus.UNDERKJENT_INNVILGET
    }

    data class Opphørt(
        override val id: String,
        override val opprettet: String,
        override val periode: PeriodeJson,
        override val tilRevurdering: VedtakJson,
        override val beregninger: RevurdertBeregningJson,
        override val saksbehandler: String,
        override val fritekstTilBrev: String,
        override val årsak: String,
        override val begrunnelse: String,
        override val attestering: AttesteringJson,
    ) : UnderkjentRevurderingJson() {
        @JsonInclude
        val status = RevurderingsStatus.UNDERKJENT_OPPHØRT
    }
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
    )
    is SimulertRevurdering.Innvilget -> SimulertRevurderingJson.Innvilget(
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
    )
    is SimulertRevurdering.Opphørt -> SimulertRevurderingJson.Opphørt(
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
    )
    is RevurderingTilAttestering.Innvilget -> TilAttesteringJson.Innvilget(
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
    )
    is RevurderingTilAttestering.Opphørt -> TilAttesteringJson.Opphørt(
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
    )
    is IverksattRevurdering.Innvilget -> IverksattRevurderingJson.Innvilget(
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
        attestant = attestering.attestant.toString(),
    )
    is IverksattRevurdering.Opphørt -> IverksattRevurderingJson.Opphørt(
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
    )
    is BeregnetRevurdering.IngenEndring -> BeregnetRevurderingJson.Avslag(
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
    )
    is BeregnetRevurdering.Opphørt -> BeregnetRevurderingJson.Opphørt(
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
    )
    is UnderkjentRevurdering.Innvilget -> UnderkjentRevurderingJson.Innvilget(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        periode = periode.toJson(),
        tilRevurdering = tilRevurdering.toJson(),
        beregninger = RevurdertBeregningJson(
            beregning = tilRevurdering.beregning.toJson(),
            revurdert = beregning.toJson(),
        ),
        saksbehandler = saksbehandler.toString(),
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
    is UnderkjentRevurdering.Opphørt -> UnderkjentRevurderingJson.Opphørt(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        periode = periode.toJson(),
        tilRevurdering = tilRevurdering.toJson(),
        beregninger = RevurdertBeregningJson(
            beregning = tilRevurdering.beregning.toJson(),
            revurdert = beregning.toJson(),
        ),
        saksbehandler = saksbehandler.toString(),
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
