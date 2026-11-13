package no.nav.su.se.bakover.client.oppdrag.simulering.historisk

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.oppdrag.OppdragDefaults
import no.nav.su.se.bakover.client.oppdrag.OppdragslinjeDefaults
import no.nav.su.se.bakover.client.oppdrag.toOppdragDate
import no.nav.su.se.bakover.client.oppdrag.toOppdragTimestamp
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequest
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequest.Oppdragslinje
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdertMånedsresultat
import no.nav.su.se.bakover.domain.historisk.revurdering.utbetaling.HistoriskInfotrygdOppdragsgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.avstemming.toFagområde
import økonomi.domain.avstemming.Avstemmingsnøkkel
import java.math.BigDecimal

// Utforskende meldingsformat. Valgene er ikke en verifisert kontrakt med Oppdrag.
internal data class UverifisertHistoriskOppdragsformat(
    val kodeEndring: UtbetalingRequest.KodeEndring,
    val opphørslinjer: Map<Måned, UverifisertInfotrygdOpphørslinje>,
    val erstatningslinjer: Map<Måned, HistoriskErstatningslinjevalg>,
)

internal data class UverifisertInfotrygdOpphørslinje(
    val id: UUID30,
    val kodeEndringLinje: Oppdragslinje.KodeEndringLinje,
    val vedtaksperiode: Periode,
    val sats: BigDecimal,
)

internal data class HistoriskErstatningslinjevalg(
    val id: UUID30,
    val refDelytelseId: UUID30?,
)

