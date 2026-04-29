package no.nav.su.se.bakover.database.regulering

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.common.deserializeList
import no.nav.su.se.bakover.common.domain.tid.periode.Perioder
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeMedOptionalTilOgMedJson
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.regulering.ÅrsakTilManuellRegulering
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
)
@JsonSubTypes(
    JsonSubTypes.Type(
        value = ÅrsakTilManuellReguleringJson.FradragMåHåndteresManuelt::class,
        name = "FradragMåHåndteresManuelt",
    ),
    JsonSubTypes.Type(
        value = ÅrsakTilManuellReguleringJson.UtbetalingFeilet::class,
        name = "UtbetalingFeilet",
    ),
    JsonSubTypes.Type(
        value = ÅrsakTilManuellReguleringJson.BrukerManglerSupplement::class,
        name = "BrukerManglerSupplement",
    ),
    JsonSubTypes.Type(
        value = ÅrsakTilManuellReguleringJson.SupplementInneholderIkkeFradraget::class,
        name = "SupplementInneholderIkkeFradraget",
    ),
    JsonSubTypes.Type(
        value = ÅrsakTilManuellReguleringJson.MerEnn1Eps::class,
        name = "MerEnn1Eps",
    ),
    JsonSubTypes.Type(
        value = ÅrsakTilManuellReguleringJson.FinnesFlerePerioderAvFradrag::class,
        name = "FinnesFlerePerioderAvFradrag",
    ),
    JsonSubTypes.Type(
        value = ÅrsakTilManuellReguleringJson.FradragErUtenlandsinntekt::class,
        name = "FradragErUtenlandsinntekt",
    ),
    JsonSubTypes.Type(
        value = ÅrsakTilManuellReguleringJson.SupplementHarFlereVedtaksperioderForFradrag::class,
        name = "SupplementHarFlereVedtaksperioderForFradrag",
    ),
    JsonSubTypes.Type(
        value = ÅrsakTilManuellReguleringJson.DifferanseFørRegulering::class,
        name = "DifferanseFørRegulering",
    ),
    JsonSubTypes.Type(
        value = ÅrsakTilManuellReguleringJson.DifferanseEtterRegulering::class,
        name = "DifferanseEtterRegulering",
    ),
    JsonSubTypes.Type(
        value = ÅrsakTilManuellReguleringJson.FantIkkeVedtakForApril::class,
        name = "FantIkkeVedtakForApril",
    ),
    JsonSubTypes.Type(
        value = ÅrsakTilManuellReguleringJson.YtelseErMidlertidigStanset::class,
        name = "YtelseErMidlertidigStanset",
    ),
    JsonSubTypes.Type(
        value = ÅrsakTilManuellReguleringJson.ForventetInntektErStørreEnn0::class,
        name = "ForventetInntektErStørreEnn0",
    ),
    JsonSubTypes.Type(
        value = ÅrsakTilManuellReguleringJson.AutomatiskSendingTilUtbetalingFeilet::class,
        name = "AutomatiskSendingTilUtbetalingFeilet",
    ),
    JsonSubTypes.Type(
        value = ÅrsakTilManuellReguleringJson.VedtakstidslinjeErIkkeSammenhengende::class,
        name = "VedtakstidslinjeErIkkeSammenhengende",
    ),
    JsonSubTypes.Type(
        value = ÅrsakTilManuellReguleringJson.DelvisOpphør::class,
        name = "DelvisOpphør",
    ),
)
internal sealed interface ÅrsakTilManuellReguleringJson {
    fun toDomain(): ÅrsakTilManuellRegulering

    data class ManglerRegulertBeløpForFradrag(
        val fradragskategori: String,
        val fradragTilhører: String,
    ) : ÅrsakTilManuellReguleringJson {
        override fun toDomain(): ÅrsakTilManuellRegulering =
            ÅrsakTilManuellRegulering.ManglerRegulertBeløpForFradrag(
                fradragskategori = Fradragstype.Kategori.valueOf(fradragskategori),
                fradragTilhører = FradragTilhører.valueOf(fradragTilhører),
            )
    }

