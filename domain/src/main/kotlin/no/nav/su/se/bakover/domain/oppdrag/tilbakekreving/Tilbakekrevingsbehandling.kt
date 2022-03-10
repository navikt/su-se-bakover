package no.nav.su.se.bakover.domain.oppdrag.tilbakekreving

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseKode
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/**
 * Dersom en revurdering fører til til en feilutbetaling, må vi ta stilling til om vi skal kreve tilbake eller ikke.
 *
 * @property periode Vi støtter i førsteomgang kun en sammenhengende periode, som kan være hele eller deler av en revurderingsperiode.
 * @property oversendtTidspunkt Tidspunktet vi sendte avgjørelsen til oppdrag, ellers null
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
                is IverksattRevurdering.IngenEndring -> {
                    throw IllegalStateException("Tilbakekreving er ikke relevant for ingen endring")
                }
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
            vedtakId = kravgrunnlag.vedtakId,
            ansvarligEnhet = "8020",
            kontrollFelt = kravgrunnlag.kontrollfelt,
            behandler = kravgrunnlag.behandler,
            tilbakekrevingsperioder = kravgrunnlag.grunnlagsperioder.map { grunnlagsperiode ->
                Tilbakekrevingsvedtak.Tilbakekrevingsperiode(
                    periode = grunnlagsperiode.periode,
                    renterBeregnes = false,
                    beløpRenter = BigDecimal.ZERO,
                    tilbakekrevingsbeløp = grunnlagsperiode.grunnlagsbeløp.map {
                        when (it.kode) {
                            KlasseKode.SUUFORE -> {
                                Tilbakekrevingsvedtak.Tilbakekrevingsperiode.Tilbakekrevingsbeløp.TilbakekrevingsbeløpYtelse(
                                    kodeKlasse = it.kode,
                                    beløpTidligereUtbetaling = it.beløpTidligereUtbetaling,
                                    beløpNyUtbetaling = it.beløpNyUtbetaling,
                                    beløpSomSkalTilbakekreves = it.beløpSkalTilbakekreves,
                                    beløpSomIkkeTilbakekreves = BigDecimal.ZERO,
                                    beløpSkatt = it.beløpSkalTilbakekreves
                                        .multiply(it.skatteProsent)
                                        .divide(BigDecimal("100"))
                                        .setScale(0, RoundingMode.DOWN)
                                        .min(grunnlagsperiode.beløpSkattMnd),
                                    tilbakekrevingsresultat = Tilbakekrevingsvedtak.Tilbakekrevingsresultat.FULL_TILBAKEKREVING,
                                    skyld = Tilbakekrevingsvedtak.Skyld.BRUKER,
                                )
                            }
                            KlasseKode.KL_KODE_FEIL_INNT -> {
                                mapDelkomponentForFeilutbetaling(it)
                            }
                            else -> {
                                throw IllegalStateException("Ukjent klassekode")
                            }
                        }
                    },
                )
            },
        )
    }

    private fun ingenTilbakekreving(kravgrunnlag: Kravgrunnlag): Tilbakekrevingsvedtak.IngenTilbakekreving {
        return Tilbakekrevingsvedtak.IngenTilbakekreving(
            vedtakId = kravgrunnlag.vedtakId,
            ansvarligEnhet = "8020",
            kontrollFelt = kravgrunnlag.kontrollfelt,
            behandler = kravgrunnlag.behandler, // TODO behandler bør sannsynligvis være fra tilbakekrevingsbehandling/revurdering og ikke kravgrunnlaget
            tilbakekrevingsperioder = kravgrunnlag.grunnlagsperioder.map { grunnlagsperiode ->
                Tilbakekrevingsvedtak.Tilbakekrevingsperiode(
                    periode = grunnlagsperiode.periode,
                    renterBeregnes = false,
                    beløpRenter = BigDecimal.ZERO,
                    tilbakekrevingsbeløp = grunnlagsperiode.grunnlagsbeløp.map {
                        when (it.kode) {
                            KlasseKode.SUUFORE -> {
                                Tilbakekrevingsvedtak.Tilbakekrevingsperiode.Tilbakekrevingsbeløp.TilbakekrevingsbeløpYtelse(
                                    kodeKlasse = it.kode,
                                    beløpTidligereUtbetaling = it.beløpTidligereUtbetaling,
                                    beløpNyUtbetaling = it.beløpNyUtbetaling,
                                    beløpSomSkalTilbakekreves = BigDecimal.ZERO,
                                    beløpSomIkkeTilbakekreves = it.beløpSkalTilbakekreves,
                                    beløpSkatt = BigDecimal.ZERO,
                                    tilbakekrevingsresultat = Tilbakekrevingsvedtak.Tilbakekrevingsresultat.INGEN_TILBAKEKREVING,
                                    skyld = Tilbakekrevingsvedtak.Skyld.IKKE_FORDELT,
                                )
                            }
                            KlasseKode.KL_KODE_FEIL_INNT -> {
                                mapDelkomponentForFeilutbetaling(it)
                            }
                            else -> {
                                throw IllegalStateException("Ukjent klassekode")
                            }
                        }
                    },
                )
            },
        )
    }

    private fun mapDelkomponentForFeilutbetaling(it: Kravgrunnlag.Grunnlagsperiode.Grunnlagsbeløp): Tilbakekrevingsvedtak.Tilbakekrevingsperiode.Tilbakekrevingsbeløp {
        return Tilbakekrevingsvedtak.Tilbakekrevingsperiode.Tilbakekrevingsbeløp.TilbakekrevingsbeløpFeilutbetaling(
            kodeKlasse = it.kode,
            beløpTidligereUtbetaling = it.beløpTidligereUtbetaling,
            beløpNyUtbetaling = it.beløpNyUtbetaling,
            beløpSomSkalTilbakekreves = it.beløpSkalTilbakekreves,
            beløpSomIkkeTilbakekreves = it.beløpSkalIkkeTilbakekreves,
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

object IkkeBehovForTilbakekrevingUnderBehandling :
    Tilbakekrevingsbehandling.UnderBehandling.IkkeBehovForTilbakekreving {
    override fun fullførBehandling(): Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.IkkeBehovForTilbakekreving {
        return IkkeBehovForTilbakekrevingFerdigbehandlet
    }
}

object IkkeBehovForTilbakekrevingFerdigbehandlet :
    Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.IkkeBehovForTilbakekreving

sealed interface Tilbakekrevingsbehandling {

    sealed interface UnderBehandling : Tilbakekrevingsbehandling {

        fun fullførBehandling(): Ferdigbehandlet

        sealed interface VurderTilbakekreving : UnderBehandling {
            val id: UUID
            val opprettet: Tidspunkt
            val sakId: UUID
            val revurderingId: UUID
            val periode: Periode

            sealed interface Avgjort : VurderTilbakekreving {
                override fun fullførBehandling(): Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag
            }

            sealed interface IkkeAvgjort : VurderTilbakekreving {
                override fun fullførBehandling(): Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag {
                    throw IllegalStateException("Må avgjøres før vurdering kan ferdigbehandles")
                }
            }
        }

        interface IkkeBehovForTilbakekreving : UnderBehandling {
            override fun fullførBehandling(): Ferdigbehandlet.UtenKravgrunnlag.IkkeBehovForTilbakekreving
        }
    }

    sealed interface Ferdigbehandlet : Tilbakekrevingsbehandling {

        sealed interface UtenKravgrunnlag : Ferdigbehandlet {

            interface IkkeBehovForTilbakekreving : UtenKravgrunnlag

            sealed interface AvventerKravgrunnlag : UtenKravgrunnlag {
                val avgjort: UnderBehandling.VurderTilbakekreving.Avgjort

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

fun Tilbakekrevingsbehandling.tilbakekrevingErVurdert(): Either<Unit, Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving.Avgjort> {
    return when (this) {
        is MottattKravgrunnlag -> {
            this.avgjort.right()
        }
        is SendtTilbakekrevingsvedtak -> {
            this.avgjort.right()
        }
        is AvventerKravgrunnlag -> {
            this.avgjort.right()
        }
        is Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.IkkeBehovForTilbakekreving -> {
            Unit.left()
        }
        is Tilbakekrevingsbehandling.UnderBehandling.IkkeBehovForTilbakekreving -> {
            Unit.left()
        }
        is IkkeTilbakekrev -> {
            this.right()
        }
        is Tilbakekrev -> {
            this.right()
        }
        is IkkeAvgjort -> {
            Unit.left()
        }
    }
}

@JvmInline
value class Skattesats private constructor(private val sats: BigDecimal) {
    init {
        require(sats in BigDecimal.ZERO..BigDecimal.ONE)
    }

    fun prosent(): BigDecimal {
        return sats
    }

    companion object {
        operator fun invoke(bigDecimal: BigDecimal): Skattesats {
            return when (bigDecimal) {
                in BigDecimal.ZERO..BigDecimal.ONE -> {
                    Skattesats(bigDecimal)
                }
                in BigDecimal.ZERO..BigDecimal(100) -> {
                    Skattesats(
                        bigDecimal.divide(BigDecimal(100)).setScale(bigDecimal.scale() + 2, RoundingMode.DOWN),
                    )
                }
                else -> {
                    throw IllegalArgumentException("Unkjent intervall for skattesats, verdien: $bigDecimal er verken i intervallet 0..1 eller 0..100")
                }
            }
        }
    }
}
