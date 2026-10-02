package no.nav.su.se.bakover.domain.historisk.revurdering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import behandling.revurdering.domain.Opphørsgrunn
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifiseringer
import no.nav.su.se.bakover.common.domain.regelspesifisering.RegelspesifisertGrunnlag
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBosituasjon
import satser.domain.historisk.HistoriskInfotrygdSats
import satser.domain.historisk.HistoriskInfotrygdSatskategori
import vilkår.inntekt.domain.grunnlag.FradragForMåned
import java.math.BigDecimal

data class HistoriskInfotrygdBeregningsgrunnlagForMåned(
    val måned: Måned,
    val satskategori: HistoriskInfotrygdSatskategori,
    val fradrag: List<FradragForMåned>,
    val manueltOpphør: HistoriskInfotrygdManueltOpphør? = null,
) {
    init {
        require(fradrag.all { it.måned == måned }) { "Alle fradrag må tilhøre måneden som beregnes" }
        require(
            manueltOpphør?.opphørsgrunn !in
                setOf(
                    Opphørsgrunn.UFØRHET,
                    Opphørsgrunn.FOR_HØY_INNTEKT,
                    Opphørsgrunn.SU_UNDER_MINSTEGRENSE,
                ),
        ) {
            "Opphørsgrunnen ${manueltOpphør?.opphørsgrunn} kan ikke velges manuelt for en alderssak"
        }
    }
}

data class HistoriskInfotrygdManueltOpphør(
    val opphørsgrunn: Opphørsgrunn,
)

fun GjeldendeHistoriskInfotrygdVedtaksdata.beregnRevurdering(
    grunnlag: List<HistoriskInfotrygdBeregningsgrunnlagForMåned>,
): Either<KunneIkkeBeregneHistoriskInfotrygdRevurdering, HistoriskInfotrygdBeregning> {
    if (grunnlag.map { it.måned } != periode.måneder()) {
        return KunneIkkeBeregneHistoriskInfotrygdRevurdering.GrunnlagDekkerIkkeHelePerioden.left()
    }

    val benyttedeMånedsregler = mutableListOf<no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifisering>()
    val resultater = linkedMapOf<Måned, HistoriskInfotrygdRevurdertMånedsresultat>()
    grunnlag.forEach { månedsgrunnlag ->
        val måned = månedsgrunnlag.måned
        val gjeldende = forMåned(måned)
            ?: return KunneIkkeBeregneHistoriskInfotrygdRevurdering.ManglerGjeldendeData(måned).left()
        val månedssats = HistoriskInfotrygdSats.beregnMånedssats(
            dato = måned.fraOgMed,
            kategori = månedsgrunnlag.satskategori,
        ) ?: return KunneIkkeBeregneHistoriskInfotrygdRevurdering.ManglerSats(
            måned,
            månedsgrunnlag.satskategori,
        ).left()
        val ensligMånedssats = HistoriskInfotrygdSats.beregnMånedssats(
            dato = måned.fraOgMed,
            kategori = HistoriskInfotrygdSatskategori.EN,
        )!!
        val minstegrense = ensligMånedssats.månedssats.multiply(BigDecimal("0.02"))
        val minstegrenseregel = Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_MINSTEGRENSE
            .benyttRegelspesifisering(
                verdi = minstegrense.toPlainString(),
                avhengigeRegler = listOf(ensligMånedssats.benyttetRegel),
            )
        val fradragsgrunnlag = RegelspesifisertGrunnlag.GRUNNLAG_FRADRAG.benyttGrunnlag(
            månedsgrunnlag.fradrag.toString(),
        )
        val bosituasjon = månedsgrunnlag.satskategori.tilBosituasjon()
        val samletFradrag = månedsgrunnlag.fradrag.samletFradragEtterEpsRegler(
            bosituasjon = bosituasjon,
            sats = månedssats.månedssats,
        )
        val fradragsregel = if (månedsgrunnlag.satskategori == HistoriskInfotrygdSatskategori.EO) {
            Regelspesifiseringer.REGEL_FRADRAG_EPS_OVER_FRIBELØP.benyttRegelspesifisering(
                verdi = samletFradrag.toPlainString(),
                avhengigeRegler = listOf(fradragsgrunnlag, månedssats.benyttetRegel),
            )
        } else {
            fradragsgrunnlag
        }
        val beregnetBeløp = månedssats.månedssats - samletFradrag
        val månedsregel = Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_MÅNEDSBEREGNING
            .benyttRegelspesifisering(
                verdi = beregnetBeløp.toPlainString(),
                avhengigeRegler = listOf(
                    månedssats.benyttetRegel,
                    fradragsregel,
                    minstegrenseregel,
                ),
            )
        val referanser = gjeldende.referanser()
            ?: return KunneIkkeBeregneHistoriskInfotrygdRevurdering.ManglerVedtak(måned).left()
        val resultat = if (
            månedsgrunnlag.manueltOpphør != null ||
            beregnetBeløp <= BigDecimal.ZERO ||
            beregnetBeløp < minstegrense
        ) {
            HistoriskInfotrygdRevurdertMånedsresultat.Opphør(
                måned = måned,
                opprinneligStønadId = referanser.first,
                opprinneligVedtakId = referanser.second,
                oppdragId = referanser.third,
                bosituasjon = bosituasjon,
                sats = månedssats.månedssats,
                fradrag = månedsgrunnlag.fradrag,
                opphørsgrunn = månedsgrunnlag.manueltOpphør?.opphørsgrunn ?: if (beregnetBeløp <= BigDecimal.ZERO) {
                    Opphørsgrunn.FOR_HØY_INNTEKT
                } else {
                    Opphørsgrunn.SU_UNDER_MINSTEGRENSE
                },
                manueltOpphør = månedsgrunnlag.manueltOpphør != null,
            )
        } else {
            HistoriskInfotrygdRevurdertMånedsresultat.Ytelse(
                måned = måned,
                opprinneligStønadId = referanser.first,
                opprinneligVedtakId = referanser.second,
                oppdragId = referanser.third,
                bosituasjon = bosituasjon,
                sats = månedssats.månedssats,
                fradrag = månedsgrunnlag.fradrag,
            )
        }
        resultater[måned] = resultat
        benyttedeMånedsregler.add(
            when (resultat) {
                is HistoriskInfotrygdRevurdertMånedsresultat.Ytelse ->
                    Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_YTELSE.benyttRegelspesifisering(
                        verdi = resultat.beløp.toPlainString(),
                        avhengigeRegler = listOf(månedsregel),
                    )
                is HistoriskInfotrygdRevurdertMånedsresultat.Opphør -> {
                    val regel = when {
                        resultat.manueltOpphør -> Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_MANUELT_OPPHØR
                        resultat.opphørsgrunn == Opphørsgrunn.FOR_HØY_INNTEKT ->
                            Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_OPPHØR_FOR_HØY_INNTEKT
                        else -> Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_OPPHØR_UNDER_MINSTEGRENSE
                    }
                    regel.benyttRegelspesifisering(
                        verdi = BigDecimal.ZERO.toPlainString(),
                        avhengigeRegler = listOfNotNull(
                            månedsregel,
                            månedsgrunnlag.manueltOpphør?.let {
                                RegelspesifisertGrunnlag.GRUNNLAG_HISTORISK_INFOTRYGD_MANUELL_OPPHØRSGRUNN
                                    .benyttGrunnlag(it.opphørsgrunn.name)
                            },
                        ),
                    )
                }
            },
        )
    }

    // Ytelse og opphør kan ikke kombineres i samme revurdering; perioder med ulikt utfall behandles hver for seg.
    if (
        resultater.values.any { it is HistoriskInfotrygdRevurdertMånedsresultat.Ytelse } &&
        resultater.values.any { it is HistoriskInfotrygdRevurdertMånedsresultat.Opphør }
    ) {
        return KunneIkkeBeregneHistoriskInfotrygdRevurdering.BlandetYtelseOgOpphør.left()
    }

    return HistoriskInfotrygdBeregning(
        månedsresultater = resultater,
        benyttetRegel = Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_BEREGNING
            .benyttRegelspesifisering(
                verdi = "Beregnet ${resultater.size} måneder",
                avhengigeRegler = benyttedeMånedsregler,
            ),
    ).right()
}

