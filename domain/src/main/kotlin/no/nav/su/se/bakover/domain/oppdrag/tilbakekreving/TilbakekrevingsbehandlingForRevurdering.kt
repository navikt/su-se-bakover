package no.nav.su.se.bakover.domain.oppdrag.tilbakekreving

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.RåTilbakekrevingsvedtakForsendelse
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlag
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/**
 * Dersom en revurdering fører til til en feilutbetaling, må vi ta stilling til om vi skal kreve tilbake eller ikke.
 *
 * @property periode Vi støtter i førsteomgang kun en sammenhengende periode, som kan være hele eller deler av en revurderingsperiode.
 */
data class Tilbakekrev(
    override val id: UUID,
    override val opprettet: Tidspunkt,
    override val sakId: UUID,
    override val revurderingId: UUID,
    override val periode: Periode,
) : Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving.Avgjort,
    Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving {
    override fun fullførBehandling(): Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag {
        return AvventerKravgrunnlag(this)
    }
}

data class IkkeTilbakekrev(
    override val id: UUID,
    override val opprettet: Tidspunkt,
    override val sakId: UUID,
    override val revurderingId: UUID,
    override val periode: Periode,
) : Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving.Avgjort,
    Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving {
    override fun fullførBehandling(): Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag {
        return AvventerKravgrunnlag(this)
    }
}

data class IkkeAvgjort(
    override val id: UUID,
    override val opprettet: Tidspunkt,
    override val sakId: UUID,
    override val revurderingId: UUID,
    override val periode: Periode,
) : Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving.IkkeAvgjort,
    Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving {
    fun tilbakekrev(): Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving.Avgjort {
        return Tilbakekrev(
            id = id,
            opprettet = opprettet,
            sakId = sakId,
            revurderingId = revurderingId,
            periode = periode,
        )
    }

    fun ikkeTilbakekrev(): Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving.Avgjort {
        return IkkeTilbakekrev(
            id = id,
            opprettet = opprettet,
            sakId = sakId,
            revurderingId = revurderingId,
            periode = periode,
        )
    }
}

data class AvventerKravgrunnlag(
    override val avgjort: Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving.Avgjort,
) : Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag {
    override fun mottattKravgrunnlag(
        kravgrunnlag: RåttKravgrunnlag,
        kravgrunnlagMottatt: Tidspunkt,
        hentRevurdering: (revurderingId: UUID) -> IverksattRevurdering,
        kravgrunnlagMapper: (råttKravgrunnlag: RåttKravgrunnlag) -> Kravgrunnlag,
    ): Tilbakekrevingsbehandling.Ferdigbehandlet.MedKravgrunnlag.MottattKravgrunnlag {
        kontrollerKravgrunnlagMotRevurdering(
            råttKravgrunnlag = kravgrunnlag,
            hentRevurdering = hentRevurdering,
            kravgrunnlagMapper = kravgrunnlagMapper,
        )
        return MottattKravgrunnlag(
            avgjort = avgjort,
            kravgrunnlag = kravgrunnlag,
            kravgrunnlagMottatt = kravgrunnlagMottatt,
        )
    }

    private fun kontrollerKravgrunnlagMotRevurdering(
        råttKravgrunnlag: RåttKravgrunnlag,
        hentRevurdering: (revurderingId: UUID) -> IverksattRevurdering,
        kravgrunnlagMapper: (råttKravgrunnlag: RåttKravgrunnlag) -> Kravgrunnlag,
    ) {
        val simulering = hentRevurdering(avgjort.revurderingId).let {
            when (it) {
                is IverksattRevurdering.Innvilget -> {
                    it.simulering
                }

                is IverksattRevurdering.Opphørt -> {
                    it.simulering
                }
            }
        }

        val kravgrunnlag = kravgrunnlagMapper(råttKravgrunnlag)
        val fraSimulering = simulering.hentFeilutbetalteBeløp()
        val fraKravgrunnlag = kravgrunnlag.hentBeløpSkalTilbakekreves()

        if (fraSimulering != fraKravgrunnlag) throw IllegalStateException("Ikke samsvar mellom perioder og beløp i simulering og kravgrunnlag for revurdering:${avgjort.revurderingId}")
    }
}

