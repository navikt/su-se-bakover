package tilbakekreving.domain.vurdert

import arrow.core.Either
import arrow.core.Nel
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.tid.periode.DatoIntervall
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import java.math.BigDecimal

/**
 * Krever at månedene er sorterte, uten duplikater, men vi aksepterer hull.
 * Inneholder kombinasjonen av [Vurderinger] og [tilbakekreving.domain.kravgrunnlag.Kravgrunnlag].
 * Skal kun trenge denne klassen for å sende tilbakekrevingsvedtaket, generere brevet og visning i frontend.
 */
data class VurderingerMedKrav private constructor(
    val perioder: Nel<PeriodevurderingMedKrav>,
    val saksnummer: Saksnummer,
    val eksternKravgrunnlagId: String,
    val eksternVedtakId: String,
    val eksternKontrollfelt: String,
    val bruttoSkalTilbakekreveSummert: Int,
    val nettoSkalTilbakekreveSummert: Int,
    val bruttoSkalIkkeTilbakekreveSummert: Int,
    val betaltSkattForYtelsesgruppenSummert: Int,
    val bruttoNyUtbetalingSummert: Int,
    val bruttoTidligereUtbetaltSummert: Int,
) : List<PeriodevurderingMedKrav> by perioder {

    init {
        perioder.map { it.periode }.let {
            require(it.sorted() == it) {
                "Vurderingene må være sortert."
            }
            it.zipWithNext { a, b ->
                require(!a.overlapper(b)) {
                    "Perioder kan ikke overlappe."
                }
            }
        }
    }

    companion object {
        fun utledFra(
            vurderinger: Vurderinger,
            kravgrunnlag: Kravgrunnlag,
        ): Either<KunneIkkeVurdereTilbakekrevingsbehandling, VurderingerMedKrav> {
            val perioderIKragrunnlaget = kravgrunnlag.grunnlagsperioder.map { it.periode }
            val perioderIVurderingene = vurderinger.perioder.map { it.periode }

            if (perioderIKragrunnlaget != perioderIVurderingene) {
                return KunneIkkeVurdereTilbakekrevingsbehandling.VurderingeneStemmerIkkeOverensMedKravgrunnlaget.left()
            }

            val perioder: Nel<PeriodevurderingMedKrav> = vurderinger.map {
                val kravgrunnlagsperiode = kravgrunnlag.forPeriode(it.periode)!!
                when (it.vurdering) {
                    Vurdering.SkalIkkeTilbakekreve -> {
                        PeriodevurderingMedKrav.SkalIkkeTilbakekreve(
                            periode = it.periode,
                            betaltSkattForYtelsesgruppen = kravgrunnlagsperiode.betaltSkattForYtelsesgruppen,
                            bruttoTidligereUtbetalt = kravgrunnlagsperiode.bruttoTidligereUtbetalt,
                            bruttoNyUtbetaling = kravgrunnlagsperiode.bruttoNyUtbetaling,
                            bruttoSkalIkkeTilbakekreve = kravgrunnlagsperiode.bruttoFeilutbetaling,
                            skatteProsent = kravgrunnlagsperiode.skatteProsent,
                        )
                    }

                    Vurdering.SkalTilbakekreve -> PeriodevurderingMedKrav.SkalTilbakekreve(
                        periode = it.periode,
                        betaltSkattForYtelsesgruppen = kravgrunnlagsperiode.betaltSkattForYtelsesgruppen,
                        bruttoTidligereUtbetalt = kravgrunnlagsperiode.bruttoTidligereUtbetalt,
                        bruttoNyUtbetaling = kravgrunnlagsperiode.bruttoNyUtbetaling,
                        bruttoSkalTilbakekreve = kravgrunnlagsperiode.bruttoFeilutbetaling,
                        nettoSkalTilbakekreve = kravgrunnlagsperiode.nettoFeilutbetaling,
                        skatteProsent = kravgrunnlagsperiode.skatteProsent,
                        skattSomGårTilReduksjon = kravgrunnlagsperiode.skattFeilutbetaling,
                    )
                }
            }.toNonEmptyList()
            return VurderingerMedKrav(
                perioder = perioder,
                saksnummer = kravgrunnlag.saksnummer,
                eksternKravgrunnlagId = kravgrunnlag.eksternKravgrunnlagId,
                eksternVedtakId = kravgrunnlag.eksternVedtakId,
                eksternKontrollfelt = kravgrunnlag.eksternKontrollfelt,
                bruttoSkalTilbakekreveSummert = perioder.sumOf { it.bruttoSkalTilbakekreve },
                nettoSkalTilbakekreveSummert = perioder.sumOf { it.nettoSkalTilbakekreve },
                bruttoSkalIkkeTilbakekreveSummert = perioder.sumOf { it.bruttoSkalIkkeTilbakekreve },
                betaltSkattForYtelsesgruppenSummert = perioder.sumOf { it.betaltSkattForYtelsesgruppen },
                bruttoNyUtbetalingSummert = perioder.sumOf { it.bruttoNyUtbetaling },
                bruttoTidligereUtbetaltSummert = perioder.sumOf { it.bruttoTidligereUtbetalt },
            ).right()
        }

        /**
         * Brukes også fra tester, men da kun som expected.
         */
        fun fraPersistert(
            perioder: Nel<PeriodevurderingMedKrav>,
            saksnummer: Saksnummer,
            eksternKravgrunnlagId: String,
            eksternVedtakId: String,
            eksternKontrollfelt: String,
        ): VurderingerMedKrav {
            return VurderingerMedKrav(
                perioder = perioder,
                saksnummer = saksnummer,
                eksternKravgrunnlagId = eksternKravgrunnlagId,
                eksternVedtakId = eksternVedtakId,
                eksternKontrollfelt = eksternKontrollfelt,
                bruttoSkalTilbakekreveSummert = perioder.sumOf { it.bruttoSkalTilbakekreve },
                nettoSkalTilbakekreveSummert = perioder.sumOf { it.nettoSkalTilbakekreve },
                bruttoSkalIkkeTilbakekreveSummert = perioder.sumOf { it.bruttoSkalIkkeTilbakekreve },
                betaltSkattForYtelsesgruppenSummert = perioder.sumOf { it.betaltSkattForYtelsesgruppen },
                bruttoNyUtbetalingSummert = perioder.sumOf { it.bruttoNyUtbetaling },
                bruttoTidligereUtbetaltSummert = perioder.sumOf { it.bruttoTidligereUtbetalt },
            )
        }
    }
}

