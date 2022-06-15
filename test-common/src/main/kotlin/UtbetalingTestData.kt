package no.nav.su.se.bakover.test

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingsinstruksjonForEtterbetalinger
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

val avstemmingsnøkkel = Avstemmingsnøkkel(opprettet = fixedTidspunkt)
val utbetalingsRequest = Utbetalingsrequest("<xml></xml>")

fun utbetalingslinje(
    id: UUID30 = UUID30.randomUUID(),
    periode: Periode = år(2021),
    clock: Clock = fixedClock,
    beløp: Int = 15000,
    forrigeUtbetalingslinjeId: UUID30? = null,
    uføregrad: Int = 50,
    kjøreplan: UtbetalingsinstruksjonForEtterbetalinger = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
) = Utbetalingslinje.Ny(
    id = id,
    opprettet = Tidspunkt.now(clock),
    fraOgMed = periode.fraOgMed,
    tilOgMed = periode.tilOgMed,
    forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId,
    beløp = beløp,
    uføregrad = Uføregrad.parse(uføregrad),
    utbetalingsinstruksjonForEtterbetalinger = kjøreplan,
)

fun nyUtbetalingForSimulering(
    sak: Sak,
    request: SimulerUtbetalingRequest.NyUtbetaling,
    clock: Clock,
): Utbetaling.UtbetalingForSimulering {
    return when (request) {
        is SimulerUtbetalingRequest.NyUtbetaling.Alder -> {
            Utbetalingsstrategi.NyAldersUtbetaling(
                sakId = sak.id,
                saksnummer = sak.saksnummer,
                fnr = sak.fnr,
                utbetalinger = sak.utbetalinger,
                behandler = request.saksbehandler,
                sakstype = sak.type,
                beregning = request.beregning,
                clock = clock,
                kjøreplan = request.utbetalingsinstruksjonForEtterbetaling,
            ).generate()
        }
        is SimulerUtbetalingRequest.NyUtbetaling.Uføre -> {
            Utbetalingsstrategi.NyUføreUtbetaling(
                sakId = sak.id,
                saksnummer = sak.saksnummer,
                fnr = sak.fnr,
                utbetalinger = sak.utbetalinger,
                behandler = request.saksbehandler,
                sakstype = sak.type,
                beregning = request.beregning,
                clock = clock,
                uføregrunnlag = request.uføregrunnlag,
                kjøreplan = request.utbetalingsinstruksjonForEtterbetaling,
            ).generate()
        }
    }
}

fun nyUtbetalingForSimulering(
    sakOgBehandling: Pair<Sak, Behandling>,
    beregning: Beregning,
    clock: Clock,
): Utbetaling.UtbetalingForSimulering {
    return sakOgBehandling.let { (sak, behandling) ->
        when (sak.type) {
            Sakstype.ALDER -> {
                Utbetalingsstrategi.NyAldersUtbetaling(
                    sakId = sak.id,
                    saksnummer = sak.saksnummer,
                    fnr = sak.fnr,
                    utbetalinger = sak.utbetalinger,
                    behandler = saksbehandler,
                    sakstype = sak.type,
                    beregning = beregning,
                    clock = clock,
                    kjøreplan = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
                ).generate()
            }
            Sakstype.UFØRE -> {
                Utbetalingsstrategi.NyUføreUtbetaling(
                    sakId = sak.id,
                    saksnummer = sak.saksnummer,
                    fnr = sak.fnr,
                    utbetalinger = sak.utbetalinger,
                    behandler = saksbehandler,
                    sakstype = sak.type,
                    beregning = beregning,
                    clock = clock,
                    uføregrunnlag = behandling.vilkårsvurderinger.uføreVilkår().getOrFail().grunnlag,
                    kjøreplan = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
                ).generate()
            }
        }
    }
}

fun nyUtbetalingSimulert(
    sakOgBehandling: Pair<Sak, Behandling>,
    beregning: Beregning,
    clock: Clock,
): Utbetaling.SimulertUtbetaling {
    return sakOgBehandling.let { (sak, _) ->
        nyUtbetalingForSimulering(
            sakOgBehandling = sakOgBehandling,
            beregning = beregning,
            clock = clock,
        ).let {
            it.toSimulertUtbetaling(
                simulerNyUtbetaling(
                    sak = sak,
                    utbetaling = it,
                ).getOrFail(),
            )
        }
    }
}

fun nyUtbetalingOversendtMedKvittering(
    sakOgBehandling: Pair<Sak, Behandling>,
    beregning: Beregning,
    clock: Clock,
): Utbetaling.OversendtUtbetaling.MedKvittering {
    return sakOgBehandling.let { (_, _) ->
        nyUtbetalingOversendUtenKvittering(
            sakOgBehandling = sakOgBehandling,
            beregning = beregning,
            clock = clock,
        ).toKvittertUtbetaling(kvittering())
    }
}