data class MottattKravgrunnlag(
    override val avgjort: Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving.Avgjort,
    override val kravgrunnlag: RåttKravgrunnlag,
    override val kravgrunnlagMottatt: Tidspunkt,
) : Tilbakekrevingsbehandling.Ferdigbehandlet.MedKravgrunnlag.MottattKravgrunnlag {

    override fun lagTilbakekrevingsvedtak(mapper: (RåttKravgrunnlag) -> Kravgrunnlag): Tilbakekrevingsvedtak {
        val kravgrunnlag = mapper(kravgrunnlag)

        /**
         * Forskjellig resultat basert på valgene som er gjort i løpet av denne behandlingen, pt kun 1 valg.
         */
        return when (avgjort) {
            is Tilbakekrev -> {
                fullTilbakekreving(kravgrunnlag)
            }

            is IkkeTilbakekrev -> {
                ingenTilbakekreving(kravgrunnlag)
            }
        }
    }

    private fun fullTilbakekreving(kravgrunnlag: Kravgrunnlag): Tilbakekrevingsvedtak.FullTilbakekreving {
        return Tilbakekrevingsvedtak.FullTilbakekreving(
            vedtakId = kravgrunnlag.eksternVedtakId,
            ansvarligEnhet = "8020",
            kontrollFelt = kravgrunnlag.eksternKontrollfelt,
            behandler = kravgrunnlag.behandler,
            tilbakekrevingsperioder = kravgrunnlag.grunnlagsmåneder.map { grunnlagsperiode ->
                Tilbakekrevingsvedtak.Tilbakekrevingsperiode(
                    periode = grunnlagsperiode.måned,
                    renterBeregnes = false,
                    beløpRenter = BigDecimal.ZERO,
                    ytelse = Tilbakekrevingsvedtak.Tilbakekrevingsperiode.Tilbakekrevingsbeløp.TilbakekrevingsbeløpYtelse(
                        kodeKlasse = grunnlagsperiode.ytelse.klassekode,
                        beløpTidligereUtbetaling = BigDecimal(grunnlagsperiode.ytelse.beløpTidligereUtbetaling),
                        beløpNyUtbetaling = BigDecimal(grunnlagsperiode.ytelse.beløpNyUtbetaling),
                        beløpSomSkalTilbakekreves = BigDecimal(grunnlagsperiode.ytelse.beløpSkalTilbakekreves),
                        beløpSomIkkeTilbakekreves = BigDecimal.ZERO,
                        beløpSkatt = BigDecimal(grunnlagsperiode.ytelse.beløpSkalTilbakekreves)
                            .multiply(grunnlagsperiode.ytelse.skatteProsent)
                            .divide(BigDecimal("100"))
                            .setScale(0, RoundingMode.DOWN)
                            .min(grunnlagsperiode.betaltSkattForYtelsesgruppen),
                        tilbakekrevingsresultat = Tilbakekrevingsvedtak.Tilbakekrevingsresultat.FULL_TILBAKEKREVING,
                        skyld = Tilbakekrevingsvedtak.Skyld.BRUKER,
                    ),
                    feilutbetaling = mapDelkomponentForFeilutbetaling(grunnlagsperiode.feilutbetaling),
                )
            },
        )
    }

    private fun ingenTilbakekreving(kravgrunnlag: Kravgrunnlag): Tilbakekrevingsvedtak.IngenTilbakekreving {
        return Tilbakekrevingsvedtak.IngenTilbakekreving(
            vedtakId = kravgrunnlag.eksternVedtakId,
            ansvarligEnhet = "8020",
            kontrollFelt = kravgrunnlag.eksternKontrollfelt,
            // TODO behandler bør sannsynligvis være fra tilbakekrevingsbehandling/revurdering og ikke kravgrunnlaget
            behandler = kravgrunnlag.behandler,
            tilbakekrevingsperioder = kravgrunnlag.grunnlagsmåneder.map { grunnlagsperiode ->
                Tilbakekrevingsvedtak.Tilbakekrevingsperiode(
                    periode = grunnlagsperiode.måned,
                    renterBeregnes = false,
                    beløpRenter = BigDecimal.ZERO,
                    ytelse = Tilbakekrevingsvedtak.Tilbakekrevingsperiode.Tilbakekrevingsbeløp.TilbakekrevingsbeløpYtelse(
                        kodeKlasse = grunnlagsperiode.ytelse.klassekode,
                        beløpTidligereUtbetaling = BigDecimal(grunnlagsperiode.ytelse.beløpTidligereUtbetaling),
                        beløpNyUtbetaling = BigDecimal(grunnlagsperiode.ytelse.beløpNyUtbetaling),
                        beløpSomSkalTilbakekreves = BigDecimal.ZERO,
                        beløpSomIkkeTilbakekreves = BigDecimal(grunnlagsperiode.ytelse.beløpSkalTilbakekreves),
                        beløpSkatt = BigDecimal.ZERO,
                        tilbakekrevingsresultat = Tilbakekrevingsvedtak.Tilbakekrevingsresultat.INGEN_TILBAKEKREVING,
                        skyld = Tilbakekrevingsvedtak.Skyld.IKKE_FORDELT,
                    ),
                    feilutbetaling = mapDelkomponentForFeilutbetaling(grunnlagsperiode.feilutbetaling),
                )
            },
        )
    }

    private fun mapDelkomponentForFeilutbetaling(it: Kravgrunnlag.Grunnlagsmåned.Feilutbetaling): Tilbakekrevingsvedtak.Tilbakekrevingsperiode.Tilbakekrevingsbeløp.TilbakekrevingsbeløpFeilutbetaling {
        return Tilbakekrevingsvedtak.Tilbakekrevingsperiode.Tilbakekrevingsbeløp.TilbakekrevingsbeløpFeilutbetaling(
            kodeKlasse = it.klassekode,
            beløpTidligereUtbetaling = BigDecimal(it.beløpTidligereUtbetaling),
            beløpNyUtbetaling = BigDecimal(it.beløpNyUtbetaling),
            beløpSomSkalTilbakekreves = BigDecimal(it.beløpSkalTilbakekreves),
            beløpSomIkkeTilbakekreves = BigDecimal(it.beløpSkalIkkeTilbakekreves),
        )
    }

    override fun sendtTilbakekrevingsvedtak(
        tilbakekrevingsvedtakForsendelse: RåTilbakekrevingsvedtakForsendelse,
    ): Tilbakekrevingsbehandling.Ferdigbehandlet.MedKravgrunnlag.SendtTilbakekrevingsvedtak {
        return SendtTilbakekrevingsvedtak(
            avgjort = avgjort,
            kravgrunnlag = kravgrunnlag,
            kravgrunnlagMottatt = kravgrunnlagMottatt,
            tilbakekrevingsvedtakForsendelse = tilbakekrevingsvedtakForsendelse,
        )
    }
}