internal fun byggHistoriskInfotrygdOppdragRequest(
    grunnlag: HistoriskInfotrygdOppdragsgrunnlag,
    saksnummer: Saksnummer,
    fnr: Fnr,
    utbetalingId: UUID30,
    attestant: NavIdentBruker.Attestant,
    avstemmingsnøkkel: Avstemmingsnøkkel,
    kjøreplan: Oppdragslinje.Kjøreplan,
    harEksisterendeSuOppdrag: Boolean,
    format: UverifisertHistoriskOppdragsformat,
): Either<KunneIkkeByggeHistoriskInfotrygdOppdragRequest, UtbetalingRequest.OppdragRequest> {
    if (harEksisterendeSuOppdrag) {
        return KunneIkkeByggeHistoriskInfotrygdOppdragRequest.EksisterendeSuOppdragIkkeStøttet.left()
    }
    if (format.kodeEndring == UtbetalingRequest.KodeEndring.UENDRET) {
        return KunneIkkeByggeHistoriskInfotrygdOppdragRequest.UendretOppdragIkkeStøttet.left()
    }
    if (format.opphørslinjer.keys != grunnlag.månedsresultater.keys) {
        return KunneIkkeByggeHistoriskInfotrygdOppdragRequest.OpphørslinjevalgDekkerIkkePerioden.left()
    }
    val nyeYtelser = grunnlag.månedsresultater.filterValues {
        it is HistoriskInfotrygdRevurdertMånedsresultat.Ytelse
    }.keys
    if (format.erstatningslinjer.keys != nyeYtelser) {
        return KunneIkkeByggeHistoriskInfotrygdOppdragRequest.ErstatningslinjevalgStemmerIkkeMedBeregningen.left()
    }
    val linjeIder = format.opphørslinjer.values.map { it.id } + format.erstatningslinjer.values.map { it.id }
    if (linjeIder.distinct().size != linjeIder.size) {
        return KunneIkkeByggeHistoriskInfotrygdOppdragRequest.DupliserteLinjeIder.left()
    }
    format.opphørslinjer.forEach { (måned, valg) ->
        val normalisertSats = valg.sats.stripTrailingZeros()
        val antallSifre = normalisertSats.precision() + maxOf(-normalisertSats.scale(), 0)
        if (valg.sats.signum() < 0 || normalisertSats.scale() > 2 || antallSifre > 13) {
            return KunneIkkeByggeHistoriskInfotrygdOppdragRequest.UgyldigOpphørssats(måned).left()
        }
    }

    val fagområde = Sakstype.ALDER.toFagområde().toString()
    fun linje(
        id: UUID30,
        periode: Periode,
        sats: BigDecimal,
        kodeEndringLinje: Oppdragslinje.KodeEndringLinje,
        opphørFra: Måned?,
        refDelytelseId: UUID30?,
    ) = Oppdragslinje(
        kodeEndringLinje = kodeEndringLinje,
        kodeStatusLinje = opphørFra?.let { Oppdragslinje.KodeStatusLinje.OPPHØR },
        datoStatusFom = opphørFra?.fraOgMed?.toOppdragDate(),
        delytelseId = id.toString(),
        kodeKlassifik = fagområde,
        datoVedtakFom = periode.fraOgMed.toOppdragDate(),
        datoVedtakTom = periode.tilOgMed.toOppdragDate(),
        sats = sats.toPlainString(),
        fradragTillegg = OppdragslinjeDefaults.fradragEllerTillegg,
        typeSats = OppdragslinjeDefaults.typeSats,
        brukKjoreplan = kjøreplan,
        saksbehId = OppdragslinjeDefaults.SAKSBEHANDLER_ID,
        utbetalesTilId = fnr.toString(),
        utbetalingId = utbetalingId.toString(),
        refDelytelseId = refDelytelseId?.toString(),
        refFagsystemId = refDelytelseId?.let { saksnummer.toString() },
        grad = null,
        attestant = listOf(Oppdragslinje.Attestant(attestant.navIdent)),
    )

    val linjer = grunnlag.periode.måneder().map { måned ->
        val valg = format.opphørslinjer.getValue(måned)
        linje(
            id = valg.id,
            periode = valg.vedtaksperiode,
            sats = valg.sats,
            kodeEndringLinje = valg.kodeEndringLinje,
            opphørFra = måned,
            refDelytelseId = null,
        )
    }.toMutableList()
    val foregåendeLinjeIder = format.opphørslinjer.values.map { it.id }.toMutableSet()
    grunnlag.månedsresultater.forEach { (måned, resultat) ->
        when (resultat) {
            is HistoriskInfotrygdRevurdertMånedsresultat.Opphør -> Unit
            is HistoriskInfotrygdRevurdertMånedsresultat.Ytelse -> {
                val valg = format.erstatningslinjer.getValue(måned)
                if (valg.refDelytelseId != null && valg.refDelytelseId !in foregåendeLinjeIder) {
                    return KunneIkkeByggeHistoriskInfotrygdOppdragRequest.UgyldigLinjereferanse(måned).left()
                }
                linjer.add(
                    linje(
                        id = valg.id,
                        periode = måned,
                        sats = resultat.beløp,
                        kodeEndringLinje = Oppdragslinje.KodeEndringLinje.ENDRING,
                        opphørFra = null,
                        refDelytelseId = valg.refDelytelseId,
                    ),
                )
                foregåendeLinjeIder.add(valg.id)
            }
        }
    }
    return UtbetalingRequest.OppdragRequest(
        kodeAksjon = UtbetalingRequest.KodeAksjon.SIMULERING,
        kodeEndring = format.kodeEndring,
        kodeFagomraade = fagområde,
        fagsystemId = saksnummer.toString(),
        utbetFrekvens = OppdragDefaults.utbetalingsfrekvens,
        oppdragGjelderId = fnr.toString(),
        datoOppdragGjelderFom = OppdragDefaults.datoOppdragGjelderFom,
        saksbehId = OppdragDefaults.SAKSBEHANDLER_ID,
        avstemming = UtbetalingRequest.Avstemming(
            kodeKomponent = OppdragDefaults.KODE_KOMPONENT,
            nokkelAvstemming = avstemmingsnøkkel.toString(),
            tidspktMelding = avstemmingsnøkkel.opprettet.toOppdragTimestamp(),
        ),
        oppdragsEnheter = listOf(OppdragDefaults.oppdragsenhet),
        oppdragslinjer = linjer,
    ).right()
}

internal sealed interface KunneIkkeByggeHistoriskInfotrygdOppdragRequest {
    data object EksisterendeSuOppdragIkkeStøttet : KunneIkkeByggeHistoriskInfotrygdOppdragRequest
    data object UendretOppdragIkkeStøttet : KunneIkkeByggeHistoriskInfotrygdOppdragRequest
    data object OpphørslinjevalgDekkerIkkePerioden : KunneIkkeByggeHistoriskInfotrygdOppdragRequest
    data object ErstatningslinjevalgStemmerIkkeMedBeregningen : KunneIkkeByggeHistoriskInfotrygdOppdragRequest
    data object DupliserteLinjeIder : KunneIkkeByggeHistoriskInfotrygdOppdragRequest
    data class UgyldigOpphørssats(val måned: Måned) : KunneIkkeByggeHistoriskInfotrygdOppdragRequest
    data class UgyldigLinjereferanse(val måned: Måned) : KunneIkkeByggeHistoriskInfotrygdOppdragRequest
}
