package no.nav.su.se.bakover.web.routes.regulering.json

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeMedOptionalTilOgMedJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeMedOptionalTilOgMedJson.Companion.toJson
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
    val begrunnelse: String?

    data object FradragMåHåndteresManuelt : ÅrsakTilManuellReguleringJson {
        override val begrunnelse: String? = null
    }

    data object UtbetalingFeilet : ÅrsakTilManuellReguleringJson {
        override val begrunnelse: String? = null
    }

    data class BrukerManglerSupplement(
        val fradragskategori: String,
        val fradragTilhører: String,
        override val begrunnelse: String,
    ) : ÅrsakTilManuellReguleringJson

    data class SupplementInneholderIkkeFradraget(
        val fradragskategori: String,
        val fradragTilhører: String,
        override val begrunnelse: String,
    ) : ÅrsakTilManuellReguleringJson

    data class FinnesFlerePerioderAvFradrag(
        val fradragskategori: String,
        val fradragTilhører: String,
        override val begrunnelse: String,
    ) : ÅrsakTilManuellReguleringJson

    data class FradragErUtenlandsinntekt(
        val fradragskategori: String,
        val fradragTilhører: String,
        override val begrunnelse: String,
    ) : ÅrsakTilManuellReguleringJson

    data class SupplementHarFlereVedtaksperioderForFradrag(
        val fradragskategori: String,
        val fradragTilhører: String,
        override val begrunnelse: String,
        val eksterneReguleringsvedtakperioder: List<PeriodeMedOptionalTilOgMedJson>,
    ) : ÅrsakTilManuellReguleringJson

    data class DifferanseFørRegulering(
        val fradragskategori: String,
        val fradragTilhører: String,
        override val begrunnelse: String,
        val eksternNettoBeløpFørRegulering: String,
        val eksternBruttoBeløpFørRegulering: String,
        val vårtBeløpFørRegulering: String,
    ) : ÅrsakTilManuellReguleringJson

    data class DifferanseEtterRegulering(
        val fradragskategori: String,
        val fradragTilhører: String,
        override val begrunnelse: String,
        val eksternNettoBeløpEtterRegulering: String,
        val eksternBruttoBeløpEtterRegulering: String,
        val forventetBeløpEtterRegulering: String,
        val vårtBeløpFørRegulering: String,
    ) : ÅrsakTilManuellReguleringJson

    data class FantIkkeVedtakForApril(
        val fradragskategori: String,
        val fradragTilhører: String,
        override val begrunnelse: String,
    ) : ÅrsakTilManuellReguleringJson

    data class YtelseErMidlertidigStanset(
        override val begrunnelse: String?,
    ) : ÅrsakTilManuellReguleringJson

    data class ForventetInntektErStørreEnn0(
        override val begrunnelse: String?,
    ) : ÅrsakTilManuellReguleringJson

    data class AutomatiskSendingTilUtbetalingFeilet(
        override val begrunnelse: String,
    ) : ÅrsakTilManuellReguleringJson

    data class VedtakstidslinjeErIkkeSammenhengende(
        override val begrunnelse: String,
    ) : ÅrsakTilManuellReguleringJson

    data class DelvisOpphør(
        val opphørsperioder: List<PeriodeJson>,
        override val begrunnelse: String?,
    ) : ÅrsakTilManuellReguleringJson

    companion object {
        internal fun Set<ÅrsakTilManuellRegulering>.toJson(): List<ÅrsakTilManuellReguleringJson> =
            this.map { it.toJson() }

        internal fun ÅrsakTilManuellRegulering.toJson(): ÅrsakTilManuellReguleringJson = when (this) {
            is ÅrsakTilManuellRegulering.AutomatiskSendingTilUtbetalingFeilet -> AutomatiskSendingTilUtbetalingFeilet(
                begrunnelse = this.begrunnelse,
            )

            is ÅrsakTilManuellRegulering.DelvisOpphør -> DelvisOpphør(
                opphørsperioder = this.opphørsperioder.map { it.toJson() },
                begrunnelse = this.begrunnelse,
            )

            is ÅrsakTilManuellRegulering.ForventetInntektErStørreEnn0 -> ForventetInntektErStørreEnn0(
                begrunnelse = this.begrunnelse,
            )

            is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.DifferanseEtterRegulering -> DifferanseEtterRegulering(
                begrunnelse = this.begrunnelse,
                fradragskategori = this.fradragskategori.toString(),
                fradragTilhører = this.fradragTilhører.toString(),
                forventetBeløpEtterRegulering = this.forventetBeløpEtterRegulering.toString(),
                eksternNettoBeløpEtterRegulering = this.eksternNettoBeløpEtterRegulering.toString(),
                eksternBruttoBeløpEtterRegulering = this.eksternBruttoBeløpEtterRegulering.toString(),
                vårtBeløpFørRegulering = this.vårtBeløpFørRegulering.toString(),

            )

            is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.BrukerManglerSupplement -> BrukerManglerSupplement(
                begrunnelse = this.begrunnelse,
                fradragskategori = this.fradragskategori.toString(),
                fradragTilhører = this.fradragTilhører.toString(),
            )

            is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FinnesFlerePerioderAvFradrag -> FinnesFlerePerioderAvFradrag(
                begrunnelse = this.begrunnelse,
                fradragskategori = this.fradragskategori.toString(),
                fradragTilhører = this.fradragTilhører.toString(),
            )

            is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FradragErUtenlandsinntekt -> FradragErUtenlandsinntekt(
                begrunnelse = this.begrunnelse,
                fradragskategori = this.fradragskategori.toString(),
                fradragTilhører = this.fradragTilhører.toString(),
            )

            is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.DifferanseFørRegulering -> DifferanseFørRegulering(
                begrunnelse = this.begrunnelse,
                fradragskategori = this.fradragskategori.toString(),
                fradragTilhører = this.fradragTilhører.toString(),
                vårtBeløpFørRegulering = this.vårtBeløpFørRegulering.toString(),
                eksternNettoBeløpFørRegulering = this.eksternNettoBeløpFørRegulering.toString(),
                eksternBruttoBeløpFørRegulering = this.eksternBruttoBeløpFørRegulering.toString(),
            )

            is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.SupplementHarFlereVedtaksperioderForFradrag -> SupplementHarFlereVedtaksperioderForFradrag(
                begrunnelse = this.begrunnelse,
                fradragskategori = this.fradragskategori.toString(),
                fradragTilhører = this.fradragTilhører.toString(),
                eksterneReguleringsvedtakperioder = this.eksterneReguleringsvedtakperioder.map { it.toJson() },
            )

            is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.SupplementInneholderIkkeFradraget -> SupplementInneholderIkkeFradraget(
                begrunnelse = this.begrunnelse,
                fradragskategori = this.fradragskategori.toString(),
                fradragTilhører = this.fradragTilhører.toString(),
            )

            is ÅrsakTilManuellRegulering.Historisk.ForventetInntektErStørreEnn0 -> ForventetInntektErStørreEnn0(
                begrunnelse = this.begrunnelse,
            )

            is ÅrsakTilManuellRegulering.Historisk.FradragMåHåndteresManuelt -> FradragMåHåndteresManuelt

            is ÅrsakTilManuellRegulering.Historisk.UtbetalingFeilet -> UtbetalingFeilet

            is ÅrsakTilManuellRegulering.VedtakstidslinjeErIkkeSammenhengende -> VedtakstidslinjeErIkkeSammenhengende(
                begrunnelse = this.begrunnelse,
            )

            is ÅrsakTilManuellRegulering.Historisk.YtelseErMidlertidigStanset -> YtelseErMidlertidigStanset(
                begrunnelse = this.begrunnelse,
            )

            is ÅrsakTilManuellRegulering.YtelseErMidlertidigStanset -> YtelseErMidlertidigStanset(
                begrunnelse = this.begrunnelse,
            )

            is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FantIkkeVedtakForApril -> FantIkkeVedtakForApril(
                begrunnelse = this.begrunnelse,
                fradragskategori = this.fradragskategori.toString(),
                fradragTilhører = this.fradragTilhører.toString(),
            )
        }
    }
}
