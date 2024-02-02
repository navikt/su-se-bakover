package no.nav.su.se.bakover.domain.oppdrag.tilbakekrevingUnderRevurdering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Beløp
import no.nav.su.se.bakover.common.MånedBeløp
import no.nav.su.se.bakover.common.Månedsbeløp
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.rått.RåTilbakekrevingsvedtakForsendelse
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
    override val revurderingId: RevurderingId,
    override val periode: Periode,
) : TilbakekrevingsbehandlingUnderRevurdering.UnderBehandling.VurderTilbakekreving.Avgjort,
    TilbakekrevingsbehandlingUnderRevurdering.UnderBehandling.VurderTilbakekreving {
    override fun fullførBehandling(): TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag {
        return AvventerKravgrunnlag(this)
    }
}

data class IkkeTilbakekrev(
    override val id: UUID,
    override val opprettet: Tidspunkt,
    override val sakId: UUID,
    override val revurderingId: RevurderingId,
    override val periode: Periode,
) : TilbakekrevingsbehandlingUnderRevurdering.UnderBehandling.VurderTilbakekreving.Avgjort,
    TilbakekrevingsbehandlingUnderRevurdering.UnderBehandling.VurderTilbakekreving {
    override fun fullførBehandling(): TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag {
        return AvventerKravgrunnlag(this)
    }
}

