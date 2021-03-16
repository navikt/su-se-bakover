package no.nav.su.se.bakover.web.routes.revurdering

import com.fasterxml.jackson.annotation.JsonInclude
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.routes.behandling.beregning.BeregningJson
import no.nav.su.se.bakover.web.routes.behandling.beregning.PeriodeJson
import no.nav.su.se.bakover.web.routes.behandling.beregning.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.behandling.beregning.toJson
import no.nav.su.se.bakover.web.routes.behandling.søknadsbehandling.GrunnlagsdatasetJson
import no.nav.su.se.bakover.web.routes.behandling.søknadsbehandling.toJson
import java.time.format.DateTimeFormatter

sealed class RevurderingJson

internal enum class RevurderingsStatus {
    OPPRETTET,
    BEREGNET_INNVILGET,
    BEREGNET_AVSLAG,
    SIMULERT,
    TIL_ATTESTERING,
    IVERKSATT
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
    val grunnlag: GrunnlagsdatasetJson,
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
    abstract val grunnlag: GrunnlagsdatasetJson

    data class Innvilget(
        override val id: String,
        override val opprettet: String,
        override val periode: PeriodeJson,
        override val tilRevurdering: VedtakJson,
        override val beregninger: RevurdertBeregningJson,
        override val saksbehandler: String,
        override val grunnlag: GrunnlagsdatasetJson,
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
        override val grunnlag: GrunnlagsdatasetJson
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
    val grunnlag: GrunnlagsdatasetJson
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
    val grunnlag: GrunnlagsdatasetJson
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
    val grunnlag: GrunnlagsdatasetJson
) : RevurderingJson() {
    @JsonInclude
    val status = RevurderingsStatus.IVERKSATT
}

internal fun Revurdering.toJson(revurderingService: RevurderingService): RevurderingJson = when (this) {
    is OpprettetRevurdering -> OpprettetRevurderingJson(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        periode = periode.toJson(),
        tilRevurdering = tilRevurdering.toJson(),
        saksbehandler = saksbehandler.toString(),
        grunnlag = GrunnlagsdatasetJson(
            førBehandling = revurderingService.opprettGrunnlagForRevurdering(sakId, periode).toJson(),
            endring = grunnlagsdata.toJson(),
            resultat = revurderingService.opprettGrunnlagsresultat(this).toJson(),
        ),
    )
    is SimulertRevurdering -> SimulertRevurderingJson(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        periode = periode.toJson(),
        tilRevurdering = tilRevurdering.toJson(),
        beregninger = RevurdertBeregningJson(
            beregning = tilRevurdering.beregning.toJson(),
            revurdert = beregning.toJson()
        ),
        saksbehandler = saksbehandler.toString(),
        grunnlag = GrunnlagsdatasetJson(
            førBehandling = revurderingService.opprettGrunnlagForRevurdering(sakId, periode).toJson(),
            endring = grunnlagsdata.toJson(),
            resultat = revurderingService.opprettGrunnlagsresultat(this).toJson(),
        ),
    )
    is RevurderingTilAttestering -> TilAttesteringJson(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        periode = periode.toJson(),
        tilRevurdering = tilRevurdering.toJson(),
        beregninger = RevurdertBeregningJson(
            beregning = tilRevurdering.beregning.toJson(),
            revurdert = beregning.toJson()
        ),
        saksbehandler = saksbehandler.toString(),
        grunnlag = GrunnlagsdatasetJson(
            førBehandling = revurderingService.opprettGrunnlagForRevurdering(sakId, periode).toJson(),
            endring = grunnlagsdata.toJson(),
            resultat = revurderingService.opprettGrunnlagsresultat(this).toJson(),
        ),
    )
    is IverksattRevurdering -> IverksattRevurderingJson(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        periode = periode.toJson(),
        tilRevurdering = tilRevurdering.toJson(),
        beregninger = RevurdertBeregningJson(
            beregning = tilRevurdering.beregning.toJson(),
            revurdert = beregning.toJson()
        ),
        saksbehandler = saksbehandler.toString(),
        attestant = attestant.toString(),
        grunnlag = GrunnlagsdatasetJson(
            førBehandling = revurderingService.opprettGrunnlagForRevurdering(sakId, periode).toJson(),
            endring = grunnlagsdata.toJson(),
            resultat = revurderingService.opprettGrunnlagsresultat(this).toJson(),
        ),
    )
    is BeregnetRevurdering.Innvilget -> BeregnetRevurderingJson.Innvilget(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        periode = periode.toJson(),
        tilRevurdering = tilRevurdering.toJson(),
        beregninger = RevurdertBeregningJson(
            beregning = tilRevurdering.beregning.toJson(),
            revurdert = beregning.toJson()
        ),
        saksbehandler = saksbehandler.toString(),
        grunnlag = GrunnlagsdatasetJson(
            førBehandling = revurderingService.opprettGrunnlagForRevurdering(sakId, periode).toJson(),
            endring = grunnlagsdata.toJson(),
            resultat = revurderingService.opprettGrunnlagsresultat(this).toJson(),
        ),
    )
    is BeregnetRevurdering.Avslag -> BeregnetRevurderingJson.Avslag(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        periode = periode.toJson(),
        tilRevurdering = tilRevurdering.toJson(),
        beregninger = RevurdertBeregningJson(
            beregning = tilRevurdering.beregning.toJson(),
            revurdert = beregning.toJson()
        ),
        saksbehandler = saksbehandler.toString(),
        grunnlag = GrunnlagsdatasetJson(
            førBehandling = revurderingService.opprettGrunnlagForRevurdering(sakId, periode).toJson(),
            endring = grunnlagsdata.toJson(),
            resultat = revurderingService.opprettGrunnlagsresultat(this).toJson(),
        ),
    )
}
