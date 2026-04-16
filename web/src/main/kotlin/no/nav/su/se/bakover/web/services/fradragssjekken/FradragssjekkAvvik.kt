package no.nav.su.se.bakover.web.services.fradragssjekken

import beregning.domain.BeregningStrategyFactory
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import satser.domain.SatsFactory
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import kotlin.math.abs

private const val UTBETALINGS_TOLERANSE_PROSENT = 10L

internal fun finnAvvikForSak(
    sjekkgrunnlag: SjekkgrunnlagForSak,
    måned: Måned,
    oppslagsresultater: EksterneOppslagsresultater,
    satsFactory: SatsFactory,
    clock: Clock,
): Avviksvurdering {
    val avvik = sjekkgrunnlag.sjekkplan.sjekkpunkter
        .mapNotNull { sjekkpunkt ->
            vurderAvvik(
                sjekkpunkt = sjekkpunkt,
                oppslag = oppslagsresultater.finnYtelseForPerson(sjekkpunkt),
                sjekkgrunnlag = sjekkgrunnlag,
                måned = måned,
                satsFactory = satsFactory,
                clock = clock,
            )
        }

    return if (avvik.isEmpty()) {
        Avviksvurdering.IngenDiff
    } else {
        Avviksvurdering.Diff(avvik)
    }
}

private fun vurderAvvik(
    sjekkpunkt: Sjekkpunkt,
    oppslag: EksterntOppslag,
    sjekkgrunnlag: SjekkgrunnlagForSak,
    måned: Måned,
    satsFactory: SatsFactory,
    clock: Clock,
): Fradragsfunn? {
    return when (oppslag) {
        is EksterntOppslag.Funnet -> vurderFunnetOppslag(
            sjekkpunkt = sjekkpunkt,
            eksterntBeløp = oppslag.beløp,
            sjekkgrunnlag = sjekkgrunnlag,
            måned = måned,
            satsFactory = satsFactory,
            clock = clock,
        )
        EksterntOppslag.IngenTreff -> vurderIngenTreff(sjekkpunkt)
        is EksterntOppslag.Feil -> null
    }
}

private fun vurderFunnetOppslag(
    sjekkpunkt: Sjekkpunkt,
    eksterntBeløp: Double,
    sjekkgrunnlag: SjekkgrunnlagForSak,
    måned: Måned,
    satsFactory: SatsFactory,
    clock: Clock,
): Fradragsfunn? {
    val lokaltBeløp = sjekkpunkt.lokaltBeløp ?: return Fradragsfunn.Oppgaveavvik(
        kode = OppgaveConfig.Fradragssjekk.AvvikKode.MANGLER_FRADRAG_I_SUAPP,
        oppgavetekst = "${sjekkpunkt.brukerType()} har ${sjekkpunkt.fradragstype} eksternt med beløp ${
            formatBeløp(
                eksterntBeløp,
            )
        }, men mangler fradrag på saken.",
    )

    if (harSammeBeløp(lokaltBeløp, eksterntBeløp)) {
        return null
    }

    return when (
        val utbetalingsendring = vurderMånedsutbetalingsendring(
            sjekkpunkt = sjekkpunkt,
            eksterntBeløp = eksterntBeløp,
            sjekkgrunnlag = sjekkgrunnlag,
            måned = måned,
            satsFactory = satsFactory,
            clock = clock,
        )
    ) {
        is MånedsutbetalingsendringVurdering.InnenforToleransegrense -> Fradragsfunn.Observasjon(
            kode = Observasjonskode.INSIGNIFIKANT_BELOEPSDIFFERANSE,
            loggtekst = "${sjekkpunkt.brukerType()} har ${sjekkpunkt.fradragstype} eksternt med beløp ${formatBeløp(eksterntBeløp)}, som ville endret månedsutbetalingen fra ${formatBeløp(sjekkgrunnlag.gjeldendeMånedsutbetaling.toDouble())} til ${formatBeløp(utbetalingsendring.nyMånedsutbetaling.toDouble())}. Endringen er innenfor toleransegrensen på 10%.",
        )

        is MånedsutbetalingsendringVurdering.UtenforToleransegrense -> Fradragsfunn.Oppgaveavvik(
            kode = OppgaveConfig.Fradragssjekk.AvvikKode.FRADRAG_DIFF_OVER_10KR,
            oppgavetekst = "${sjekkpunkt.brukerType()} har ${sjekkpunkt.fradragstype} eksternt med beløp ${formatBeløp(eksterntBeløp)}, som ville endret månedsutbetalingen fra ${formatBeløp(sjekkgrunnlag.gjeldendeMånedsutbetaling.toDouble())} til ${formatBeløp(utbetalingsendring.nyMånedsutbetaling.toDouble())}. Endringen er over toleransegrensen på 10% av tidligere månedsutbetaling.",
        )

        is MånedsutbetalingsendringVurdering.UgyldigEndring -> Fradragsfunn.Oppgaveavvik(
            kode = OppgaveConfig.Fradragssjekk.AvvikKode.ULIKT_BELOP,
            oppgavetekst = "${sjekkpunkt.brukerType()} har ${sjekkpunkt.fradragstype} med ulikt beløp, men vi klarte ikke beregne endring i månedsutbetaling. Lokalt=${formatBeløp(lokaltBeløp)}, eksternt=${formatBeløp(eksterntBeløp)} fra ${sjekkpunkt.ytelse.ytelseNavn}. Feil=${utbetalingsendring.grunn}.",
        )
    }
}

