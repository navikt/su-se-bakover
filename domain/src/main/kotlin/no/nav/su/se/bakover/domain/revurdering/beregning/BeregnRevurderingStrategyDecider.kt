package no.nav.su.se.bakover.domain.revurdering.beregning

import arrow.core.getOrHandle
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.revurdering.AnnullerAvkorting
import no.nav.su.se.bakover.domain.revurdering.BeregnRevurderingStrategy
import no.nav.su.se.bakover.domain.revurdering.Normal
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.VidereførAvkorting
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderingsresultat
import java.time.Clock

internal class BeregnRevurderingStrategyDecider(
    private val revurdering: Revurdering,
    private val gjeldendeVedtaksdata: GjeldendeVedtaksdata,
    private val clock: Clock,
) {
    fun decide(): BeregnRevurderingStrategy {
        /**
         * Lurer typesystemet til å snevre inn valgmulighetene for avkorting ved å forsøke en vanlig beregning først.
         */
        val avkorting = Normal(revurdering, clock).beregn()
            .getOrHandle { throw IllegalStateException(it.toString()) }.first.avkorting

        val avkortingsgrunnlag = gjeldendeVedtaksdata.grunnlagsdata.fradragsgrunnlag.filter {
            it.fradragstype == Fradragstype.AvkortingUtenlandsopphold
        }

        return when (avkorting) {
            is AvkortingVedRevurdering.Uhåndtert.IngenUtestående -> {
                if (avkortingsgrunnlag.isEmpty()) {
                    Normal(revurdering, clock)
                } else {
                    when {
                        annullerMedInnvilgelse(revurdering, gjeldendeVedtaksdata) -> {
                            AnnullerAvkorting(revurdering, clock)
                        }
                        annullerMedOpphør(revurdering, gjeldendeVedtaksdata) -> {
                            AnnullerAvkorting(revurdering, clock)
                        }
                        else -> {
                            VidereførAvkorting(revurdering, avkortingsgrunnlag, clock)
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
                            AnnullerAvkorting(revurdering, clock)
                        }
                        annullerMedOpphør(revurdering, gjeldendeVedtaksdata) -> {
                            AnnullerAvkorting(revurdering, clock)
                        }
                        else -> {
                            Normal(revurdering, clock)
                        }
                    }
                } else {
                    when {
                        annullerMedInnvilgelse(revurdering, gjeldendeVedtaksdata) -> {
                            VidereførAvkorting(revurdering, avkortingsgrunnlag, clock)
                        }
                        annullerMedOpphør(revurdering, gjeldendeVedtaksdata) -> {
                            VidereførAvkorting(revurdering, avkortingsgrunnlag, clock)
                        }
                        else -> {
                            VidereførAvkorting(revurdering, avkortingsgrunnlag, clock)
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
                    .map { it.periode() }
                    .all { revurdering.periode.inneholder(it) },
            ) { "Må revurdere hele perioden for opprinngelig avkorting ved annullering." }
        }

        return kanAnnullere
    }

    private fun produserteAvkortingsvarselIRevurderingsperiode(
        revurdering: Revurdering,
        gjeldendeVedtaksdata: GjeldendeVedtaksdata,
    ): List<AvkortingVedRevurdering.Iverksatt.HarProdusertNyttAvkortingsvarsel> {
        return revurdering.periode.tilMånedsperioder()
            .map { gjeldendeVedtaksdata.gjeldendeVedtakPåDato(it.fraOgMed) }
            .distinct()
            .filterIsInstance<VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering>()
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
                    .map { it.periode() }
                    .all { revurdering.periode.inneholder(it) },
            ) { "Må revurdere hele perioden for opprinngelig avkorting ved annullering." }
            check(
                (revurdering.vilkårsvurderingsResultat() as Vilkårsvurderingsresultat.Avslag).dato <= iverksattAvkortingsvarselIPeriode.minOf { it.periode().fraOgMed },
            ) { "Dato for opphør må være tidligere enn eller lik fra og med dato for opprinnelig avkorting som annulleres" }
        }

        return kanAnnullere
    }
}