data class IkkeAvgjort(
    override val id: UUID,
    override val opprettet: Tidspunkt,
    override val sakId: UUID,
    override val revurderingId: RevurderingId,
    override val periode: Periode,
) : TilbakekrevingsbehandlingUnderRevurdering.UnderBehandling.VurderTilbakekreving.IkkeAvgjort,
    TilbakekrevingsbehandlingUnderRevurdering.UnderBehandling.VurderTilbakekreving {
    fun tilbakekrev(): TilbakekrevingsbehandlingUnderRevurdering.UnderBehandling.VurderTilbakekreving.Avgjort {
        return Tilbakekrev(
            id = id,
            opprettet = opprettet,
            sakId = sakId,
            revurderingId = revurderingId,
            periode = periode,
        )
    }

    fun ikkeTilbakekrev(): TilbakekrevingsbehandlingUnderRevurdering.UnderBehandling.VurderTilbakekreving.Avgjort {
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
    override val avgjort: TilbakekrevingsbehandlingUnderRevurdering.UnderBehandling.VurderTilbakekreving.Avgjort,
) : TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag {
    override fun mottattKravgrunnlag(
        kravgrunnlag: Kravgrunnlag,
        kravgrunnlagMottatt: Tidspunkt,
        hentRevurdering: (revurderingId: RevurderingId) -> IverksattRevurdering,
    ): TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.MedKravgrunnlag.MottattKravgrunnlag {
        unsafeKontrollerKravgrunnlagMotRevurdering(
            kravgrunnlag = kravgrunnlag,
            hentRevurdering = hentRevurdering,
        )
        return MottattKravgrunnlag(
            avgjort = avgjort,
            kravgrunnlag = kravgrunnlag,
            kravgrunnlagMottatt = kravgrunnlagMottatt,
        )
    }

    /**
     * @throws IllegalArgumentException dersom perioden til kravgrunnlaget ikke er en [Måned]
     */
    private fun unsafeKontrollerKravgrunnlagMotRevurdering(
        kravgrunnlag: Kravgrunnlag,
        hentRevurdering: (revurderingId: RevurderingId) -> IverksattRevurdering,
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

        val fraSimulering = simulering.hentFeilutbetalteBeløp()
        val fraKravgrunnlag = kravgrunnlag.unsafeHentBeløpSkalTilbakekreves()

        if (fraSimulering != fraKravgrunnlag) {
            throw IllegalStateException("Ikke samsvar mellom perioder og beløp i simulering og kravgrunnlag for revurdering:${avgjort.revurderingId}. Simulering: $fraSimulering, Kravgrunnlag: $fraKravgrunnlag")
        }
    }
}

data class MottattKravgrunnlag(
    override val avgjort: TilbakekrevingsbehandlingUnderRevurdering.UnderBehandling.VurderTilbakekreving.Avgjort,
    override val kravgrunnlag: Kravgrunnlag,
    override val kravgrunnlagMottatt: Tidspunkt,
) : TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.MedKravgrunnlag.MottattKravgrunnlag {

    override fun lagTilbakekrevingsvedtak(kravgrunnlag: Kravgrunnlag): TilbakekrevingsvedtakUnderRevurdering {
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

    private fun fullTilbakekreving(kravgrunnlag: Kravgrunnlag): TilbakekrevingsvedtakUnderRevurdering.FullTilbakekreving {
        return TilbakekrevingsvedtakUnderRevurdering.FullTilbakekreving(
            vedtakId = kravgrunnlag.eksternVedtakId,
            ansvarligEnhet = "8020",
            kontrollFelt = kravgrunnlag.eksternKontrollfelt,
            behandler = kravgrunnlag.behandler,
            tilbakekrevingsperioder = kravgrunnlag.grunnlagsperioder.map { grunnlagsperiode ->
                TilbakekrevingsvedtakUnderRevurdering.Tilbakekrevingsperiode(
                    periode = grunnlagsperiode.unsafeTilMåned(),
                    renterBeregnes = false,
                    beløpRenter = BigDecimal.ZERO,
                    ytelse = mapDelkomponentForYtelse(
                        grunnlagsperiode = grunnlagsperiode,
                        betaltSkattForYtelsesgruppen = grunnlagsperiode.betaltSkattForYtelsesgruppen,
                        resultat = TilbakekrevingsvedtakUnderRevurdering.Tilbakekrevingsresultat.FULL_TILBAKEKREVING,
                    ),
                    feilutbetaling = mapDelkomponentForFeilutbetaling(grunnlagsperiode),
                )
            },
        )
    }

    private fun ingenTilbakekreving(kravgrunnlag: Kravgrunnlag): TilbakekrevingsvedtakUnderRevurdering.IngenTilbakekreving {
        return TilbakekrevingsvedtakUnderRevurdering.IngenTilbakekreving(
            vedtakId = kravgrunnlag.eksternVedtakId,
            ansvarligEnhet = "8020",
            kontrollFelt = kravgrunnlag.eksternKontrollfelt,
            // TODO behandler bør sannsynligvis være fra tilbakekrevingsbehandling/revurdering og ikke kravgrunnlaget
            behandler = kravgrunnlag.behandler,
            tilbakekrevingsperioder = kravgrunnlag.grunnlagsperioder.map { grunnlagsperiode ->
                TilbakekrevingsvedtakUnderRevurdering.Tilbakekrevingsperiode(
                    periode = grunnlagsperiode.unsafeTilMåned(),
                    renterBeregnes = false,
                    beløpRenter = BigDecimal.ZERO,
                    ytelse = mapDelkomponentForYtelse(
                        grunnlagsperiode = grunnlagsperiode,
                        betaltSkattForYtelsesgruppen = grunnlagsperiode.betaltSkattForYtelsesgruppen,
                        resultat = TilbakekrevingsvedtakUnderRevurdering.Tilbakekrevingsresultat.INGEN_TILBAKEKREVING,
                    ),
                    feilutbetaling = mapDelkomponentForFeilutbetaling(grunnlagsperiode),
                )
            },
        )
    }

    private fun mapDelkomponentForYtelse(
        grunnlagsperiode: Kravgrunnlag.Grunnlagsperiode,
        betaltSkattForYtelsesgruppen: Int,
        resultat: TilbakekrevingsvedtakUnderRevurdering.Tilbakekrevingsresultat,
    ): TilbakekrevingsvedtakUnderRevurdering.Tilbakekrevingsperiode.Tilbakekrevingsbeløp.TilbakekrevingsbeløpYtelse {
        return TilbakekrevingsvedtakUnderRevurdering.Tilbakekrevingsperiode.Tilbakekrevingsbeløp.TilbakekrevingsbeløpYtelse(
            beløpTidligereUtbetaling = BigDecimal(grunnlagsperiode.bruttoTidligereUtbetalt),
            beløpNyUtbetaling = BigDecimal(grunnlagsperiode.bruttoNyUtbetaling),
            beløpSomSkalTilbakekreves = when (resultat) {
                TilbakekrevingsvedtakUnderRevurdering.Tilbakekrevingsresultat.FULL_TILBAKEKREVING -> BigDecimal(
                    grunnlagsperiode.bruttoFeilutbetaling,
                )

                TilbakekrevingsvedtakUnderRevurdering.Tilbakekrevingsresultat.INGEN_TILBAKEKREVING -> BigDecimal.ZERO
            },
            beløpSomIkkeTilbakekreves = when (resultat) {
                TilbakekrevingsvedtakUnderRevurdering.Tilbakekrevingsresultat.FULL_TILBAKEKREVING -> BigDecimal.ZERO
                TilbakekrevingsvedtakUnderRevurdering.Tilbakekrevingsresultat.INGEN_TILBAKEKREVING -> BigDecimal(
                    grunnlagsperiode.bruttoFeilutbetaling,
                )
            },
            beløpSkatt = when (resultat) {
                TilbakekrevingsvedtakUnderRevurdering.Tilbakekrevingsresultat.FULL_TILBAKEKREVING -> BigDecimal(
                    grunnlagsperiode.bruttoFeilutbetaling,
                )
                    .multiply(grunnlagsperiode.skatteProsent)
                    .divide(BigDecimal("100"))
                    .min(BigDecimal(betaltSkattForYtelsesgruppen))
                    .setScale(0, RoundingMode.DOWN)

                TilbakekrevingsvedtakUnderRevurdering.Tilbakekrevingsresultat.INGEN_TILBAKEKREVING -> BigDecimal.ZERO
            },
            tilbakekrevingsresultat = resultat,
            skyld = when (resultat) {
                TilbakekrevingsvedtakUnderRevurdering.Tilbakekrevingsresultat.FULL_TILBAKEKREVING -> TilbakekrevingsvedtakUnderRevurdering.Skyld.BRUKER
                TilbakekrevingsvedtakUnderRevurdering.Tilbakekrevingsresultat.INGEN_TILBAKEKREVING -> TilbakekrevingsvedtakUnderRevurdering.Skyld.IKKE_FORDELT
            },
        )
    }

    private fun mapDelkomponentForFeilutbetaling(
        grunnlagsperiode: Kravgrunnlag.Grunnlagsperiode,
    ): TilbakekrevingsvedtakUnderRevurdering.Tilbakekrevingsperiode.Tilbakekrevingsbeløp.TilbakekrevingsbeløpFeilutbetaling {
        return TilbakekrevingsvedtakUnderRevurdering.Tilbakekrevingsperiode.Tilbakekrevingsbeløp.TilbakekrevingsbeløpFeilutbetaling(
            beløpTidligereUtbetaling = BigDecimal.ZERO,
            beløpNyUtbetaling = grunnlagsperiode.bruttoFeilutbetaling.toBigDecimal(),
            beløpSomSkalTilbakekreves = BigDecimal.ZERO,
            beløpSomIkkeTilbakekreves = BigDecimal.ZERO,
        )
    }

    override fun sendtTilbakekrevingsvedtak(
        tilbakekrevingsvedtakForsendelse: RåTilbakekrevingsvedtakForsendelse,
    ): TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.MedKravgrunnlag.SendtTilbakekrevingsvedtak {
        return SendtTilbakekrevingsvedtak(
            avgjort = avgjort,
            kravgrunnlag = kravgrunnlag,
            kravgrunnlagMottatt = kravgrunnlagMottatt,
            tilbakekrevingsvedtakForsendelse = tilbakekrevingsvedtakForsendelse,
        )
    }
}

data class SendtTilbakekrevingsvedtak(
    override val avgjort: TilbakekrevingsbehandlingUnderRevurdering.UnderBehandling.VurderTilbakekreving.Avgjort,
    override val kravgrunnlag: Kravgrunnlag,
    override val kravgrunnlagMottatt: Tidspunkt,
    override val tilbakekrevingsvedtakForsendelse: RåTilbakekrevingsvedtakForsendelse,
) : TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.MedKravgrunnlag.SendtTilbakekrevingsvedtak

data object IkkeBehovForTilbakekrevingUnderBehandling :
    TilbakekrevingsbehandlingUnderRevurdering.UnderBehandling.IkkeBehovForTilbakekreving {
    override fun fullførBehandling(): TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.UtenKravgrunnlag.IkkeBehovForTilbakekreving {
        return IkkeBehovForTilbakekrevingFerdigbehandlet
    }
}

data object IkkeBehovForTilbakekrevingFerdigbehandlet :
    TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.UtenKravgrunnlag.IkkeBehovForTilbakekreving

sealed interface TilbakekrevingsbehandlingUnderRevurdering {

    /**
     * Har saksbehandler vurdert saken dithen at penger skal tilbakekreves?
     */
    fun skalTilbakekreve(): Either<Unit, UnderBehandling.VurderTilbakekreving.Avgjort>

    sealed interface UnderBehandling : TilbakekrevingsbehandlingUnderRevurdering {

        override fun skalTilbakekreve(): Either<Unit, VurderTilbakekreving.Avgjort>

        fun fullførBehandling(): Ferdigbehandlet

        sealed interface VurderTilbakekreving : UnderBehandling {
            val id: UUID
            val opprettet: Tidspunkt
            val sakId: UUID
            val revurderingId: RevurderingId
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

    sealed interface Ferdigbehandlet : TilbakekrevingsbehandlingUnderRevurdering {

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
                    kravgrunnlag: Kravgrunnlag,
                    kravgrunnlagMottatt: Tidspunkt,
                    hentRevurdering: (revurderingId: RevurderingId) -> IverksattRevurdering,
                ): MedKravgrunnlag.MottattKravgrunnlag
            }
        }

        sealed interface MedKravgrunnlag : Ferdigbehandlet {
            val avgjort: UnderBehandling.VurderTilbakekreving.Avgjort
            val kravgrunnlag: Kravgrunnlag
            val kravgrunnlagMottatt: Tidspunkt

            override fun skalTilbakekreve(): Either<Unit, UnderBehandling.VurderTilbakekreving.Avgjort> {
                return when (avgjort) {
                    is IkkeTilbakekrev -> Unit.left()
                    is Tilbakekrev -> avgjort.right()
                }
            }

            sealed interface MottattKravgrunnlag : MedKravgrunnlag {
                fun lagTilbakekrevingsvedtak(kravgrunnlag: Kravgrunnlag): TilbakekrevingsvedtakUnderRevurdering

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

/**
 * TODO jah: Sletter når vi fjerner tilbakekreving under revurdering
 *
 * @throws IllegalArgumentException dersom perioden ikke er en [Måned]
 */
private fun Kravgrunnlag.unsafeHentBeløpSkalTilbakekreves(): Månedsbeløp = Månedsbeløp(
    grunnlagsperioder
        .map { it.unsafeHentBruttoFeilutbetalingSomMånedBeløp() }
        .filter { it.sum() > 0 },
)

/**
 * TODO jah: Sletter når vi fjerner tilbakekreving under revurdering
 *
 * @throws IllegalArgumentException dersom perioden ikke er en [Måned]
 */
private fun Kravgrunnlag.Grunnlagsperiode.unsafeHentBruttoFeilutbetalingSomMånedBeløp(): MånedBeløp = MånedBeløp(
    periode = Måned.fra(periode.fraOgMed, periode.tilOgMed),
    beløp = Beløp(bruttoFeilutbetaling),
)

fun Kravgrunnlag.Grunnlagsperiode.unsafeTilMåned(): Måned = Måned.fra(this.periode.fraOgMed, this.periode.tilOgMed)