fun nyUtbetalingOversendUtenKvittering(
    sakOgBehandling: Pair<Sak, Behandling>,
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

fun opphørUtbetalingForSimulering(
    sakOgBehandling: Pair<Sak, Behandling>,
    opphørsdato: LocalDate,
    clock: Clock,
): Utbetaling.UtbetalingForSimulering {
    return sakOgBehandling.let { (sak, behandling) ->
        Utbetalingsstrategi.Opphør(
            sakId = behandling.sakId,
            saksnummer = behandling.saksnummer,
            fnr = behandling.fnr,
            utbetalinger = sak.utbetalinger,
            behandler = saksbehandler,
            opphørsDato = opphørsdato,
            clock = clock,
            sakstype = sakOgBehandling.first.type,
        ).generate()
    }
}

fun opphørUtbetalingSimulert(
    sakOgBehandling: Pair<Sak, Behandling>,
    opphørsdato: LocalDate,
    clock: Clock,
): Utbetaling.SimulertUtbetaling {
    return sakOgBehandling.let { (sak, behandling) ->
        opphørUtbetalingForSimulering(
            sakOgBehandling = sakOgBehandling,
            opphørsdato = opphørsdato,
            clock = clock,
        ).toSimulertUtbetaling(
            simuleringOpphørt(
                opphørsdato = opphørsdato,
                eksisterendeUtbetalinger = sak.utbetalinger,
                fnr = behandling.fnr,
                sakId = behandling.sakId,
                saksnummer = behandling.saksnummer,
                clock = clock,
            ),
        )
    }
}

fun opphørUtbetalingOversendUtenKvittering(
    sakOgBehandling: Pair<Sak, Behandling>,
    opphørsdato: LocalDate,
    clock: Clock,
): Utbetaling.OversendtUtbetaling.UtenKvittering {
    return sakOgBehandling.let { (_, _) ->
        opphørUtbetalingSimulert(
            sakOgBehandling = sakOgBehandling,
            opphørsdato = opphørsdato,
            clock = clock,
        ).toOversendtUtbetaling(
            oppdragsmelding = utbetalingsRequest,
        )
    }
}

@Suppress("unused")
fun oversendtUtbetalingUtenKvittering(
    søknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget,
    avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.test.avstemmingsnøkkel,
    clock: Clock = fixedClock,
    utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(
        utbetalingslinje(
            clock = clock,
            periode = søknadsbehandling.periode,
        ),
    ),
    eksisterendeUtbetalinger: List<Utbetaling> = emptyList(),
) = oversendtUtbetalingUtenKvittering(
    periode = søknadsbehandling.periode,
    fnr = søknadsbehandling.fnr,
    sakId = søknadsbehandling.sakId,
    saksnummer = søknadsbehandling.saksnummer,
    utbetalingslinjer = utbetalingslinjer,
    avstemmingsnøkkel = avstemmingsnøkkel,
    eksisterendeUtbetalinger = eksisterendeUtbetalinger,
    beregning = søknadsbehandling.beregning,
    clock = clock,
)

@Suppress("unused")
fun oversendtUtbetalingUtenKvittering(
    periode: Periode = år(2021),
    revurdering: RevurderingTilAttestering,
    avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.test.avstemmingsnøkkel,
    clock: Clock = fixedClock,
    utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(
        utbetalingslinje(
            clock = clock,
            periode = periode,
        ),
    ),
    eksisterendeUtbetalinger: List<Utbetaling> = emptyList(),
) = oversendtUtbetalingUtenKvittering(
    fnr = revurdering.fnr,
    sakId = revurdering.sakId,
    saksnummer = revurdering.saksnummer,
    utbetalingslinjer = utbetalingslinjer,
    avstemmingsnøkkel = avstemmingsnøkkel,
    eksisterendeUtbetalinger = eksisterendeUtbetalinger,
    clock = clock,
)

fun oversendtUtbetalingUtenKvittering(
    id: UUID30 = UUID30.randomUUID(),
    periode: Periode = år(2021),
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    clock: Clock = fixedClock,
    utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(
        utbetalingslinje(
            clock = clock,
            periode = periode,
        ),
    ),
    avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.test.avstemmingsnøkkel,
    type: Utbetaling.UtbetalingsType = Utbetaling.UtbetalingsType.NY,
    eksisterendeUtbetalinger: List<Utbetaling> = emptyList(),
    beregning: Beregning = beregning(periode),
): Utbetaling.OversendtUtbetaling.UtenKvittering {
    return Utbetaling.UtbetalingForSimulering(
        id = id,
        opprettet = Tidspunkt.now(clock),
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        utbetalingslinjer = utbetalingslinjer,
        type = type,
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

fun simulertUtbetaling(
    id: UUID30 = UUID30.randomUUID(),
    periode: Periode = år(2021),
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    clock: Clock = fixedClock,
    utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(
        utbetalingslinje(
            periode = periode,
            clock = clock,
        ),
    ),
    avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.test.avstemmingsnøkkel,
    type: Utbetaling.UtbetalingsType = Utbetaling.UtbetalingsType.NY,
    eksisterendeUtbetalinger: List<Utbetaling> = emptyList(),
): Utbetaling.SimulertUtbetaling {
    return Utbetaling.UtbetalingForSimulering(
        id = id,
        opprettet = Tidspunkt.now(clock),
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        utbetalingslinjer = utbetalingslinjer,
        type = type,
        behandler = attestant,
        avstemmingsnøkkel = avstemmingsnøkkel,
        sakstype = Sakstype.UFØRE, // TODO("simulering_utbetaling_alder utled fra sak/behandling")
    ).toSimulertUtbetaling(
        simulering = simuleringNy(
            fnr = fnr,
            eksisterendeUtbetalinger = eksisterendeUtbetalinger,
            clock = clock,
        ),
    )
}

fun simulertUtbetalingOpphør(
    id: UUID30 = UUID30.randomUUID(),
    periode: Periode = år(2021),
    opphørsdato: LocalDate = periode.fraOgMed,
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    behandler: NavIdentBruker = attestant,
    clock: Clock = fixedClock,
    avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.test.avstemmingsnøkkel,
    eksisterendeUtbetalinger: List<Utbetaling>,
): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling> {
    return Utbetaling.UtbetalingForSimulering(
        id = id,
        opprettet = Tidspunkt.now(clock),
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        utbetalingslinjer = nonEmptyListOf(
            Utbetalingslinje.Endring.Opphør(
                utbetalingslinje = eksisterendeUtbetalinger.last().sisteUtbetalingslinje(),
                virkningstidspunkt = opphørsdato,
                clock = clock,
            ),
        ),
        type = Utbetaling.UtbetalingsType.OPPHØR,
        behandler = behandler,
        avstemmingsnøkkel = avstemmingsnøkkel,
        sakstype = Sakstype.UFØRE, // TODO("simulering_utbetaling_alder utled fra sak/behandling")
    ).toSimulertUtbetaling(
        simulering = simuleringOpphørt(
            opphørsdato = opphørsdato,
            eksisterendeUtbetalinger = eksisterendeUtbetalinger,
            fnr = fnr,
            sakId = sakId,
            saksnummer = saksnummer,
        ),
    ).right()
}

fun simulertFeilutbetaling(
    id: UUID30 = UUID30.randomUUID(),
    periode: Periode = år(2021),
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    clock: Clock = fixedClock,
    utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(
        utbetalingslinje(
            periode = periode,
            clock = clock,
        ),
    ),
    avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.test.avstemmingsnøkkel,
    type: Utbetaling.UtbetalingsType = Utbetaling.UtbetalingsType.NY,
): Utbetaling.SimulertUtbetaling {
    return Utbetaling.UtbetalingForSimulering(
        id = id,
        opprettet = Tidspunkt.now(clock),
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        utbetalingslinjer = utbetalingslinjer,
        type = type,
        behandler = attestant,
        avstemmingsnøkkel = avstemmingsnøkkel,
        sakstype = Sakstype.UFØRE, // TODO("simulering_utbetaling_alder utled fra sak/behandling")
    ).toSimulertUtbetaling(
        simulering = simuleringFeilutbetaling(periode),
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
    type: Utbetaling.UtbetalingsType = Utbetaling.UtbetalingsType.NY,
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    eksisterendeUtbetalinger: List<Utbetaling> = emptyList(),
    clock: Clock = fixedClock,
): Utbetaling.OversendtUtbetaling.MedKvittering {
    return oversendtUtbetalingUtenKvittering(
        id = id,
        periode = periode,
        fnr = fnr,
        type = type,
        sakId = sakId,
        saksnummer = saksnummer,
        eksisterendeUtbetalinger = eksisterendeUtbetalinger,
        clock = clock,
    ).toKvittertUtbetaling(kvittering(utbetalingsstatus = utbetalingsstatus, clock = clock))
}

fun stansUtbetalingForSimulering(
    stansDato: LocalDate,
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    clock: Clock = fixedClock,
    eksisterendeUtbetalinger: List<Utbetaling> = listOf(oversendtUtbetalingMedKvittering(clock = clock)),
): Utbetaling.UtbetalingForSimulering {
    return Utbetalingsstrategi.Stans(
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        utbetalinger = eksisterendeUtbetalinger,
        behandler = saksbehandler,
        stansDato = stansDato,
        clock = clock,
        sakstype = Sakstype.UFØRE, // TODO("simulering_utbetaling_alder utled fra sak/behandling")
    ).generer().getOrFail()
}

fun simulertStansUtbetaling(
    stansDato: LocalDate,
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    clock: Clock = fixedClock,
    eksisterendeUtbetalinger: List<Utbetaling> = listOf(oversendtUtbetalingMedKvittering(clock = clock)),
): Utbetaling.SimulertUtbetaling {
    return stansUtbetalingForSimulering(
        stansDato = stansDato,
        fnr = fnr,
        sakId = sakId,
        saksnummer = saksnummer,
        eksisterendeUtbetalinger = eksisterendeUtbetalinger,
        clock = clock,
    ).toSimulertUtbetaling(
        simuleringStans(
            stansDato = stansDato,
            eksisterendeUtbetalinger = eksisterendeUtbetalinger,
            fnr = fnr,
            sakId = sakId,
            saksnummer = saksnummer,
            clock = clock,
        ),
    )
}

fun oversendtStansUtbetalingUtenKvittering(
    stansDato: LocalDate,
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    clock: Clock = fixedClock,
    eksisterendeUtbetalinger: List<Utbetaling> = listOf(oversendtUtbetalingMedKvittering(clock = clock)),
): Utbetaling.OversendtUtbetaling.UtenKvittering {
    return simulertStansUtbetaling(
        stansDato = stansDato,
        fnr = fnr,
        sakId = sakId,
        saksnummer = saksnummer,
        eksisterendeUtbetalinger = eksisterendeUtbetalinger,
        clock = clock,
    ).toOversendtUtbetaling(utbetalingsRequest)
}

fun gjenopptakUtbetalingForSimulering(
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    clock: Clock = fixedClock,
    eksisterendeUtbetalinger: List<Utbetaling> = listOf(
        oversendtStansUtbetalingUtenKvittering(
            stansDato = LocalDate.now(clock).plusMonths(1).startOfMonth(),
            clock = clock,
        ),
    ),
): Utbetaling.UtbetalingForSimulering {
    return Utbetalingsstrategi.Gjenoppta(
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        utbetalinger = eksisterendeUtbetalinger,
        behandler = saksbehandler,
        clock = clock,
        sakstype = Sakstype.UFØRE, // TODO("simulering_utbetaling_alder utled fra sak/behandling")
    ).generer().getOrFail("Skal kunne generere utbetaling for gjenopptak")
}

fun simulertGjenopptakUtbetaling(
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    clock: Clock = fixedClock,
    eksisterendeUtbetalinger: List<Utbetaling> = listOf(
        oversendtStansUtbetalingUtenKvittering(
            stansDato = LocalDate.now(clock).plusMonths(1).startOfMonth(),
        ),
    ),
): Utbetaling.SimulertUtbetaling {
    return gjenopptakUtbetalingForSimulering(
        fnr = fnr,
        sakId = sakId,
        saksnummer = saksnummer,
        eksisterendeUtbetalinger = eksisterendeUtbetalinger,
        clock = clock,
    ).toSimulertUtbetaling(
        simuleringGjenopptak(
            eksisterendeUtbetalinger = eksisterendeUtbetalinger,
            fnr = fnr,
            sakId = sakId,
            saksnummer = saksnummer,
        ),
    )
}

fun oversendtGjenopptakUtbetalingUtenKvittering(
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    clock: Clock = fixedClock,
    eksisterendeUtbetalinger: List<Utbetaling> = listOf(
        oversendtStansUtbetalingUtenKvittering(
            stansDato = LocalDate.now(clock).plusMonths(1).startOfMonth(),
        ),
    ),
): Utbetaling.OversendtUtbetaling.UtenKvittering {
    return simulertGjenopptakUtbetaling(
        fnr = fnr,
        sakId = sakId,
        saksnummer = saksnummer,
        eksisterendeUtbetalinger = eksisterendeUtbetalinger,
        clock = clock,
    ).toOversendtUtbetaling(utbetalingsRequest)
}

/**
 * Defaultverdier:
 * - utbetalingsstatus: OK
 */
fun kvittering(
    utbetalingsstatus: Kvittering.Utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
    clock: Clock = fixedClock,
) = Kvittering(
    utbetalingsstatus = utbetalingsstatus,
    originalKvittering = "<xml></xml>",
    mottattTidspunkt = Tidspunkt.now(clock),
)