    data object ManglerIeuFraPesys : ÅrsakTilManuellReguleringJson {
        override fun toDomain(): ÅrsakTilManuellRegulering =
            ÅrsakTilManuellRegulering.ManglerIeuFraPesys()
    }

    data class YtelseErMidlertidigStanset(
        val begrunnelse: String?,
    ) : ÅrsakTilManuellReguleringJson {
        override fun toDomain(): ÅrsakTilManuellRegulering = if (begrunnelse == null) {
            ÅrsakTilManuellRegulering.Historisk.YtelseErMidlertidigStanset
        } else {
            ÅrsakTilManuellRegulering.YtelseErMidlertidigStanset(begrunnelse = begrunnelse)
        }
    }

    data object EtAutomatiskFradragHarFremtidigPeriode : ÅrsakTilManuellReguleringJson {
        override fun toDomain(): ÅrsakTilManuellRegulering =
            ÅrsakTilManuellRegulering.EtAutomatiskFradragHarFremtidigPeriode()
    }

    data object UgyldigePerioderForAutomatiskRegulering : ÅrsakTilManuellReguleringJson {
        override fun toDomain(): ÅrsakTilManuellRegulering =
            ÅrsakTilManuellRegulering.UgyldigePerioderForAutomatiskRegulering(
                begrunnelse = "Reguleringsperioden inneholder hull. Vi støtter ikke hull i vedtakene p.t.",
            )
    }

    // Historiske
    data object FradragMåHåndteresManuelt : ÅrsakTilManuellReguleringJson {
        override fun toDomain(): ÅrsakTilManuellRegulering =
            ÅrsakTilManuellRegulering.Historisk.FradragMåHåndteresManuelt.Gammel
    }

    data object UtbetalingFeilet : ÅrsakTilManuellReguleringJson {
        override fun toDomain(): ÅrsakTilManuellRegulering = ÅrsakTilManuellRegulering.Historisk.UtbetalingFeilet
    }

    data class BrukerManglerSupplement(
        val fradragskategori: String,
        val fradragTilhører: String,
        val begrunnelse: String,
    ) : ÅrsakTilManuellReguleringJson {
        override fun toDomain(): ÅrsakTilManuellRegulering =
            ÅrsakTilManuellRegulering.Historisk.FradragMåHåndteresManuelt.BrukerManglerSupplement(
                fradragskategori = Fradragstype.Kategori.valueOf(fradragskategori),
                fradragTilhører = FradragTilhører.valueOf(fradragTilhører),
                begrunnelse = begrunnelse,
            )
    }

    data class SupplementInneholderIkkeFradraget(
        val fradragskategori: String,
        val fradragTilhører: String,
        val begrunnelse: String,
    ) : ÅrsakTilManuellReguleringJson {
        override fun toDomain(): ÅrsakTilManuellRegulering =
            ÅrsakTilManuellRegulering.Historisk.FradragMåHåndteresManuelt.SupplementInneholderIkkeFradraget(
                fradragskategori = Fradragstype.Kategori.valueOf(fradragskategori),
                fradragTilhører = FradragTilhører.valueOf(fradragTilhører),
                begrunnelse = begrunnelse,
            )
    }

    data class MerEnn1Eps(
        val fradragskategori: String,
        val fradragTilhører: String,
        val begrunnelse: String,
    ) : ÅrsakTilManuellReguleringJson {
        override fun toDomain(): ÅrsakTilManuellRegulering =
            ÅrsakTilManuellRegulering.Historisk.FradragMåHåndteresManuelt.MerEnn1Eps(
                fradragskategori = Fradragstype.Kategori.valueOf(fradragskategori),
                fradragTilhører = FradragTilhører.valueOf(fradragTilhører),
                begrunnelse = begrunnelse,
            )
    }

    data class FinnesFlerePerioderAvFradrag(
        val fradragskategori: String,
        val fradragTilhører: String,
        val begrunnelse: String,
    ) : ÅrsakTilManuellReguleringJson {
        override fun toDomain(): ÅrsakTilManuellRegulering =
            ÅrsakTilManuellRegulering.Historisk.FradragMåHåndteresManuelt.FinnesFlerePerioderAvFradrag(
                fradragskategori = Fradragstype.Kategori.valueOf(fradragskategori),
                fradragTilhører = FradragTilhører.valueOf(fradragTilhører),
                begrunnelse = begrunnelse,
            )
    }

