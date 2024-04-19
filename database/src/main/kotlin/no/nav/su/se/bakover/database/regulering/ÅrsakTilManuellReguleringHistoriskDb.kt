package no.nav.su.se.bakover.database.regulering

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeMedOptionalTilOgMedJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeMedOptionalTilOgMedJson.Companion.toJson
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.regulering.ÅrsakTilManuellRegulering

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
        value = ÅrsakTilManuellReguleringJson.MismatchMellomBeløpFraSupplementOgFradrag::class,
        name = "MismatchMellomBeløpFraSupplementOgFradrag",
    ),
    JsonSubTypes.Type(
        value = ÅrsakTilManuellReguleringJson.BeløpErStørreEnForventet::class,
        name = "BeløpErStørreEnForventet",
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
sealed interface ÅrsakTilManuellReguleringJson {

    data object FradragMåHåndteresManuelt : ÅrsakTilManuellReguleringJson
    data object UtbetalingFeilet : ÅrsakTilManuellReguleringJson

    data class BrukerManglerSupplement(
        val fradragstype: String,
        val fradragTilhører: String,
        val begrunnelse: String,
    ) : ÅrsakTilManuellReguleringJson

    data class SupplementInneholderIkkeFradraget(
        val fradragstype: String,
        val fradragTilhører: String,
        val begrunnelse: String,
    ) : ÅrsakTilManuellReguleringJson

    data class FinnesFlerePerioderAvFradrag(
        val fradragstype: String,
        val fradragTilhører: String,
        val begrunnelse: String,
    ) : ÅrsakTilManuellReguleringJson

    data class FradragErUtenlandsinntekt(
        val fradragstype: String,
        val fradragTilhører: String,
        val begrunnelse: String,
    ) : ÅrsakTilManuellReguleringJson

    data class SupplementHarFlereVedtaksperioderForFradrag(
        val fradragstype: String,
        val fradragTilhører: String,
        val begrunnelse: String,
        val eksterneReguleringsvedtakperioder: List<PeriodeMedOptionalTilOgMedJson>,
    ) : ÅrsakTilManuellReguleringJson

    data class MismatchMellomBeløpFraSupplementOgFradrag(
        val fradragstype: String,
        val fradragTilhører: String,
        val begrunnelse: String,
        val eksterntBeløpFørRegulering: String,
        val vårtBeløpFørRegulering: String,
    ) : ÅrsakTilManuellReguleringJson

    data class BeløpErStørreEnForventet(
        val fradragstype: String,
        val fradragTilhører: String,
        val begrunnelse: String,
        val eksterntBeløpEtterRegulering: String,
        val forventetBeløpEtterRegulering: String,
    ) : ÅrsakTilManuellReguleringJson

    data class YtelseErMidlertidigStanset(
        val begrunnelse: String?,
    ) : ÅrsakTilManuellReguleringJson

    data class ForventetInntektErStørreEnn0(
        val begrunnelse: String?,
    ) : ÅrsakTilManuellReguleringJson

    data class AutomatiskSendingTilUtbetalingFeilet(
        val begrunnelse: String,
    ) : ÅrsakTilManuellReguleringJson

    data class VedtakstidslinjeErIkkeSammenhengende(
        val begrunnelse: String,
    ) : ÅrsakTilManuellReguleringJson

    data class DelvisOpphør(
        val opphørsperioder: List<PeriodeJson>,
        val begrunnelse: String?,
    ) : ÅrsakTilManuellReguleringJson

    companion object {
        fun toDomain(json: String): Set<ÅrsakTilManuellRegulering> {
            TODO("Not yet implemented - $json")
        }
    }
}

fun ÅrsakTilManuellRegulering.toDbJson(): String {
    return when (this) {
        is ÅrsakTilManuellRegulering.AutomatiskSendingTilUtbetalingFeilet -> ÅrsakTilManuellReguleringJson.AutomatiskSendingTilUtbetalingFeilet(
            begrunnelse = this.begrunnelse,
        )

        is ÅrsakTilManuellRegulering.DelvisOpphør -> ÅrsakTilManuellReguleringJson.DelvisOpphør(
            opphørsperioder = this.opphørsperioder.map { it.toJson() },
            begrunnelse = this.begrunnelse,
        )

        is ÅrsakTilManuellRegulering.ForventetInntektErStørreEnn0 -> ÅrsakTilManuellReguleringJson.ForventetInntektErStørreEnn0(
            begrunnelse = this.begrunnelse,
        )

        is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.BeløpErStørreEnForventet -> ÅrsakTilManuellReguleringJson.BeløpErStørreEnForventet(
            begrunnelse = this.begrunnelse,
            fradragstype = this.fradragstype.kategori.toString(),
            fradragTilhører = this.fradragTilhører.toString(),
            eksterntBeløpEtterRegulering = this.eksterntBeløpEtterRegulering.toString(),
            forventetBeløpEtterRegulering = this.forventetBeløpEtterRegulering.toString(),

        )

        is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.BrukerManglerSupplement -> ÅrsakTilManuellReguleringJson.BrukerManglerSupplement(
            begrunnelse = this.begrunnelse,
            fradragstype = this.fradragstype.kategori.toString(),
            fradragTilhører = this.fradragTilhører.toString(),
        )

        is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FinnesFlerePerioderAvFradrag -> ÅrsakTilManuellReguleringJson.FinnesFlerePerioderAvFradrag(
            begrunnelse = this.begrunnelse,
            fradragstype = this.fradragstype.toString(),
            fradragTilhører = this.fradragTilhører.toString(),
        )

        is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FradragErUtenlandsinntekt -> ÅrsakTilManuellReguleringJson.FradragErUtenlandsinntekt(
            begrunnelse = this.begrunnelse,
            fradragstype = this.fradragstype.toString(),
            fradragTilhører = this.fradragTilhører.toString(),
        )

        is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.MismatchMellomBeløpFraSupplementOgFradrag -> ÅrsakTilManuellReguleringJson.MismatchMellomBeløpFraSupplementOgFradrag(
            begrunnelse = this.begrunnelse,
            fradragstype = this.fradragstype.toString(),
            fradragTilhører = this.fradragTilhører.toString(),
            eksterntBeløpFørRegulering = this.eksterntBeløpFørRegulering.toString(),
            vårtBeløpFørRegulering = this.vårtBeløpFørRegulering.toString(),

        )

        is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.SupplementHarFlereVedtaksperioderForFradrag -> ÅrsakTilManuellReguleringJson.SupplementHarFlereVedtaksperioderForFradrag(
            begrunnelse = this.begrunnelse,
            fradragstype = this.fradragstype.toString(),
            fradragTilhører = this.fradragTilhører.toString(),
            eksterneReguleringsvedtakperioder = this.eksterneReguleringsvedtakperioder.map { it.toJson() },
        )

        is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.SupplementInneholderIkkeFradraget -> ÅrsakTilManuellReguleringJson.SupplementInneholderIkkeFradraget(
            begrunnelse = this.begrunnelse,
            fradragstype = this.fradragstype.toString(),
            fradragTilhører = this.fradragTilhører.toString(),
        )

        is ÅrsakTilManuellRegulering.Historisk.ForventetInntektErStørreEnn0 -> ÅrsakTilManuellReguleringJson.ForventetInntektErStørreEnn0(
            begrunnelse = this.begrunnelse,
        )

        is ÅrsakTilManuellRegulering.Historisk.FradragMåHåndteresManuelt -> ÅrsakTilManuellReguleringJson.FradragMåHåndteresManuelt

        is ÅrsakTilManuellRegulering.Historisk.UtbetalingFeilet -> ÅrsakTilManuellReguleringJson.UtbetalingFeilet

        is ÅrsakTilManuellRegulering.Historisk.YtelseErMidlertidigStanset -> ÅrsakTilManuellReguleringJson.YtelseErMidlertidigStanset(
            begrunnelse = this.begrunnelse,
        )

        is ÅrsakTilManuellRegulering.VedtakstidslinjeErIkkeSammenhengende -> ÅrsakTilManuellReguleringJson.YtelseErMidlertidigStanset(
            begrunnelse = this.begrunnelse,
        )

        is ÅrsakTilManuellRegulering.YtelseErMidlertidigStanset -> ÅrsakTilManuellReguleringJson.YtelseErMidlertidigStanset(
            begrunnelse = this.begrunnelse,
        )
    }.let {
        serialize(it)
    }
}
