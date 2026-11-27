package no.nav.su.se.bakover.client.oppdrag.simulering.historisk

import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequest
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdertMånedsresultat
import no.nav.su.se.bakover.domain.historisk.revurdering.utbetaling.HistoriskSimuleringsforespørsel
import økonomi.domain.avstemming.Avstemmingsnøkkel
import java.math.BigDecimal

// Kandidat for utprøving, ikke en verifisert kontrakt om periodeeffekt eller kobling til SU-linjer.
internal fun HistoriskSimuleringsforespørsel.byggMeldingskandidat(tidspunkt: Tidspunkt) =
    byggHistoriskInfotrygdOppdragRequest(
        grunnlag = grunnlag,
        saksnummer = saksnummer,
        fnr = fnr,
        utbetalingId = UUID30.randomUUID(),
        attestant = saksbehandler,
        avstemmingsnøkkel = Avstemmingsnøkkel(tidspunkt),
        kjøreplan = UtbetalingRequest.Oppdragslinje.Kjøreplan.NEI,
        format = UverifisertHistoriskOppdragsformat(
            kodeEndring = if (harEksisterendeSuOppdrag) {
                UtbetalingRequest.KodeEndring.ENDRING
            } else {
                UtbetalingRequest.KodeEndring.NY
            },
            opphørslinjer = grunnlag.periode.måneder().associateWith { måned ->
                UverifisertInfotrygdOpphørslinje(
                    id = UUID30.randomUUID(),
                    kodeEndringLinje = UtbetalingRequest.Oppdragslinje.KodeEndringLinje.NY,
                    vedtaksperiode = måned,
                    sats = BigDecimal.ZERO,
                )
            },
            erstatningslinjer = grunnlag.månedsresultater.filterValues {
                it is HistoriskInfotrygdRevurdertMånedsresultat.Ytelse
            }.mapValues { HistoriskErstatningslinjevalg(UUID30.randomUUID(), null) },
        ),
    )
