package no.nav.su.se.bakover.database.regulering

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.infrastructure.PeriodeMedOptionalTilOgMedJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeMedOptionalTilOgMedJson.Companion.toJson
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.database.grunnlag.fradrag.FradragskategoriDbJson
import no.nav.su.se.bakover.database.grunnlag.fradrag.FradragskategoriDbJson.Companion.toDbJson
import no.nav.su.se.bakover.database.regulering.EksternVedtakJson.Companion.toDbJson
import no.nav.su.se.bakover.database.regulering.EksternVedtakstypeJson.Companion.toDbJson
import no.nav.su.se.bakover.database.regulering.FradragsperiodeJson.Companion.toDbJson
import no.nav.su.se.bakover.database.regulering.PerTypeJson.Companion.toDbJson
import no.nav.su.se.bakover.database.regulering.ReguleringssupplementForJson.Companion.toDbJson
import no.nav.su.se.bakover.domain.regulering.EksternSupplementRegulering
import no.nav.su.se.bakover.domain.regulering.supplement.Eksternvedtak
import no.nav.su.se.bakover.domain.regulering.supplement.Reguleringssupplement
import no.nav.su.se.bakover.domain.regulering.supplement.ReguleringssupplementFor
import java.time.LocalDate
import java.util.UUID

fun Reguleringssupplement.toDbJson(): String = serialize(this.map { it.toDbJson() })

fun deserEskternSupplementReguleringJson(json: String): EksternSupplementRegulering =
    deserialize<EksternSupplementReguleringJson>(json).toDomain()

private data class EksternSupplementReguleringJson(
    val supplementId: String?,
    val bruker: ReguleringssupplementForJson? = null,
    val eps: List<ReguleringssupplementForJson> = emptyList(),
) {
    fun toDomain(): EksternSupplementRegulering = EksternSupplementRegulering(
        supplementId = supplementId?.let { UUID.fromString(it) },
        bruker = bruker?.toDomain(),
        eps = eps.map { it.toDomain() },
    )
}

fun EksternSupplementRegulering.toDbJson(): String = EksternSupplementReguleringJson(
    supplementId = this.supplementId?.toString(),
    bruker = this.bruker?.toDbJson(),
    eps = this.eps.map { it.toDbJson() },
).let { serialize(it) }

private data class ReguleringssupplementForJson(
    val fnr: String,
    val perType: List<PerTypeJson>,
) {
    fun toDomain(): ReguleringssupplementFor = ReguleringssupplementFor(
        fnr = Fnr.tryCreate(fnr)!!,
        perType = perType.map { it.toDomain() }.toNonEmptyList(),
    )

    companion object {
        fun ReguleringssupplementFor.toDbJson(): ReguleringssupplementForJson = ReguleringssupplementForJson(
            fnr = this.fnr.toString(),
            perType = perType.map { it.toDbJson() },
        )
    }
}

private data class PerTypeJson(
    val vedtak: List<EksternVedtakJson>,
    val fradragskategori: FradragskategoriDbJson,
) {

    fun toDomain(): ReguleringssupplementFor.PerType = ReguleringssupplementFor.PerType(
        vedtak = vedtak.map { it.toDomain() }.toNonEmptyList(),
        kategori = fradragskategori.toDomain(),
    )

    companion object {
        fun ReguleringssupplementFor.PerType.toDbJson(): PerTypeJson = PerTypeJson(
            vedtak = this.vedtak.map { it.toDbJson() },
            fradragskategori = this.kategori.toDbJson(),
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
private sealed interface EksternVedtakJson {
    fun toDomain(): Eksternvedtak

    data class EndringJson(
        val periode: PeriodeMedOptionalTilOgMedJson,
        val fradrag: List<FradragsperiodeJson>,
        val beløp: Int,
    ) : EksternVedtakJson {
        override fun toDomain(): Eksternvedtak = Eksternvedtak.Endring(
            periode = periode.toDomain(),
            fradrag = fradrag.map { it.toDomain() }.toNonEmptyList(),
            beløp = beløp,
        )
    }

    data class ReguleringJson(
        val periodeOptionalTilOgMed: PeriodeMedOptionalTilOgMedJson,
        val fradrag: List<FradragsperiodeJson>,
        val beløp: Int,
    ) : EksternVedtakJson {
        override fun toDomain(): Eksternvedtak {
            return Eksternvedtak.Regulering(
                periode = periodeOptionalTilOgMed.toDomain(),
                fradrag = fradrag.map { it.toDomain() }.toNonEmptyList(),
                beløp = beløp,
            )
        }
    }

    companion object {
        fun Eksternvedtak.toDbJson(): EksternVedtakJson = when (this) {
            is Eksternvedtak.Endring -> EndringJson(
                periode = periode.toJson(),
                fradrag = fradrag.map { it.toDbJson() },
                beløp = beløp,
            )

            is Eksternvedtak.Regulering -> ReguleringJson(
                periodeOptionalTilOgMed = this.periode.toJson(),
                fradrag = fradrag.map { it.toDbJson() },
                beløp = beløp,
            )
        }
    }
}

private enum class EksternVedtakstypeJson {
    Endring,
    Regulering,
    ;

    fun toDomain(): ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype = when (this) {
        Endring -> ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Endring
        Regulering -> ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Regulering
    }

    companion object {
        fun ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.toDbJson(): EksternVedtakstypeJson =
            when (this) {
                ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Endring -> Endring
                ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Regulering -> Regulering
            }
    }
}

private data class FradragsperiodeJson(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate?,
    val vedtakstype: EksternVedtakstypeJson,
    val beløp: Int,
    val eksterndata: EksternData,
) {
    fun toDomain(): ReguleringssupplementFor.PerType.Fradragsperiode =
        ReguleringssupplementFor.PerType.Fradragsperiode(
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            beløp = beløp,
            vedtakstype = vedtakstype.toDomain(),
            eksterndata = ReguleringssupplementFor.PerType.Fradragsperiode.Eksterndata(
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
        fun ReguleringssupplementFor.PerType.Fradragsperiode.toDbJson(): FradragsperiodeJson = FradragsperiodeJson(
            fraOgMed = this.fraOgMed,
            tilOgMed = tilOgMed,
            beløp = beløp,
            vedtakstype = vedtakstype.toDbJson(),
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