private fun vurderIngenTreff(
    sjekkpunkt: Sjekkpunkt,
): Fradragsfunn? {
    return sjekkpunkt.lokaltBeløp?.let {
        Fradragsfunn.Oppgaveavvik(
            kode = OppgaveConfig.Fradragssjekk.AvvikKode.LOKALT_FRADRAG_MANGLER_EKSTERNT,
            oppgavetekst = "${sjekkpunkt.brukerType()} har ${sjekkpunkt.fradragstype} lokalt med beløp ${formatBeløp(it)}, men det finnes ikke i ${sjekkpunkt.ytelse.ytelseNavn}.",
        )
    }
}

private fun Sjekkpunkt.brukerType(): String = when (tilhører) {
    FradragTilhører.BRUKER -> "Bruker"
    FradragTilhører.EPS -> "EPS"
}

private fun vurderMånedsutbetalingsendring(
    sjekkpunkt: Sjekkpunkt,
    eksterntBeløp: Double,
    sjekkgrunnlag: SjekkgrunnlagForSak,
    måned: Måned,
    satsFactory: SatsFactory,
    clock: Clock,
): MånedsutbetalingsendringVurdering {
    return runCatching {
        beregnNyMånedsutbetaling(
            sjekkpunkt = sjekkpunkt,
            eksterntBeløp = eksterntBeløp,
            sjekkgrunnlag = sjekkgrunnlag,
            måned = måned,
            satsFactory = satsFactory,
            clock = clock,
        )
    }.fold(
        onSuccess = { nyMånedsutbetaling ->
            if (erUtenforToleransegrense(
                    gammeltMånedsbeløp = sjekkgrunnlag.gjeldendeMånedsutbetaling,
                    nyttMånedsbeløp = nyMånedsutbetaling,
                )
            ) {
                MånedsutbetalingsendringVurdering.UtenforToleransegrense(nyMånedsutbetaling)
            } else {
                MånedsutbetalingsendringVurdering.InnenforToleransegrense(nyMånedsutbetaling)
            }
        },
        onFailure = {
            MånedsutbetalingsendringVurdering.UgyldigEndring(it.message ?: it::class.simpleName ?: "Ukjent feil")
        },
    )
}

private fun beregnNyMånedsutbetaling(
    sjekkpunkt: Sjekkpunkt,
    eksterntBeløp: Double,
    sjekkgrunnlag: SjekkgrunnlagForSak,
    måned: Måned,
    satsFactory: SatsFactory,
    clock: Clock,
): Int {
    val nyBeregning = BeregningStrategyFactory(
        clock = clock,
        satsFactory = satsFactory,
    ).beregn(
        grunnlagsdataOgVilkårsvurderinger = sjekkgrunnlag.gjeldendeVedtaksdata.grunnlagsdataOgVilkårsvurderinger
            .oppdaterFradragsgrunnlag(
                oppdaterRelevantFradragsgrunnlagMedEksterntBeløp(
                    sjekkpunkt = sjekkpunkt,
                    eksterntBeløp = eksterntBeløp,
                    sjekkgrunnlag = sjekkgrunnlag,
                ),
            ),
        begrunnelse = null,
        sakstype = sjekkgrunnlag.sjekkplan.sak.type,
    )

    return nyBeregning.getMånedsberegninger()
        .singleOrNull { it.måned == måned }
        ?.getSumYtelse()
        ?: error("Forventet nøyaktig én månedsberegning for måned $måned i fradragssjekken")
}

private fun oppdaterRelevantFradragsgrunnlagMedEksterntBeløp(
    sjekkpunkt: Sjekkpunkt,
    eksterntBeløp: Double,
    sjekkgrunnlag: SjekkgrunnlagForSak,
) = sjekkgrunnlag.gjeldendeVedtaksdata.grunnlagsdata.fradragsgrunnlag.let { fradragsgrunnlag ->
    val relevanteFradrag = fradragsgrunnlag.filter {
        it.fradragstype == sjekkpunkt.fradragstype &&
            it.tilhører == sjekkpunkt.tilhører &&
            it.utenlandskInntekt == null
    }

    require(relevanteFradrag.size == 1) {
        "Forventet nøyaktig ett relevant fradragsgrunnlag for type=${sjekkpunkt.fradragstype}, tilhører=${sjekkpunkt.tilhører}, men fant ${relevanteFradrag.size}"
    }

    val fradragSomSkalOppdateres = relevanteFradrag.single()

    fradragsgrunnlag.map {
        if (it == fradragSomSkalOppdateres) {
            it.oppdaterBeløpMedEksternRegulering(BigDecimal.valueOf(eksterntBeløp))
        } else {
            it
        }
    }
}

internal fun erUtenforToleransegrense(
    gammeltMånedsbeløp: Int,
    nyttMånedsbeløp: Int,
): Boolean {
    val endringIMånedsutbetaling = abs(nyttMånedsbeløp.toLong() - gammeltMånedsbeløp.toLong())
    val tillattEndringIMånedsutbetaling = (gammeltMånedsbeløp.toLong() * UTBETALINGS_TOLERANSE_PROSENT) / 100

    return endringIMånedsutbetaling > tillattEndringIMånedsutbetaling
}

private fun harSammeBeløp(
    lokaltBeløp: Double,
    eksterntBeløp: Double,
): Boolean {
    return BigDecimal.valueOf(lokaltBeløp).compareTo(BigDecimal.valueOf(eksterntBeløp)) == 0
}

private sealed interface MånedsutbetalingsendringVurdering {
    data class InnenforToleransegrense(val nyMånedsutbetaling: Int) : MånedsutbetalingsendringVurdering
    data class UtenforToleransegrense(val nyMånedsutbetaling: Int) : MånedsutbetalingsendringVurdering
    data class UgyldigEndring(val grunn: String) : MånedsutbetalingsendringVurdering
}

private fun formatBeløp(beløp: Double): String {
    return BigDecimal.valueOf(beløp).setScale(2, RoundingMode.HALF_UP).toPlainString()
}
