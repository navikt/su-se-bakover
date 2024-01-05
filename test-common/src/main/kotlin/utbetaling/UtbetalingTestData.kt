package no.nav.su.se.bakover.test.utbetaling

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import beregning.domain.Beregning
import no.nav.su.se.bakover.common.Rekkefølge
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.Stønadsbehandling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.Utbetalinger
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.beregning
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.simulering.simulerNyUtbetaling
import no.nav.su.se.bakover.test.simulering.simuleringNy
import økonomi.domain.avstemming.Avstemmingsnøkkel
import økonomi.domain.kvittering.Kvittering
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.UtbetalingsinstruksjonForEtterbetalinger
import økonomi.domain.utbetaling.Utbetalingslinje
import økonomi.domain.utbetaling.Utbetalingsrequest
import java.time.Clock
import java.util.UUID

val avstemmingsnøkkel = Avstemmingsnøkkel(opprettet = fixedTidspunkt)
val utbetalingsRequest = Utbetalingsrequest("<xml></xml>")

fun utbetaling(
    clock: Clock,
    utbetalingslinje: Utbetalingslinje,
    vararg utbetalingslinjer: Utbetalingslinje,
): Utbetaling {
    return oversendtUtbetalingMedKvittering(
        clock = clock,
        utbetalingslinjer = nonEmptyListOf(utbetalingslinje) + utbetalingslinjer.toList(),
    )
}

fun nyUtbetalingForSimulering(
    sakOgBehandling: Pair<Sak, Stønadsbehandling>,
    beregning: Beregning,
    clock: Clock,
    aksepterKvitteringMedFeil: Boolean = false,
): Utbetaling.UtbetalingForSimulering {
    return sakOgBehandling.let { (sak, behandling) ->
        when (sak.type) {
            Sakstype.ALDER -> {
                Utbetalingsstrategi.NyAldersUtbetaling(
                    sakId = sak.id,
                    saksnummer = sak.saksnummer,
                    fnr = sak.fnr,
                    eksisterendeUtbetalinger = sak.utbetalinger,
                    behandler = saksbehandler,
                    sakstype = sak.type,
                    beregning = beregning,
                    clock = clock,
                    kjøreplan = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
                    aksepterKvitteringMedFeil = aksepterKvitteringMedFeil,
                ).generate()
            }

            Sakstype.UFØRE -> {
                Utbetalingsstrategi.NyUføreUtbetaling(
                    sakId = sak.id,
                    saksnummer = sak.saksnummer,
                    fnr = sak.fnr,
                    eksisterendeUtbetalinger = sak.utbetalinger,
                    behandler = saksbehandler,
                    sakstype = sak.type,
                    beregning = beregning,
                    clock = clock,
                    uføregrunnlag = behandling.vilkårsvurderinger.uføreVilkår().getOrFail().grunnlag,
                    kjøreplan = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
                    aksepterKvitteringMedFeil = aksepterKvitteringMedFeil,
                ).generate()
            }
        }
    }
}

fun nyUtbetalingSimulert(
    sakOgBehandling: Pair<Sak, Stønadsbehandling>,
    beregning: Beregning,
    clock: Clock,
    aksepterKvitteringMedFeil: Boolean = false,
): Utbetaling.SimulertUtbetaling {
    return sakOgBehandling.let { (sak, _) ->
        nyUtbetalingForSimulering(
            sakOgBehandling = sakOgBehandling,
            beregning = beregning,
            clock = clock,
            aksepterKvitteringMedFeil = aksepterKvitteringMedFeil,
        ).let {
            it.toSimulertUtbetaling(
                simulerNyUtbetaling(
                    utbetalingerPåSak = sak.utbetalinger,
                    utbetalingForSimulering = it,
                ).getOrFail(),
            )
        }
    }
}

fun nyUtbetalingOversendUtenKvittering(
    sakOgBehandling: Pair<Sak, Stønadsbehandling>,
    beregning: Beregning,
    clock: Clock,
): Utbetaling.OversendtUtbetaling.UtenKvittering {
    return sakOgBehandling.let { (_, _) ->
        nyUtbetalingSimulert(
            sakOgBehandling = sakOgBehandling,
            beregning = beregning,
            clock = clock,
        ).toOversendtUtbetaling(
            oppdragsmelding = Utbetalingsrequest("<xml></xml>"),
        )
    }
}

@Suppress("unused")
fun oversendtUtbetalingUtenKvittering(
    id: UUID30 = UUID30.randomUUID(),
    søknadsbehandling: IverksattSøknadsbehandling.Innvilget,
    avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.test.utbetaling.avstemmingsnøkkel,
    clock: Clock = fixedClock,
    utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(
        utbetalingslinjeNy(
            clock = clock,
            periode = søknadsbehandling.periode,
            rekkefølge = Rekkefølge.start(),
        ),
    ),
    eksisterendeUtbetalinger: Utbetalinger = Utbetalinger(),
): Utbetaling.OversendtUtbetaling.UtenKvittering {
    return oversendtUtbetalingUtenKvittering(
        id = id,
        periode = søknadsbehandling.periode,
        fnr = søknadsbehandling.fnr,
        sakId = søknadsbehandling.sakId,
        saksnummer = søknadsbehandling.saksnummer,
        clock = clock,
        utbetalingslinjer = utbetalingslinjer,
        avstemmingsnøkkel = avstemmingsnøkkel,
        eksisterendeUtbetalinger = eksisterendeUtbetalinger,
        beregning = søknadsbehandling.beregning,
    )
}

