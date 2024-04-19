package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.domain.tid.periode.PeriodeMedOptionalTilOgMed
import no.nav.su.se.bakover.common.domain.tid.periode.Perioder
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.math.BigDecimal

sealed interface ÅrsakTilManuellRegulering {
    val begrunnelse: String?

    /**
     * Historisk. Ikke bruk for nye reguleringer.
     * Historiske årsaker har ikke en begrunnelse. Nyere årsaker skal helst ha mer context til hvorfor dem gikk til manuell behandling
     */
    sealed interface Historisk : ÅrsakTilManuellRegulering {
        override val begrunnelse: String?

        /**
         * Bruk heller de mer spesifikke årsakene.
         */
        data object FradragMåHåndteresManuelt : Historisk {
            override val begrunnelse = null
        }

        /**
         * Bruk heller [ÅrsakTilManuellRegulering.YtelseErMidlertidigStanset]
         */
        data object YtelseErMidlertidigStanset : Historisk {
            override val begrunnelse = null
        }

        /**
         * Bruk heller [ÅrsakTilManuellRegulering.ForventetInntektErStørreEnn0]
         */
        data object ForventetInntektErStørreEnn0 : Historisk {
            override val begrunnelse = null
        }

        /**
         * Bruk heller [AutomatiskSendingTilUtbetalingFeilet]
         * Per 2024-04-19 har vi 12 av disse i prod.
         */
        data object UtbetalingFeilet : Historisk {
            override val begrunnelse = null
        }
    }

    // TODO test på alle disse
    sealed interface FradragMåHåndteresManuelt : ÅrsakTilManuellRegulering {
        val fradragstype: Fradragstype
        val fradragTilhører: FradragTilhører
        override val begrunnelse: String?

        data class BrukerManglerSupplement(
            override val fradragstype: Fradragstype,
            override val fradragTilhører: FradragTilhører,
            override val begrunnelse: String,
        ) : FradragMåHåndteresManuelt

        data class SupplementInneholderIkkeFradraget(
            override val fradragstype: Fradragstype,
            override val fradragTilhører: FradragTilhører,
            override val begrunnelse: String,
        ) : FradragMåHåndteresManuelt

        data class FinnesFlerePerioderAvFradrag(
            override val fradragstype: Fradragstype,
            override val fradragTilhører: FradragTilhører,
            override val begrunnelse: String,
        ) : FradragMåHåndteresManuelt

        data class FradragErUtenlandsinntekt(
            override val fradragstype: Fradragstype,
            override val fradragTilhører: FradragTilhører,
            override val begrunnelse: String,
        ) : FradragMåHåndteresManuelt

        data class SupplementHarFlereVedtaksperioderForFradrag(
            override val fradragstype: Fradragstype,
            override val fradragTilhører: FradragTilhører,
            override val begrunnelse: String,
            val eksterneReguleringsvedtakperioder: List<PeriodeMedOptionalTilOgMed>,
        ) : FradragMåHåndteresManuelt

        data class MismatchMellomBeløpFraSupplementOgFradrag(
            override val fradragstype: Fradragstype,
            override val fradragTilhører: FradragTilhører,
            override val begrunnelse: String,
            val eksterntBeløpFørRegulering: BigDecimal,
            val vårtBeløpFørRegulering: BigDecimal,
        ) : FradragMåHåndteresManuelt {
            val differanse = eksterntBeløpFørRegulering.subtract(vårtBeløpFørRegulering).abs()
        }

        data class BeløpErStørreEnForventet(
            override val fradragstype: Fradragstype,
            override val fradragTilhører: FradragTilhører,
            override val begrunnelse: String,
            val eksterntBeløpEtterRegulering: BigDecimal,
            val forventetBeløpEtterRegulering: BigDecimal,
        ) : FradragMåHåndteresManuelt {
            val differanse = eksterntBeløpEtterRegulering.subtract(forventetBeløpEtterRegulering).abs()
        }
    }

    data class YtelseErMidlertidigStanset(
        override val begrunnelse: String,
    ) : ÅrsakTilManuellRegulering

    data class ForventetInntektErStørreEnn0(
        override val begrunnelse: String,
    ) : ÅrsakTilManuellRegulering

    data class AutomatiskSendingTilUtbetalingFeilet(
        override val begrunnelse: String,
    ) : ÅrsakTilManuellRegulering

    /**
     * Per 2024-04-19 har ikke dette oppstått i produksjon.
     * Vi støtter ikke behandlinger/vedtak med hull.
     */
    data class VedtakstidslinjeErIkkeSammenhengende(
        override val begrunnelse: String,
    ) : ÅrsakTilManuellRegulering

    /**
     * Per 2024-04-19 har ikke dette oppstått i produksjon.
     * TODO jah: Dette kan støttes med dagens funksjonalitet dersom opphøret er før eller etter reguleringsperiode.
     *  Dersom opphøret er mellom 2 reguleringsperioder, trenger vi utvidet logikk i utbetalingsrutinen.
     */
    data class DelvisOpphør(
        val opphørsperioder: Perioder,
        override val begrunnelse: String?,
    ) : ÅrsakTilManuellRegulering
}
