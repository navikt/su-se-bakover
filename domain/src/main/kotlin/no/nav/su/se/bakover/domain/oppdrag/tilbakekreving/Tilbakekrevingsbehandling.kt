package no.nav.su.se.bakover.domain.oppdrag.tilbakekreving

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseKode
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import java.math.BigDecimal
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
    override fun ferdigbehandlet(): Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag {
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
    override fun ferdigbehandlet(): Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag {
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
            aksjonsKode = Tilbakekrevingsvedtak.AksjonsKode.FATT_VEDTAK,
            vedtakId = kravgrunnlag.vedtakId,
            hjemmel = Tilbakekrevingsvedtak.TilbakekrevingsHjemmel.ANNEN,
            renterBeregnes = false,
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
                                    beløpSkatt = grunnlagsperiode.beløpSkattMnd,
                                    tilbakekrevingsresultat = Tilbakekrevingsvedtak.Tilbakekrevingsresultat.FULL_TILBAKEKREV,
                                    tilbakekrevingsÅrsak = Tilbakekrevingsvedtak.TilbakekrevingsÅrsak.ANNET,
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
            aksjonsKode = Tilbakekrevingsvedtak.AksjonsKode.FATT_VEDTAK,
            vedtakId = kravgrunnlag.vedtakId,
            hjemmel = Tilbakekrevingsvedtak.TilbakekrevingsHjemmel.ANNEN,
            renterBeregnes = false,
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
                                    beløpSomSkalTilbakekreves = BigDecimal.ZERO,
                                    beløpSomIkkeTilbakekreves = it.beløpSkalTilbakekreves,
                                    beløpSkatt = BigDecimal.ZERO,
                                    tilbakekrevingsresultat = Tilbakekrevingsvedtak.Tilbakekrevingsresultat.INGEN_TILBAKEKREV,
                                    tilbakekrevingsÅrsak = Tilbakekrevingsvedtak.TilbakekrevingsÅrsak.ANNET,
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
        require(it.kode == KlasseKode.KL_KODE_FEIL_INNT)
        return Tilbakekrevingsvedtak.Tilbakekrevingsperiode.Tilbakekrevingsbeløp.TilbakekrevingsbeløpFeilutbetaling(
            kodeKlasse = it.kode,
            beløpTidligereUtbetaling = it.beløpTidligereUtbetaling,
            beløpNyUtbetaling = it.beløpNyUtbetaling,
            beløpSomSkalTilbakekreves = it.beløpSkalTilbakekreves,
            beløpSomIkkeTilbakekreves = it.beløpSkalIkkeTilbakekreves,
        )
    }

    override fun sendtTilbakekrevingsvedtak(
        tilbakekrevingsvedtak: RåttTilbakekrevingsvedtak,
        tilbakekrevingsvedtakSendt: Tidspunkt,
    ): Tilbakekrevingsbehandling.Ferdigbehandlet.MedKravgrunnlag.SendtTilbakekrevingsvedtak {
        return SendtTilbakekrevingsvedtak(
            avgjort = avgjort,
            kravgrunnlag = kravgrunnlag,
            kravgrunnlagMottatt = kravgrunnlagMottatt,
            tilbakekrevingsvedtak = tilbakekrevingsvedtak,
            tilbakekrevingsvedtakSendt = tilbakekrevingsvedtakSendt,
        )
    }
}

data class SendtTilbakekrevingsvedtak(
    override val avgjort: Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving.Avgjort,
    override val kravgrunnlag: RåttKravgrunnlag,
    override val kravgrunnlagMottatt: Tidspunkt,
    override val tilbakekrevingsvedtak: RåttTilbakekrevingsvedtak,
    override val tilbakekrevingsvedtakSendt: Tidspunkt,
) : Tilbakekrevingsbehandling.Ferdigbehandlet.MedKravgrunnlag.SendtTilbakekrevingsvedtak

object IkkeBehovForTilbakekrevingUnderBehandling :
    Tilbakekrevingsbehandling.UnderBehandling.IkkeBehovForTilbakekreving {
    override fun ferdigbehandlet(): Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.IkkeBehovForTilbakekreving {
        return IkkeBehovForTilbakekrevingFerdigbehandlet
    }
}

object IkkeBehovForTilbakekrevingFerdigbehandlet :
    Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.IkkeBehovForTilbakekreving

sealed interface Tilbakekrevingsbehandling {

    sealed interface UnderBehandling : Tilbakekrevingsbehandling {

        fun ferdigbehandlet(): Ferdigbehandlet

        sealed interface VurderTilbakekreving : UnderBehandling {
            val id: UUID
            val opprettet: Tidspunkt
            val sakId: UUID
            val revurderingId: UUID
            val periode: Periode

            sealed interface Avgjort : VurderTilbakekreving {
                override fun ferdigbehandlet(): Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag
            }

            sealed interface IkkeAvgjort : VurderTilbakekreving {
                override fun ferdigbehandlet(): Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag {
                    throw IllegalStateException("Må avgjøres før vurdering kan ferdigbehandles")
                }
            }
        }

        interface IkkeBehovForTilbakekreving : UnderBehandling {
            override fun ferdigbehandlet(): Ferdigbehandlet.UtenKravgrunnlag.IkkeBehovForTilbakekreving
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
                    tilbakekrevingsvedtak: RåttTilbakekrevingsvedtak,
                    tilbakekrevingsvedtakSendt: Tidspunkt,
                ): SendtTilbakekrevingsvedtak
            }

            sealed interface SendtTilbakekrevingsvedtak : MedKravgrunnlag {
                val tilbakekrevingsvedtak: RåttTilbakekrevingsvedtak
                val tilbakekrevingsvedtakSendt: Tidspunkt
            }
        }
    }
}
