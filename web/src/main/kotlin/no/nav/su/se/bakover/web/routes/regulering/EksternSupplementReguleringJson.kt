package no.nav.su.se.bakover.web.routes.regulering

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeMedOptionalTilOgMedJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeMedOptionalTilOgMedJson.Companion.toJson
import no.nav.su.se.bakover.domain.regulering.EksternSupplementRegulering
import no.nav.su.se.bakover.domain.regulering.supplement.Eksternvedtak
import no.nav.su.se.bakover.domain.regulering.supplement.ReguleringssupplementFor
import no.nav.su.se.bakover.web.routes.grunnlag.fradrag.FradragskategoriJson
import no.nav.su.se.bakover.web.routes.grunnlag.fradrag.FradragskategoriJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.regulering.EksternVedtakJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.regulering.EksternVedtakstypeJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.regulering.FradragsperiodeJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.regulering.PerTypeJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.regulering.ReguleringssupplementForJson.Companion.toJson
import java.time.LocalDate

internal data class EksternSupplementReguleringJson(
    val bruker: ReguleringssupplementForJson? = null,
    val eps: List<ReguleringssupplementForJson> = emptyList(),
) {
    companion object {
        fun EksternSupplementRegulering.toJson(): EksternSupplementReguleringJson = EksternSupplementReguleringJson(
            bruker = this.bruker?.toJson(),
            eps = this.eps.map { it.toJson() },
        )
    }
}

internal data class ReguleringssupplementForJson(
    val fnr: String,
    val perType: List<PerTypeJson>,
) {
    companion object {
        fun ReguleringssupplementFor.toJson(): ReguleringssupplementForJson = ReguleringssupplementForJson(
            fnr = this.fnr.toString(),
            perType = perType.map { it.toJson() },
        )
    }
}

internal data class PerTypeJson(
    val vedtak: List<EksternVedtakJson>,
    val fradragskategori: FradragskategoriJson,
) {
    companion object {
        fun ReguleringssupplementFor.PerType.toJson(): PerTypeJson = PerTypeJson(
            vedtak = this.vedtak.map { it.toJson() },
            fradragskategori = this.kategori.toJson(),
        )
    }
}

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
)
@JsonSubTypes(
    JsonSubTypes.Type(
        value = EksternVedtakJson.EndringJson::class,
        name = "endring",
    ),
    JsonSubTypes.Type(
        value = EksternVedtakJson.ReguleringJson::class,
        name = "regulering",
    ),
)
internal sealed interface EksternVedtakJson {
    val fradrag: List<FradragsperiodeJson>
    val beløp: Int

    data class EndringJson(
        override val fradrag: List<FradragsperiodeJson>,
        override val beløp: Int,
        val måned: PeriodeJson,
    ) : EksternVedtakJson

    data class ReguleringJson(
        override val fradrag: List<FradragsperiodeJson>,
        override val beløp: Int,
        val periodeOptionalTilOgMed: PeriodeMedOptionalTilOgMedJson,
    ) : EksternVedtakJson

    companion object {
        fun Eksternvedtak.toJson(): EksternVedtakJson = when (this) {
            is Eksternvedtak.Endring -> EndringJson(
                måned = måned.toJson(),
                fradrag = fradrag.map { it.toJson() },
                beløp = beløp,
            )

            is Eksternvedtak.Regulering -> ReguleringJson(
                periodeOptionalTilOgMed = this.periode.toJson(),
                fradrag = fradrag.map { it.toJson() },
                beløp = beløp,
            )
        }
    }
}

enum class EksternVedtakstypeJson {
    Endring,
    Regulering,
    ;

    companion object {
        fun ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.toJson(): EksternVedtakstypeJson =
            when (this) {
                ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Endring -> Endring
                ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Regulering -> Regulering
            }
    }
}

internal data class FradragsperiodeJson(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate?,
    val vedtakstype: EksternVedtakstypeJson,
    val beløp: Int,
    val eksterndata: EksternData,
) {
    data class EksternData(
        val fnr: String,
        val sakstype: String,
        val vedtakstype: String,
        val fraOgMed: String,
        val tilOgMed: String?,
        val bruttoYtelse: String,
        val nettoYtelse: String,
        val ytelseskomponenttype: String,
        val bruttoYtelseskomponent: String,
        val nettoYtelseskomponent: String,
    )

    companion object {
        fun ReguleringssupplementFor.PerType.Fradragsperiode.toJson(): FradragsperiodeJson = FradragsperiodeJson(
            fraOgMed = this.fraOgMed,
            tilOgMed = tilOgMed,
            beløp = beløp,
            vedtakstype = vedtakstype.toJson(),
            eksterndata = EksternData(
                fnr = eksterndata.fnr,
                sakstype = eksterndata.sakstype,
                vedtakstype = eksterndata.vedtakstype,
                fraOgMed = eksterndata.fraOgMed,
                tilOgMed = eksterndata.tilOgMed,
                bruttoYtelse = eksterndata.bruttoYtelse,
                nettoYtelse = eksterndata.nettoYtelse,
                ytelseskomponenttype = eksterndata.ytelseskomponenttype,
                bruttoYtelseskomponent = eksterndata.bruttoYtelseskomponent,
                nettoYtelseskomponent = eksterndata.nettoYtelseskomponent,
            ),
        )
    }
}