@Suppress("unused")
fun oversendtUtbetalingUtenKvittering(
    id: UUID30 = UUID30.randomUUID(),
    periode: Periode = år(2021),
    revurdering: RevurderingTilAttestering,
    avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.test.utbetaling.avstemmingsnøkkel,
    clock: Clock = fixedClock,
    utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(
        utbetalingslinjeNy(
            clock = clock,
            periode = periode,
            rekkefølge = Rekkefølge.start(),
        ),
    ),
    eksisterendeUtbetalinger: Utbetalinger = Utbetalinger(),
): Utbetaling.OversendtUtbetaling.UtenKvittering {
    return oversendtUtbetalingUtenKvittering(
        id = id,
        fnr = revurdering.fnr,
        sakId = revurdering.sakId,
        saksnummer = revurdering.saksnummer,
        clock = clock,
        utbetalingslinjer = utbetalingslinjer,
        avstemmingsnøkkel = avstemmingsnøkkel,
        eksisterendeUtbetalinger = eksisterendeUtbetalinger,
    )
}

fun oversendtUtbetalingUtenKvittering(
    id: UUID30 = UUID30.randomUUID(),
    periode: Periode = år(2021),
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    clock: Clock = fixedClock,
    opprettet: Tidspunkt = Tidspunkt.now(clock),
    eksisterendeUtbetalinger: Utbetalinger = Utbetalinger(),
    utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(
        utbetalingslinjeNy(
            clock = clock,
            periode = periode,
            forrigeUtbetalingslinjeId = eksisterendeUtbetalinger.lastOrNull()?.utbetalingslinjer?.lastOrNull()?.id,
        ),
    ),
    avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.test.utbetaling.avstemmingsnøkkel,
    beregning: Beregning = beregning(periode),
): Utbetaling.OversendtUtbetaling.UtenKvittering {
    return Utbetaling.UtbetalingForSimulering(
        id = id,
        opprettet = opprettet,
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        utbetalingslinjer = utbetalingslinjer,
        behandler = attestant,
        avstemmingsnøkkel = avstemmingsnøkkel,
        sakstype = Sakstype.UFØRE,
    ).toSimulertUtbetaling(
        simulering = simuleringNy(
            beregning = beregning,
            eksisterendeUtbetalinger = eksisterendeUtbetalinger,
            fnr = fnr,
            sakId = sakId,
            saksnummer = saksnummer,
            clock = clock,
        ),
    ).toOversendtUtbetaling(
        oppdragsmelding = utbetalingsRequest,
    )
}

@Suppress("unused")
fun simulertUtbetaling(
    id: UUID30 = UUID30.randomUUID(),
    periode: Periode = år(2021),
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    clock: Clock = fixedClock,
    utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(
        utbetalingslinjeNy(
            periode = periode,
            clock = clock,
        ),
    ),
    avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.test.utbetaling.avstemmingsnøkkel,
    eksisterendeUtbetalinger: Utbetalinger = Utbetalinger(),
): Utbetaling.SimulertUtbetaling {
    return Utbetaling.UtbetalingForSimulering(
        id = id,
        opprettet = Tidspunkt.now(clock),
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        utbetalingslinjer = utbetalingslinjer,
        behandler = attestant,
        avstemmingsnøkkel = avstemmingsnøkkel,
        // TODO("simulering_utbetaling_alder utled fra sak/behandling")
        sakstype = Sakstype.UFØRE,
    ).toSimulertUtbetaling(
        simulering = simuleringNy(
            fnr = fnr,
            eksisterendeUtbetalinger = eksisterendeUtbetalinger,
            clock = clock,
        ),
    )
}

/**
 * Defaultverdier:
 * - id: arbitrær
 * - utbetalingsstatus: OK
 * - type: NY
 */
fun oversendtUtbetalingMedKvittering(
    id: UUID30 = UUID30.randomUUID(),
    periode: Periode = år(2021),
    utbetalingsstatus: Kvittering.Utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    eksisterendeUtbetalinger: Utbetalinger = Utbetalinger(),
    clock: Clock = TikkendeKlokke(),
    utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(
        utbetalingslinjeNy(
            clock = clock,
            periode = periode,
            forrigeUtbetalingslinjeId = eksisterendeUtbetalinger.sisteUtbetalingslinjeId(),
        ),
    ),
    beregning: Beregning = beregning(periode),
    avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.test.utbetaling.avstemmingsnøkkel,
    kvittering: Kvittering = kvittering(
        utbetalingsstatus = utbetalingsstatus,
        clock = clock,
    ),
): Utbetaling.OversendtUtbetaling.MedKvittering {
    return oversendtUtbetalingUtenKvittering(
        id = id,
        periode = periode,
        fnr = fnr,
        sakId = sakId,
        saksnummer = saksnummer,
        clock = clock,
        eksisterendeUtbetalinger = eksisterendeUtbetalinger,
        utbetalingslinjer = utbetalingslinjer,
        avstemmingsnøkkel = avstemmingsnøkkel,
        beregning = beregning,
    ).toKvittertUtbetaling(
        kvittering = kvittering,
    )
}