private fun GjeldendeHistoriskInfotrygdMånedsdata.referanser() = when (this) {
    is GjeldendeHistoriskInfotrygdMånedsdata.Ytelse -> Triple(opprinneligStønadId, opprinneligVedtakId, oppdragId)
    is GjeldendeHistoriskInfotrygdMånedsdata.IngenYtelse ->
        if (opprinneligStønadId != null && opprinneligVedtakId != null) {
            Triple(opprinneligStønadId, opprinneligVedtakId, oppdragId)
        } else {
            null
        }
}

private fun HistoriskInfotrygdSatskategori.tilBosituasjon(): HistoriskBosituasjon = when (this) {
    HistoriskInfotrygdSatskategori.EN -> HistoriskBosituasjon.ENSLIG
    HistoriskInfotrygdSatskategori.EU -> HistoriskBosituasjon.EPS_UNDER_67
    HistoriskInfotrygdSatskategori.EO -> HistoriskBosituasjon.EPS_OVER_67
    HistoriskInfotrygdSatskategori.EV -> HistoriskBosituasjon.ENSLIG_MED_BOFELLESSKAP
}

sealed interface KunneIkkeBeregneHistoriskInfotrygdRevurdering {
    data object GrunnlagDekkerIkkeHelePerioden : KunneIkkeBeregneHistoriskInfotrygdRevurdering
    data class ManglerGjeldendeData(val måned: Måned) : KunneIkkeBeregneHistoriskInfotrygdRevurdering
    data class ManglerVedtak(val måned: Måned) : KunneIkkeBeregneHistoriskInfotrygdRevurdering
    data object BlandetYtelseOgOpphør : KunneIkkeBeregneHistoriskInfotrygdRevurdering
    data class ManglerSats(
        val måned: Måned,
        val satskategori: HistoriskInfotrygdSatskategori,
    ) : KunneIkkeBeregneHistoriskInfotrygdRevurdering
}