    data class FradragErUtenlandsinntekt(
        val fradragskategori: String,
        val fradragTilhører: String,
        val begrunnelse: String,
    ) : ÅrsakTilManuellReguleringJson {
        override fun toDomain(): ÅrsakTilManuellRegulering =
            ÅrsakTilManuellRegulering.Historisk.FradragMåHåndteresManuelt.FradragErUtenlandsinntekt(
                fradragskategori = Fradragstype.Kategori.valueOf(fradragskategori),
                fradragTilhører = FradragTilhører.valueOf(fradragTilhører),
                begrunnelse = begrunnelse,
            )
    }

    data class SupplementHarFlereVedtaksperioderForFradrag(
        val fradragskategori: String,
        val fradragTilhører: String,
        val begrunnelse: String,
        val eksterneReguleringsvedtakperioder: List<PeriodeMedOptionalTilOgMedJson>,
    ) : ÅrsakTilManuellReguleringJson {
        override fun toDomain(): ÅrsakTilManuellRegulering =
            ÅrsakTilManuellRegulering.Historisk.FradragMåHåndteresManuelt.SupplementHarFlereVedtaksperioderForFradrag(
                fradragskategori = Fradragstype.Kategori.valueOf(fradragskategori),
                fradragTilhører = FradragTilhører.valueOf(fradragTilhører),
                begrunnelse = begrunnelse,
                eksterneReguleringsvedtakperioder = eksterneReguleringsvedtakperioder.map { it.toDomain() },
            )
    }

    data class DifferanseFørRegulering(
        val fradragskategori: String,
        val fradragTilhører: String,
        val begrunnelse: String,
        val eksternNettoBeløpFørRegulering: String,
        val eksternBruttoBeløpFørRegulering: String,
        val vårtBeløpFørRegulering: String,
    ) : ÅrsakTilManuellReguleringJson {
        override fun toDomain(): ÅrsakTilManuellRegulering =
            ÅrsakTilManuellRegulering.Historisk.FradragMåHåndteresManuelt.DifferanseFørRegulering(
                fradragskategori = Fradragstype.Kategori.valueOf(fradragskategori),
                fradragTilhører = FradragTilhører.valueOf(fradragTilhører),
                vårtBeløpFørRegulering = vårtBeløpFørRegulering.toBigDecimal(),
                eksternBruttoBeløpFørRegulering = eksternBruttoBeløpFørRegulering.toBigDecimal(),
                eksternNettoBeløpFørRegulering = eksternNettoBeløpFørRegulering.toBigDecimal(),
                begrunnelse = begrunnelse,
            )
    }

    data class DifferanseEtterRegulering(
        val fradragskategori: String,
        val fradragTilhører: String,
        val begrunnelse: String,
        val eksternNettoBeløpEtterRegulering: String,
        val eksternBruttoBeløpEtterRegulering: String,
        val forventetBeløpEtterRegulering: String,
        val vårtBeløpFørRegulering: String,
    ) : ÅrsakTilManuellReguleringJson {
        override fun toDomain(): ÅrsakTilManuellRegulering =
            ÅrsakTilManuellRegulering.Historisk.FradragMåHåndteresManuelt.DifferanseEtterRegulering(
                fradragskategori = Fradragstype.Kategori.valueOf(fradragskategori),
                fradragTilhører = FradragTilhører.valueOf(fradragTilhører),
                vårtBeløpFørRegulering = vårtBeløpFørRegulering.toBigDecimal(),
                eksternBruttoBeløpEtterRegulering = eksternBruttoBeløpEtterRegulering.toBigDecimal(),
                eksternNettoBeløpEtterRegulering = eksternNettoBeløpEtterRegulering.toBigDecimal(),
                forventetBeløpEtterRegulering = forventetBeløpEtterRegulering.toBigDecimal(),
                begrunnelse = begrunnelse,
            )
    }

