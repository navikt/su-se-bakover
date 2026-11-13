package no.nav.su.se.bakover.domain.historisk.revurdering.utbetaling

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.historisk.revurdering.GjeldendeHistoriskInfotrygdMånedsdata
import no.nav.su.se.bakover.domain.historisk.revurdering.GjeldendeHistoriskInfotrygdVedtaksdata
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdering
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingStatus
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdertMånedsresultat
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskVedtakSomRevurderes
import no.nav.su.se.bakover.domain.historisk.revurdering.KunneIkkeVerifisereHistoriskeVedtakSomRevurderes

class HistoriskInfotrygdOppdragsgrunnlag private constructor(
    val periode: Periode,
    val månedsresultater: Map<Måned, HistoriskInfotrygdRevurdertMånedsresultat>,
) {
    companion object {
        fun bygg(
            revurdering: HistoriskInfotrygdRevurdering,
            gjeldende: GjeldendeHistoriskInfotrygdVedtaksdata,
        ): Either<KunneIkkeByggeHistoriskInfotrygdOppdragsgrunnlag, HistoriskInfotrygdOppdragsgrunnlag> {
            if (revurdering.projeksjonId != gjeldende.projeksjonId) {
                return KunneIkkeByggeHistoriskInfotrygdOppdragsgrunnlag.FeilProjeksjon.left()
            }
            val beregning = revurdering.beregning
                ?: return KunneIkkeByggeHistoriskInfotrygdOppdragsgrunnlag.ManglerBeregning.left()
            when (revurdering.status) {
                HistoriskInfotrygdRevurderingStatus.OPPRETTET,
                HistoriskInfotrygdRevurderingStatus.AVSLUTTET,
                -> return KunneIkkeByggeHistoriskInfotrygdOppdragsgrunnlag.UgyldigStatus(revurdering.status).left()
                HistoriskInfotrygdRevurderingStatus.BEREGNET,
                HistoriskInfotrygdRevurderingStatus.TIL_ATTESTERING,
                HistoriskInfotrygdRevurderingStatus.ATTESTERT,
                HistoriskInfotrygdRevurderingStatus.UNDERKJENT,
                -> Unit
            }
            if (beregning.månedsresultater.keys.toList() != revurdering.periode.måneder()) {
                return KunneIkkeByggeHistoriskInfotrygdOppdragsgrunnlag.BeregningDekkerIkkePerioden.left()
            }
            revurdering.verifiserAtVedtakeneSomRevurderesIkkeHarForandretSeg(gjeldende)
                .getOrElse {
                    return KunneIkkeByggeHistoriskInfotrygdOppdragsgrunnlag.EndretVedtaksgrunnlag(it).left()
                }
            val harSuKilde = revurdering.vedtakSomRevurderesMånedsvis.values.any {
                it is HistoriskVedtakSomRevurderes.HistoriskRevurderingsvedtak
            }
            if (harSuKilde) {
                return KunneIkkeByggeHistoriskInfotrygdOppdragsgrunnlag.SuKildeIkkeStøttetIUtkast.left()
            }
            beregning.månedsresultater.forEach { (måned, nyttResultat) ->
                when (val gammeltResultat = gjeldende.tidslinje.getValue(måned)) {
                    is GjeldendeHistoriskInfotrygdMånedsdata.IngenYtelse ->
                        return KunneIkkeByggeHistoriskInfotrygdOppdragsgrunnlag.IngenGjeldendeYtelse(måned).left()
                    is GjeldendeHistoriskInfotrygdMånedsdata.Ytelse -> when (nyttResultat) {
                        is HistoriskInfotrygdRevurdertMånedsresultat.Opphør -> Unit
                        is HistoriskInfotrygdRevurdertMånedsresultat.Ytelse -> {
                            if (nyttResultat.beløp.compareTo(gammeltResultat.beløp) == 0) {
                                return KunneIkkeByggeHistoriskInfotrygdOppdragsgrunnlag.UendretYtelse(måned).left()
                            }
                            if (nyttResultat.beløp.signum() <= 0) {
                                return KunneIkkeByggeHistoriskInfotrygdOppdragsgrunnlag.IkkePositivNyYtelse(måned).left()
                            }
                        }
                    }
                }
            }
            return HistoriskInfotrygdOppdragsgrunnlag(
                periode = revurdering.periode,
                månedsresultater = beregning.månedsresultater.toMap(),
            ).right()
        }
    }
}

sealed interface KunneIkkeByggeHistoriskInfotrygdOppdragsgrunnlag {
    data object FeilProjeksjon : KunneIkkeByggeHistoriskInfotrygdOppdragsgrunnlag
    data object ManglerBeregning : KunneIkkeByggeHistoriskInfotrygdOppdragsgrunnlag
    data class UgyldigStatus(
        val status: HistoriskInfotrygdRevurderingStatus,
    ) : KunneIkkeByggeHistoriskInfotrygdOppdragsgrunnlag
    data object BeregningDekkerIkkePerioden : KunneIkkeByggeHistoriskInfotrygdOppdragsgrunnlag
    data class EndretVedtaksgrunnlag(
        val feil: KunneIkkeVerifisereHistoriskeVedtakSomRevurderes,
    ) : KunneIkkeByggeHistoriskInfotrygdOppdragsgrunnlag
    data object SuKildeIkkeStøttetIUtkast : KunneIkkeByggeHistoriskInfotrygdOppdragsgrunnlag
    data class IngenGjeldendeYtelse(val måned: Måned) : KunneIkkeByggeHistoriskInfotrygdOppdragsgrunnlag
    data class UendretYtelse(val måned: Måned) : KunneIkkeByggeHistoriskInfotrygdOppdragsgrunnlag
    data class IkkePositivNyYtelse(val måned: Måned) : KunneIkkeByggeHistoriskInfotrygdOppdragsgrunnlag
}
