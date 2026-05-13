package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.domain.tid.periode.PeriodeMedOptionalTilOgMed
import no.nav.su.se.bakover.common.domain.tid.periode.Perioder
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.math.BigDecimal

sealed interface ÅrsakTilManuellRegulering {
    val begrunnelse: String?
    val kategori: ÅrsakTilManuellReguleringKategori

    data class ManglerRegulertBeløpForFradrag(
        val fradragskategori: Fradragstype.Kategori,
        val fradragTilhører: FradragTilhører,
        override val begrunnelse: String = "Fradragstype $fradragskategori har ingen løsning for å hente regulert beløp",
        override val kategori: ÅrsakTilManuellReguleringKategori = ÅrsakTilManuellReguleringKategori.ManglerRegulertBeløpForFradrag,
    ) : ÅrsakTilManuellRegulering

    data class ManglerIeuFraPesys(
        override val begrunnelse: String = "Mangler IEU fra Pesys. Trolig på grunn av manuell behandling i Pesys.",
        override val kategori: ÅrsakTilManuellReguleringKategori = ÅrsakTilManuellReguleringKategori.ManglerIeuFraPesys,
    ) : ÅrsakTilManuellRegulering

    data class YtelseErMidlertidigStanset(
        override val begrunnelse: String,
    ) : ÅrsakTilManuellRegulering {
        override val kategori: ÅrsakTilManuellReguleringKategori =
            ÅrsakTilManuellReguleringKategori.YtelseErMidlertidigStanset
    }

    data class EtAutomatiskFradragHarFremtidigPeriode(
        override val begrunnelse: String? = "Et fradrag som som skal reguleres automatisk kan ikke være frem i tid",
    ) : ÅrsakTilManuellRegulering {
        override val kategori: ÅrsakTilManuellReguleringKategori =
            ÅrsakTilManuellReguleringKategori.EtAutomatiskFradragHarFremtidigPeriode
    }

    data class UgyldigePerioderForAutomatiskRegulering(
        override val begrunnelse: String,
    ) : ÅrsakTilManuellRegulering {
        override val kategori: ÅrsakTilManuellReguleringKategori =
            ÅrsakTilManuellReguleringKategori.UgyldigePerioderForAutomatiskRegulering
    }

    /**
     * Historisk. Ikke bruk for nye reguleringer.
     */
    sealed interface Historisk : ÅrsakTilManuellRegulering {
        override val begrunnelse: String?

        /**
         * Bruk heller [ÅrsakTilManuellRegulering.YtelseErMidlertidigStanset]
         */
        data object YtelseErMidlertidigStanset : Historisk {
            override val begrunnelse = null
            override val kategori: ÅrsakTilManuellReguleringKategori =
                ÅrsakTilManuellReguleringKategori.YtelseErMidlertidigStanset
        }

        data class ForventetInntektErStørreEnn0(
            override val begrunnelse: String? = null,
        ) : Historisk {
            override val kategori: ÅrsakTilManuellReguleringKategori =
                ÅrsakTilManuellReguleringKategori.ForventetInntektErStørreEnn0
        }

        /**
         * Per 2024-04-19 har vi 12 av disse i prod.
         */
        data object UtbetalingFeilet : Historisk {
            override val begrunnelse = null
            override val kategori: ÅrsakTilManuellReguleringKategori =
                ÅrsakTilManuellReguleringKategori.UtbetalingFeilet
        }

        data class AutomatiskSendingTilUtbetalingFeilet(
            override val begrunnelse: String,
        ) : Historisk {
            override val kategori: ÅrsakTilManuellReguleringKategori =
                ÅrsakTilManuellReguleringKategori.AutomatiskSendingTilUtbetalingFeilet
        }

        /**
         * Per 2024-04-19 har ikke dette oppstått i produksjon.
         * Vi støtter ikke behandlinger/vedtak med hull.
         */
        data class VedtakstidslinjeErIkkeSammenhengende(
            override val begrunnelse: String,
        ) : Historisk {
            override val kategori: ÅrsakTilManuellReguleringKategori =
                ÅrsakTilManuellReguleringKategori.VedtakstidslinjeErIkkeSammenhengende
        }

        /**
         * Per 2024-04-19 har ikke dette oppstått i produksjon.
         * TODO jah: Dette kan støttes med dagens funksjonalitet dersom opphøret er før eller etter reguleringsperiode.
         *  Dersom opphøret er mellom 2 reguleringsperioder, trenger vi utvidet logikk i utbetalingsrutinen.
         */
        data class DelvisOpphør(
            val opphørsperioder: Perioder,
            override val begrunnelse: String?,
        ) : Historisk {
            override val kategori: ÅrsakTilManuellReguleringKategori = ÅrsakTilManuellReguleringKategori.DelvisOpphør
        }

        sealed interface FradragMåHåndteresManuelt : Historisk {
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

            data class MerEnn1Eps(
                override val fradragskategori: Fradragstype.Kategori,
                override val fradragTilhører: FradragTilhører,
                override val begrunnelse: String,
            ) : FradragMåHåndteresManuelt {
                override val kategori: ÅrsakTilManuellReguleringKategori = ÅrsakTilManuellReguleringKategori.MerEnn1Eps
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

                val differanse: BigDecimal =
                    eksternNettoBeløpEtterRegulering.subtract(forventetBeløpEtterRegulering).abs()
            }

            data class FantIkkeVedtakForApril(
                override val begrunnelse: String,
                override val fradragTilhører: FradragTilhører,
                override val fradragskategori: Fradragstype.Kategori,
            ) : FradragMåHåndteresManuelt {
                override val kategori: ÅrsakTilManuellReguleringKategori =
                    ÅrsakTilManuellReguleringKategori.FantIkkeVedtakForApril
            }

            data object Gammel : Historisk {
                override val begrunnelse = null
                override val kategori: ÅrsakTilManuellReguleringKategori =
                    ÅrsakTilManuellReguleringKategori.FradragMåHåndteresManuelt
            }
        }
    }
}