    data class FantIkkeVedtakForApril(
        val fradragskategori: String,
        val fradragTilhører: String,
        val begrunnelse: String,
    ) : ÅrsakTilManuellReguleringJson {
        override fun toDomain(): ÅrsakTilManuellRegulering =
            ÅrsakTilManuellRegulering.Historisk.FradragMåHåndteresManuelt.FantIkkeVedtakForApril(
                fradragskategori = Fradragstype.Kategori.valueOf(fradragskategori),
                fradragTilhører = FradragTilhører.valueOf(fradragTilhører),
                begrunnelse = begrunnelse,
            )
    }

    data class ForventetInntektErStørreEnn0(
        val begrunnelse: String?,
    ) : ÅrsakTilManuellReguleringJson {
        override fun toDomain(): ÅrsakTilManuellRegulering =
            ÅrsakTilManuellRegulering.Historisk.ForventetInntektErStørreEnn0(begrunnelse)
    }

    data class AutomatiskSendingTilUtbetalingFeilet(
        val begrunnelse: String,
    ) : ÅrsakTilManuellReguleringJson {
        override fun toDomain(): ÅrsakTilManuellRegulering =
            ÅrsakTilManuellRegulering.Historisk.AutomatiskSendingTilUtbetalingFeilet(begrunnelse)
    }

    data class VedtakstidslinjeErIkkeSammenhengende(
        val begrunnelse: String,
    ) : ÅrsakTilManuellReguleringJson {
        override fun toDomain(): ÅrsakTilManuellRegulering =
            ÅrsakTilManuellRegulering.Historisk.VedtakstidslinjeErIkkeSammenhengende(begrunnelse)
    }

    data class DelvisOpphør(
        val opphørsperioder: List<PeriodeJson>,
        val begrunnelse: String?,
    ) : ÅrsakTilManuellReguleringJson {
        override fun toDomain(): ÅrsakTilManuellRegulering =
            ÅrsakTilManuellRegulering.Historisk.DelvisOpphør(
                opphørsperioder.map { it.toPeriode() }.let { Perioder.create(it) },
                begrunnelse,
            )
    }

    companion object {
        fun toDomain(json: String): Set<ÅrsakTilManuellRegulering> =
            deserializeList<ÅrsakTilManuellReguleringJson>(json).map { it.toDomain() }.toSet()
    }
}

internal fun Reguleringstype.årsakerTilManuellReguleringJson(): String =
    when (this) {
        Reguleringstype.AUTOMATISK -> "[]"
        is Reguleringstype.MANUELL -> this.problemer.toDbJson()
    }

internal fun Set<ÅrsakTilManuellRegulering>.toDbJson(): String =
    this.joinToString(prefix = "[", postfix = "]") { it.toDbJson() }

internal fun ÅrsakTilManuellRegulering.toDbJson(): String = when (this) {
    is ÅrsakTilManuellRegulering.ManglerRegulertBeløpForFradrag -> ÅrsakTilManuellReguleringJson.ManglerRegulertBeløpForFradrag(
        fradragskategori = this.fradragskategori.name,
        fradragTilhører = this.fradragTilhører.name,
    )

    is ÅrsakTilManuellRegulering.YtelseErMidlertidigStanset -> ÅrsakTilManuellReguleringJson.YtelseErMidlertidigStanset(
        this.begrunnelse,
    )

    is ÅrsakTilManuellRegulering.ManglerIeuFraPesys -> ÅrsakTilManuellReguleringJson.ManglerIeuFraPesys
    is ÅrsakTilManuellRegulering.EtAutomatiskFradragHarFremtidigPeriode -> ÅrsakTilManuellReguleringJson.EtAutomatiskFradragHarFremtidigPeriode
    is ÅrsakTilManuellRegulering.UgyldigePerioderForAutomatiskRegulering -> ÅrsakTilManuellReguleringJson.UgyldigePerioderForAutomatiskRegulering

    is ÅrsakTilManuellRegulering.Historisk -> IllegalArgumentException("Skal ikke lagre historiske årsaker")
}.let {
    serialize(it)
}
