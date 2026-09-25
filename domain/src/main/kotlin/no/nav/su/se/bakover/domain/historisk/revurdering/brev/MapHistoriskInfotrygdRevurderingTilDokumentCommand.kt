package no.nav.su.se.bakover.domain.historisk.revurdering.brev

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import behandling.revurdering.domain.Opphørsgrunn
import dokument.domain.GenererDokumentCommand
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.ddMMyyyy
import no.nav.su.se.bakover.domain.brev.Satsoversikt
import no.nav.su.se.bakover.domain.brev.Satsoversikt.Companion.slåSammenLikePerioder
import no.nav.su.se.bakover.domain.brev.beregning.Beregningsperiode
import no.nav.su.se.bakover.domain.brev.beregning.FradragForBrev
import no.nav.su.se.bakover.domain.brev.beregning.tilBrevperiode
import no.nav.su.se.bakover.domain.brev.beregning.toMånedsfradragPerType
import no.nav.su.se.bakover.domain.brev.command.IverksettRevurderingDokumentCommand
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBosituasjon
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdering
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdertMånedsresultat
import satser.domain.SatsFactory
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import java.math.BigDecimal
import java.math.RoundingMode

fun HistoriskInfotrygdRevurdering.lagVedtaksbrevkommando(
    sakInfo: SakInfo,
    satsFactory: SatsFactory,
): Either<KunneIkkeLageHistoriskInfotrygdVedtaksbrevkommando, GenererDokumentCommand> {
    val beregning = beregning
        ?: return KunneIkkeLageHistoriskInfotrygdVedtaksbrevkommando.ManglerBeregning.left()
    if (vedtaksbrevFritekst.isNullOrBlank()) {
        return KunneIkkeLageHistoriskInfotrygdVedtaksbrevkommando.ManglerFritekst.left()
    }

    val resultater = beregning.månedsresultater.values.toList()
    if (resultater.any { it.bosituasjon.harEktefelle() }) {
        return KunneIkkeLageHistoriskInfotrygdVedtaksbrevkommando
            .EktefelleberegningKanIkkeUtledesPåSammeMåteSomOrdinærRevurdering
            .left()
    }
    val beregningsperioder = resultater.map { it.tilBeregningsperiode() }
    val satsoversikt = resultater.tilSatsoversikt()

    return when {
        resultater.all { it is HistoriskInfotrygdRevurdertMånedsresultat.Ytelse } ->
            IverksettRevurderingDokumentCommand.Inntekt(
                fødselsnummer = sakInfo.fnr,
                saksnummer = sakInfo.saksnummer,
                sakstype = Sakstype.ALDER,
                saksbehandler = saksbehandler,
                attestant = null,
                beregningsperioder = beregningsperioder,
                fritekst = vedtaksbrevFritekst,
                harEktefelle = false,
                forventetInntektStørreEnn0 = false,
                satsoversikt = satsoversikt,
            ).right()

        resultater.all { it is HistoriskInfotrygdRevurdertMånedsresultat.Opphør } ->
            IverksettRevurderingDokumentCommand.Opphør(
                fødselsnummer = sakInfo.fnr,
                saksnummer = sakInfo.saksnummer,
                sakstype = Sakstype.ALDER,
                beregningsperioder = beregningsperioder,
                forventetInntektStørreEnn0 = false,
                harEktefelle = false,
                saksbehandler = saksbehandler,
                attestant = null,
                fritekst = vedtaksbrevFritekst,
                opphørsgrunner = resultater.map { it.utledOpphørsgrunn() }.distinct(),
                opphørsperiode = periode,
                satsoversikt = satsoversikt,
                halvtGrunnbeløp = satsFactory.grunnbeløp(periode.fraOgMed)
                    .halvtGrunnbeløpPerÅrAvrundet(),
            ).right()

        else ->
            KunneIkkeLageHistoriskInfotrygdVedtaksbrevkommando
                .BlandetYtelseOpphørOgGjeninnvilgelseStøttesIkkeAvBrevmalen
                .left()
    }
}

