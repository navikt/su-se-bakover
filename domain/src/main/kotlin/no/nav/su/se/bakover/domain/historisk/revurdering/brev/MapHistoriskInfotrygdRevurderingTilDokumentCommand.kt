package no.nav.su.se.bakover.domain.historisk.revurdering.brev

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.tid.ddMMyyyy
import no.nav.su.se.bakover.domain.brev.Satsoversikt
import no.nav.su.se.bakover.domain.brev.Satsoversikt.Companion.slåSammenLikePerioder
import no.nav.su.se.bakover.domain.brev.beregning.Beregningsperiode
import no.nav.su.se.bakover.domain.brev.beregning.FradragForBrev
import no.nav.su.se.bakover.domain.brev.beregning.tilBrevperiode
import no.nav.su.se.bakover.domain.brev.beregning.toMånedsfradragPerType
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBosituasjon
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdAttestering
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdering
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdertMånedsresultat
import no.nav.su.se.bakover.domain.historisk.revurdering.beregnFradragEtterEpsRegler
import satser.domain.historisk.HistoriskInfotrygdSats
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import java.math.BigDecimal
import java.math.RoundingMode

fun HistoriskInfotrygdRevurdering.lagVedtaksbrevkommando(
    sakInfo: SakInfo,
): Either<KunneIkkeLageHistoriskInfotrygdVedtaksbrevkommando, HistoriskInfotrygdRevurderingDokumentCommand> {
    val beregning = beregning
        ?: return KunneIkkeLageHistoriskInfotrygdVedtaksbrevkommando.ManglerBeregning.left()
    val fritekst = vedtaksbrevFritekst?.takeIf { it.isNotBlank() }
        ?: return KunneIkkeLageHistoriskInfotrygdVedtaksbrevkommando.ManglerFritekst.left()

    val resultater = beregning.månedsresultater.values.toList()
    val beregningsperioder = resultater.map { it.tilBeregningsperiode() }
    val satsoversikt = resultater.tilSatsoversikt()
    val harEktefelle = resultater.any { it.bosituasjon.harEktefelle() }
    val attestant = attesteringer.filterIsInstance<HistoriskInfotrygdAttestering.Godkjent>().lastOrNull()?.attestant

    return if (beregning.erOpphør) {
        HistoriskInfotrygdRevurderingDokumentCommand.Opphør(
            fødselsnummer = sakInfo.fnr,
            saksnummer = sakInfo.saksnummer,
            saksbehandler = saksbehandler,
            attestant = attestant,
            beregningsperioder = beregningsperioder,
            fritekst = fritekst,
            harEktefelle = harEktefelle,
            satsoversikt = satsoversikt,
            opphørsgrunner = resultater
                .filterIsInstance<HistoriskInfotrygdRevurdertMånedsresultat.Opphør>()
                .map { it.opphørsgrunn }
                .distinct(),
            opphørsperiode = periode,
            halvtGrunnbeløp = requireNotNull(HistoriskInfotrygdSats.halvtGrunnbeløpPerÅrAvrundet(periode.fraOgMed)) {
                "Mangler grunnbeløp for ${periode.fraOgMed}"
            },
        )
    } else {
        HistoriskInfotrygdRevurderingDokumentCommand.Inntekt(
            fødselsnummer = sakInfo.fnr,
            saksnummer = sakInfo.saksnummer,
            saksbehandler = saksbehandler,
            attestant = attestant,
            beregningsperioder = beregningsperioder,
            fritekst = fritekst,
            harEktefelle = harEktefelle,
            satsoversikt = satsoversikt,
        )
    }.right()
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
    val beregnedeFradrag = fradrag.beregnFradragEtterEpsRegler(bosituasjon, sats)
    val epsFradrag = fradrag.filter { it.tilhører == FradragTilhører.EPS }
    val brukerEpsFradrag = beregnedeFradrag.epsFradrag.signum() != 0
    return Beregningsperiode(
        ytelsePerMåned = ytelse.avrundetTilInt(),
        satsbeløpPerMåned = sats.avrundetTilInt(),
        epsFribeløp = beregnedeFradrag.epsFribeløp.avrundetTilInt(),
        fradrag = FradragForBrev(
            bruker = fradrag
                .filter { it.tilhører == FradragTilhører.BRUKER }
                .toMånedsfradragPerType(),
            eps = FradragForBrev.Eps(
                fradrag = if (brukerEpsFradrag) epsFradrag.toMånedsfradragPerType() else emptyList(),
                harFradragMedSumSomErLavereEnnFribeløp =
                !brukerEpsFradrag && epsFradrag.isNotEmpty() && bosituasjon.harEktefelle(),
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
}