data class SendtTilbakekrevingsvedtak(
    override val avgjort: Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving.Avgjort,
    override val kravgrunnlag: RåttKravgrunnlag,
    override val kravgrunnlagMottatt: Tidspunkt,
    override val tilbakekrevingsvedtakForsendelse: RåTilbakekrevingsvedtakForsendelse,
) : Tilbakekrevingsbehandling.Ferdigbehandlet.MedKravgrunnlag.SendtTilbakekrevingsvedtak

data object IkkeBehovForTilbakekrevingUnderBehandling :
    Tilbakekrevingsbehandling.UnderBehandling.IkkeBehovForTilbakekreving {
    override fun fullførBehandling(): Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.IkkeBehovForTilbakekreving {
        return IkkeBehovForTilbakekrevingFerdigbehandlet
    }
}

data object IkkeBehovForTilbakekrevingFerdigbehandlet :
    Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.IkkeBehovForTilbakekreving

sealed interface Tilbakekrevingsbehandling {

    /**
     * Har saksbehandler vurdert saken dithen at penger skal tilbakekreves?
     */
    fun skalTilbakekreve(): Either<Unit, UnderBehandling.VurderTilbakekreving.Avgjort>

    sealed interface UnderBehandling : Tilbakekrevingsbehandling {

        override fun skalTilbakekreve(): Either<Unit, VurderTilbakekreving.Avgjort>

