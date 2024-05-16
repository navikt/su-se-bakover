package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.domain.tid.periode.PeriodeMedOptionalTilOgMed
import no.nav.su.se.bakover.common.domain.tid.periode.Perioder
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.math.BigDecimal

sealed interface ÅrsakTilManuellRegulering {
    val begrunnelse: String?

    // TODO - test at hvert av objektene har riktig kategori knyttet til seg
    val kategori: ÅrsakTilManuellReguleringKategori

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
            override val kategori: ÅrsakTilManuellReguleringKategori =
                ÅrsakTilManuellReguleringKategori.FradragMåHåndteresManuelt
        }

        /**
         * Bruk heller [ÅrsakTilManuellRegulering.YtelseErMidlertidigStanset]
         */
        data object YtelseErMidlertidigStanset : Historisk {
            override val begrunnelse = null
            override val kategori: ÅrsakTilManuellReguleringKategori =
                ÅrsakTilManuellReguleringKategori.YtelseErMidlertidigStanset
        }

        /**
         * Bruk heller [ÅrsakTilManuellRegulering.ForventetInntektErStørreEnn0]
         */
        data object ForventetInntektErStørreEnn0 : Historisk {
            override val begrunnelse = null
            override val kategori: ÅrsakTilManuellReguleringKategori =
                ÅrsakTilManuellReguleringKategori.ForventetInntektErStørreEnn0
        }

        /**
         * Bruk heller [AutomatiskSendingTilUtbetalingFeilet]
         * Per 2024-04-19 har vi 12 av disse i prod.
         */
        data object UtbetalingFeilet : Historisk {
            override val begrunnelse = null
            override val kategori: ÅrsakTilManuellReguleringKategori =
                ÅrsakTilManuellReguleringKategori.UtbetalingFeilet
        }
    }

    sealed interface FradragMåHåndteresManuelt : ÅrsakTilManuellRegulering {
        val fradragskategori: Fradragstype.Kategori
        val fradragTilhører: FradragTilhører
        override val begrunnelse: String?

        data class BrukerManglerSupplement(
            override val fradragskategori: Fradragstype.Kategori,
            override val fradragTilhører: FradragTilhører,
            override val begrunnelse: String,
        ) : FradragMåHåndteresManuelt {
            override val kategori: ÅrsakTilManuellReguleringKategori =
                ÅrsakTilManuellReguleringKategori.BrukerManglerSupplement
        }

        data class SupplementInneholderIkkeFradraget(
            override val fradragskategori: Fradragstype.Kategori,
            override val fradragTilhører: FradragTilhører,
            override val begrunnelse: String,
        ) : FradragMåHåndteresManuelt {
            override val kategori: ÅrsakTilManuellReguleringKategori =
                ÅrsakTilManuellReguleringKategori.SupplementInneholderIkkeFradraget
        }

        data class FinnesFlerePerioderAvFradrag(
            override val fradragskategori: Fradragstype.Kategori,
            override val fradragTilhører: FradragTilhører,
            override val begrunnelse: String,
        ) : FradragMåHåndteresManuelt {
            override val kategori: ÅrsakTilManuellReguleringKategori =
                ÅrsakTilManuellReguleringKategori.FinnesFlerePerioderAvFradrag
        }

        data class FradragErUtenlandsinntekt(
            override val fradragskategori: Fradragstype.Kategori,
            override val fradragTilhører: FradragTilhører,
            override val begrunnelse: String,
        ) : FradragMåHåndteresManuelt {
            override val kategori: ÅrsakTilManuellReguleringKategori =
                ÅrsakTilManuellReguleringKategori.FradragErUtenlandsinntekt
        }

        data class SupplementHarFlereVedtaksperioderForFradrag(
            override val fradragskategori: Fradragstype.Kategori,
            override val fradragTilhører: FradragTilhører,
            override val begrunnelse: String,
            val eksterneReguleringsvedtakperioder: List<PeriodeMedOptionalTilOgMed>,
        ) : FradragMåHåndteresManuelt {
            override val kategori: ÅrsakTilManuellReguleringKategori =
                ÅrsakTilManuellReguleringKategori.SupplementHarFlereVedtaksperioderForFradrag
        }

        data class DifferanseFørRegulering(
            override val fradragskategori: Fradragstype.Kategori,
            override val fradragTilhører: FradragTilhører,
            override val begrunnelse: String,
            val eksternBruttoBeløpFørRegulering: BigDecimal,
            val eksternNettoBeløpFørRegulering: BigDecimal,
            val vårtBeløpFørRegulering: BigDecimal,
        ) : FradragMåHåndteresManuelt {
            override val kategori: ÅrsakTilManuellReguleringKategori =
                ÅrsakTilManuellReguleringKategori.DifferanseFørRegulering
            val differanse: BigDecimal = eksternNettoBeløpFørRegulering.subtract(vårtBeløpFørRegulering).abs()
        }

        data class DifferanseEtterRegulering(
            override val fradragskategori: Fradragstype.Kategori,
            override val fradragTilhører: FradragTilhører,
            override val begrunnelse: String,
            val eksternBruttoBeløpEtterRegulering: BigDecimal,
            val eksternNettoBeløpEtterRegulering: BigDecimal,
            val forventetBeløpEtterRegulering: BigDecimal,
            val vårtBeløpFørRegulering: BigDecimal,
        ) : FradragMåHåndteresManuelt {
            override val kategori: ÅrsakTilManuellReguleringKategori =
                ÅrsakTilManuellReguleringKategori.DifferanseEtterRegulering

            val differanse: BigDecimal = eksternNettoBeløpEtterRegulering.subtract(forventetBeløpEtterRegulering).abs()
        }

        data class FantIkkeVedtakForApril(
            override val begrunnelse: String,
            override val fradragTilhører: FradragTilhører,
            override val fradragskategori: Fradragstype.Kategori,
        ) : FradragMåHåndteresManuelt {
            override val kategori: ÅrsakTilManuellReguleringKategori = ÅrsakTilManuellReguleringKategori.FantIkkeVedtakForApril
        }
    }

    data class YtelseErMidlertidigStanset(
        override val begrunnelse: String,
    ) : ÅrsakTilManuellRegulering {
        override val kategori: ÅrsakTilManuellReguleringKategori = ÅrsakTilManuellReguleringKategori.YtelseErMidlertidigStanset
    }

    data class ForventetInntektErStørreEnn0(
        override val begrunnelse: String,
    ) : ÅrsakTilManuellRegulering {
        override val kategori: ÅrsakTilManuellReguleringKategori = ÅrsakTilManuellReguleringKategori.ForventetInntektErStørreEnn0
    }

    data class AutomatiskSendingTilUtbetalingFeilet(
        override val begrunnelse: String,
    ) : ÅrsakTilManuellRegulering {
        override val kategori: ÅrsakTilManuellReguleringKategori = ÅrsakTilManuellReguleringKategori.AutomatiskSendingTilUtbetalingFeilet
    }

    /**
     * Per 2024-04-19 har ikke dette oppstått i produksjon.
     * Vi støtter ikke behandlinger/vedtak med hull.
     */
    data class VedtakstidslinjeErIkkeSammenhengende(
        override val begrunnelse: String,
    ) : ÅrsakTilManuellRegulering {
        override val kategori: ÅrsakTilManuellReguleringKategori = ÅrsakTilManuellReguleringKategori.VedtakstidslinjeErIkkeSammenhengende
    }

    /**
     * Per 2024-04-19 har ikke dette oppstått i produksjon.
     * TODO jah: Dette kan støttes med dagens funksjonalitet dersom opphøret er før eller etter reguleringsperiode.
     *  Dersom opphøret er mellom 2 reguleringsperioder, trenger vi utvidet logikk i utbetalingsrutinen.
     */
    data class DelvisOpphør(
        val opphørsperioder: Perioder,
        override val begrunnelse: String?,
    ) : ÅrsakTilManuellRegulering {
        override val kategori: ÅrsakTilManuellReguleringKategori = ÅrsakTilManuellReguleringKategori.DelvisOpphør
    }
}