private fun HistoriskInfotrygdRevurdertMånedsresultat.tilBeregningsperiode(): Beregningsperiode {
    val fradrag = when (this) {
        is HistoriskInfotrygdRevurdertMånedsresultat.Ytelse -> fradrag
        is HistoriskInfotrygdRevurdertMånedsresultat.Opphør -> fradrag
    }
    val ytelse = when (this) {
        is HistoriskInfotrygdRevurdertMånedsresultat.Ytelse -> beløp
        is HistoriskInfotrygdRevurdertMånedsresultat.Opphør -> BigDecimal.ZERO
    }
    return Beregningsperiode(
        ytelsePerMåned = ytelse.avrundetTilInt(),
        satsbeløpPerMåned = sats.avrundetTilInt(),
        epsFribeløp = 0,
        fradrag = FradragForBrev(
            bruker = fradrag
                .filter { it.tilhører == FradragTilhører.BRUKER }
                .toMånedsfradragPerType(),
            eps = FradragForBrev.Eps(
                fradrag = emptyList(),
                harFradragMedSumSomErLavereEnnFribeløp = false,
            ),
        ),
        periode = måned.tilPeriode().tilBrevperiode(),
        sats = bosituasjon.brevtekst(),
    )
}

private fun List<HistoriskInfotrygdRevurdertMånedsresultat>.tilSatsoversikt(): Satsoversikt =
    Satsoversikt(
        map {
            Satsoversikt.Satsperiode(
                fraOgMed = it.måned.fraOgMed.ddMMyyyy(),
                tilOgMed = it.måned.tilOgMed.ddMMyyyy(),
                sats = it.bosituasjon.brevtekst(),
                satsBeløp = it.sats.avrundetTilInt(),
                satsGrunn = "Historisk Infotrygd-sats ${it.bosituasjon.satskode()}",
            )
        }.slåSammenLikePerioder(),
    )

private fun BigDecimal.avrundetTilInt(): Int = setScale(0, RoundingMode.HALF_UP).intValueExact()

private fun HistoriskInfotrygdRevurdertMånedsresultat.utledOpphørsgrunn(): Opphørsgrunn {
    require(this is HistoriskInfotrygdRevurdertMånedsresultat.Opphør)
    val sumFradrag = fradrag.sumOf { BigDecimal.valueOf(it.månedsbeløp) }
    return if (sumFradrag >= sats) {
        Opphørsgrunn.FOR_HØY_INNTEKT
    } else {
        Opphørsgrunn.SU_UNDER_MINSTEGRENSE
    }
}

private val HistoriskInfotrygdRevurdertMånedsresultat.sats: BigDecimal
    get() = when (this) {
        is HistoriskInfotrygdRevurdertMånedsresultat.Ytelse -> sats
        is HistoriskInfotrygdRevurdertMånedsresultat.Opphør -> sats
    }

private val HistoriskInfotrygdRevurdertMånedsresultat.bosituasjon: HistoriskBosituasjon
    get() = when (this) {
        is HistoriskInfotrygdRevurdertMånedsresultat.Ytelse -> bosituasjon
        is HistoriskInfotrygdRevurdertMånedsresultat.Opphør -> bosituasjon
    }

private fun HistoriskBosituasjon.harEktefelle(): Boolean =
    this == HistoriskBosituasjon.EPS_UNDER_67 || this == HistoriskBosituasjon.EPS_OVER_67

private fun HistoriskBosituasjon.satskode(): String = when (this) {
    HistoriskBosituasjon.ENSLIG -> "EN"
    HistoriskBosituasjon.EPS_UNDER_67 -> "EU"
    HistoriskBosituasjon.EPS_OVER_67 -> "EO"
    HistoriskBosituasjon.ENSLIG_MED_BOFELLESSKAP -> "EV"
}

private fun HistoriskBosituasjon.brevtekst(): String = when (this) {
    HistoriskBosituasjon.ENSLIG -> "enslig"
    HistoriskBosituasjon.EPS_UNDER_67 -> "ektefelle under 67 år"
    HistoriskBosituasjon.EPS_OVER_67 -> "ektefelle over 67 år"
    HistoriskBosituasjon.ENSLIG_MED_BOFELLESSKAP -> "enslig med bofellesskap"
}

sealed interface KunneIkkeLageHistoriskInfotrygdVedtaksbrevkommando {
    data object ManglerBeregning : KunneIkkeLageHistoriskInfotrygdVedtaksbrevkommando
    data object ManglerFritekst : KunneIkkeLageHistoriskInfotrygdVedtaksbrevkommando
    data object EktefelleberegningKanIkkeUtledesPåSammeMåteSomOrdinærRevurdering :
        KunneIkkeLageHistoriskInfotrygdVedtaksbrevkommando
    data object BlandetYtelseOpphørOgGjeninnvilgelseStøttesIkkeAvBrevmalen :
        KunneIkkeLageHistoriskInfotrygdVedtaksbrevkommando
}