        fun fullførBehandling(): Ferdigbehandlet

        sealed interface VurderTilbakekreving : UnderBehandling {
            val id: UUID
            val opprettet: Tidspunkt
            val sakId: UUID
            val revurderingId: UUID
            val periode: Periode

            sealed interface Avgjort : VurderTilbakekreving {
                override fun fullførBehandling(): Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag

                override fun skalTilbakekreve(): Either<Unit, Avgjort> {
                    return when (this) {
                        is IkkeTilbakekrev -> Unit.left()
                        is Tilbakekrev -> this.right()
                    }
                }
            }

            sealed interface IkkeAvgjort : VurderTilbakekreving {
                override fun fullførBehandling(): Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag {
                    throw IllegalStateException("Må avgjøres før vurdering kan ferdigbehandles")
                }

                override fun skalTilbakekreve(): Either<Unit, Avgjort> {
                    throw IllegalStateException("Må avgjøres før vi vet om tilbakekreving skal gjennomføres!")
                }
            }
        }

        interface IkkeBehovForTilbakekreving : UnderBehandling {
            override fun fullførBehandling(): Ferdigbehandlet.UtenKravgrunnlag.IkkeBehovForTilbakekreving

            override fun skalTilbakekreve(): Either<Unit, VurderTilbakekreving.Avgjort> {
                return Unit.left()
            }
        }
    }

    sealed interface Ferdigbehandlet : Tilbakekrevingsbehandling {

        override fun skalTilbakekreve(): Either<Unit, UnderBehandling.VurderTilbakekreving.Avgjort>
        fun avventerKravgrunnlag(): Boolean {
            return this is AvventerKravgrunnlag
        }

        sealed interface UtenKravgrunnlag : Ferdigbehandlet {

            interface IkkeBehovForTilbakekreving : UtenKravgrunnlag {
                override fun skalTilbakekreve(): Either<Unit, UnderBehandling.VurderTilbakekreving.Avgjort> {
                    return Unit.left()
                }
            }

            sealed interface AvventerKravgrunnlag : UtenKravgrunnlag {
                val avgjort: UnderBehandling.VurderTilbakekreving.Avgjort

                override fun skalTilbakekreve(): Either<Unit, UnderBehandling.VurderTilbakekreving.Avgjort> {
                    return when (avgjort) {
                        is IkkeTilbakekrev -> Unit.left()
                        is Tilbakekrev -> avgjort.right()
                    }
                }

                fun mottattKravgrunnlag(
                    kravgrunnlag: RåttKravgrunnlag,
                    kravgrunnlagMottatt: Tidspunkt,
                    hentRevurdering: (revurderingId: UUID) -> IverksattRevurdering,
                    kravgrunnlagMapper: (råttKravgrunnlag: RåttKravgrunnlag) -> Kravgrunnlag,
                ): MedKravgrunnlag.MottattKravgrunnlag
            }
        }

        sealed interface MedKravgrunnlag : Ferdigbehandlet {
            val avgjort: UnderBehandling.VurderTilbakekreving.Avgjort
            val kravgrunnlag: RåttKravgrunnlag
            val kravgrunnlagMottatt: Tidspunkt

            override fun skalTilbakekreve(): Either<Unit, UnderBehandling.VurderTilbakekreving.Avgjort> {
                return when (avgjort) {
                    is IkkeTilbakekrev -> Unit.left()
                    is Tilbakekrev -> avgjort.right()
                }
            }

            sealed interface MottattKravgrunnlag : MedKravgrunnlag {
                fun lagTilbakekrevingsvedtak(mapper: (RåttKravgrunnlag) -> Kravgrunnlag): Tilbakekrevingsvedtak

                fun sendtTilbakekrevingsvedtak(
                    tilbakekrevingsvedtakForsendelse: RåTilbakekrevingsvedtakForsendelse,
                ): SendtTilbakekrevingsvedtak
            }

            sealed interface SendtTilbakekrevingsvedtak : MedKravgrunnlag {
                val tilbakekrevingsvedtakForsendelse: RåTilbakekrevingsvedtakForsendelse
            }
        }
    }
}