sealed interface PeriodevurderingMedKrav {
    val periode: DatoIntervall
    val betaltSkattForYtelsesgruppen: Int
    val bruttoTidligereUtbetalt: Int
    val bruttoNyUtbetaling: Int
    val bruttoSkalTilbakekreve: Int
    val nettoSkalTilbakekreve: Int
    val bruttoSkalIkkeTilbakekreve: Int
    val skattSomGårTilReduksjon: Int
    val skatteProsent: BigDecimal

    data class SkalTilbakekreve(
        override val periode: DatoIntervall,
        override val betaltSkattForYtelsesgruppen: Int,
        override val bruttoTidligereUtbetalt: Int,
        override val bruttoNyUtbetaling: Int,
        override val bruttoSkalTilbakekreve: Int,
        override val nettoSkalTilbakekreve: Int,
        override val skatteProsent: BigDecimal,
        override val skattSomGårTilReduksjon: Int,
    ) : PeriodevurderingMedKrav {
        override val bruttoSkalIkkeTilbakekreve = 0

        init {
            require(skattSomGårTilReduksjon <= betaltSkattForYtelsesgruppen) {
                "Skatt som går til reduksjon ($skattSomGårTilReduksjon) kan ikke være større enn betalt skatt for ytelsesgruppen ($betaltSkattForYtelsesgruppen)."
            }
        }
    }

    data class SkalIkkeTilbakekreve(
        override val periode: DatoIntervall,
        override val betaltSkattForYtelsesgruppen: Int,
        override val bruttoTidligereUtbetalt: Int,
        override val bruttoNyUtbetaling: Int,
        override val bruttoSkalIkkeTilbakekreve: Int,
        override val skatteProsent: BigDecimal,
    ) : PeriodevurderingMedKrav {
        override val bruttoSkalTilbakekreve = 0
        override val skattSomGårTilReduksjon = 0
        override val nettoSkalTilbakekreve = 0
    }
}
