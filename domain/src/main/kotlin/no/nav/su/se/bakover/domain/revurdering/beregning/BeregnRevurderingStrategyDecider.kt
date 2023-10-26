package no.nav.su.se.bakover.domain.revurdering.beregning

import arrow.core.getOrElse
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.beregning.BeregningStrategyFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.Opphørsvedtak
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderingsresultat
import java.time.Clock

internal class BeregnRevurderingStrategyDecider(
    private val revurdering: Revurdering,
    private val gjeldendeVedtaksdata: GjeldendeVedtaksdata,
    private val clock: Clock,
    private val beregningStrategyFactory: BeregningStrategyFactory,
) {
    init {
        require(revurdering.periode == gjeldendeVedtaksdata.garantertSammenhengendePeriode()) { "Periode for revurdering:${revurdering.periode} og gjeldende vedtaksdata:${gjeldendeVedtaksdata.garantertSammenhengendePeriode()} er forskjellig" }
    }
    fun decide(): BeregnRevurderingStrategy {
        /**
         * Lurer typesystemet til å snevre inn valgmulighetene for avkorting ved å forsøke en vanlig beregning først.
         */
        val avkorting = Normal(revurdering, beregningStrategyFactory).beregn()
            .getOrElse { throw IllegalStateException(it.toString()) }.first.avkorting

        val avkortingsgrunnlag = gjeldendeVedtaksdata.grunnlagsdata.fradragsgrunnlag.filter {
            it.fradragstype == Fradragstype.AvkortingUtenlandsopphold
        }

        return when (avkorting) {
            is AvkortingVedRevurdering.Uhåndtert.IngenUtestående -> {
                if (avkortingsgrunnlag.isEmpty()) {
                    Normal(revurdering, beregningStrategyFactory)
                } else {
                    when {
                        annullerMedInnvilgelse(revurdering, gjeldendeVedtaksdata) -> {
                            AnnullerAvkorting(revurdering, beregningStrategyFactory)
                        }
                        annullerMedOpphør(revurdering, gjeldendeVedtaksdata) -> {
                            AnnullerAvkorting(revurdering, beregningStrategyFactory)
                        }
                        else -> {
                            VidereførAvkorting(revurdering, avkortingsgrunnlag, clock, beregningStrategyFactory)
                        }
                    }
                }
            }
            is AvkortingVedRevurdering.Uhåndtert.KanIkkeHåndtere -> {
                throw IllegalStateException("Skal ikke kunne skje")
            }
            is AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting -> {
                if (avkortingsgrunnlag.isEmpty()) {
                    when {
                        annullerMedInnvilgelse(revurdering, gjeldendeVedtaksdata) -> {
                            AnnullerAvkorting(revurdering, beregningStrategyFactory)
                        }
                        annullerMedOpphør(revurdering, gjeldendeVedtaksdata) -> {
                            AnnullerAvkorting(revurdering, beregningStrategyFactory)
                        }
                        else -> {
                            Normal(revurdering, beregningStrategyFactory)
                        }
                    }
                } else {
                    when {
                        annullerMedInnvilgelse(revurdering, gjeldendeVedtaksdata) -> {
                            VidereførAvkorting(revurdering, avkortingsgrunnlag, clock, beregningStrategyFactory)
                        }
                        annullerMedOpphør(revurdering, gjeldendeVedtaksdata) -> {
                            VidereførAvkorting(revurdering, avkortingsgrunnlag, clock, beregningStrategyFactory)
                        }
                        else -> {
                            VidereførAvkorting(revurdering, avkortingsgrunnlag, clock, beregningStrategyFactory)
                        }
                    }
                }
            }
        }
    }

    private fun annullerMedInnvilgelse(
        revurdering: Revurdering,
        gjeldendeVedtaksdata: GjeldendeVedtaksdata,
    ): Boolean {
        val iverksattAvkortingsvarselIPeriode =
            produserteAvkortingsvarselIRevurderingsperiode(revurdering, gjeldendeVedtaksdata)
        val kanAnnullere = iverksattAvkortingsvarselIPeriode.isNotEmpty() &&
            revurdering.vilkårsvurderingsResultat() is Vilkårsvurderingsresultat.Innvilget
        if (kanAnnullere) {
            check(
                iverksattAvkortingsvarselIPeriode
                    .map { it.datoIntervall() }
                    .all { revurdering.periode.inneholder(it) },
            ) { "Må revurdere hele perioden for opprinngelig avkorting ved annullering." }
        }

        return kanAnnullere
    }

    private fun produserteAvkortingsvarselIRevurderingsperiode(
        revurdering: Revurdering,
        gjeldendeVedtaksdata: GjeldendeVedtaksdata,
    ): List<AvkortingVedRevurdering.Iverksatt.HarProdusertNyttAvkortingsvarsel> {
        @Suppress("ConvertCallChainIntoSequence")
        return revurdering.periode.måneder()
            .mapNotNull { gjeldendeVedtaksdata.gjeldendeVedtakForMåned(it) }
            .distinct()
            // Det er foreløpig kun tidligere utenlandsopphør som har ført til avkorting.
            .filterIsInstance<Opphørsvedtak>()
            .map { it.behandling.avkorting }
            .filterIsInstance<AvkortingVedRevurdering.Iverksatt.HarProdusertNyttAvkortingsvarsel>()
    }

    private fun annullerMedOpphør(
        revurdering: Revurdering,
        gjeldendeVedtaksdata: GjeldendeVedtaksdata,
    ): Boolean {
        val iverksattAvkortingsvarselIPeriode =
            produserteAvkortingsvarselIRevurderingsperiode(revurdering, gjeldendeVedtaksdata)
        val kanAnnullere = iverksattAvkortingsvarselIPeriode.isNotEmpty() &&
            revurdering.vilkårsvurderingsResultat() is Vilkårsvurderingsresultat.Avslag

        if (kanAnnullere) {
            check(
                iverksattAvkortingsvarselIPeriode
                    .map { it.datoIntervall() }
                    .all { revurdering.periode.inneholder(it) },
            ) { "Må revurdere hele perioden for opprinngelig avkorting ved annullering." }
            check(
                (revurdering.vilkårsvurderingsResultat() as Vilkårsvurderingsresultat.Avslag).tidligsteDatoForAvslag <= iverksattAvkortingsvarselIPeriode.minOf { it.datoIntervall().fraOgMed },
            ) { "Dato for opphør må være tidligere enn eller lik fra og med dato for opprinnelig avkorting som annulleres" }
        }

        return kanAnnullere
    }
}
