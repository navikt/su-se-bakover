package no.nav.su.se.bakover.test

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingsHandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandlingshendelse
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandlingshistorikk
import java.time.Clock

/**
 * @param tidspunkt ignorerer clock hvis vi sender med tidspunkt
 */
fun nySøknadsbehandlingshendelse(
    clock: Clock = fixedClock,
    tidspunkt: Tidspunkt = Tidspunkt.now(clock),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    handling: SøknadsbehandlingsHandling = SøknadsbehandlingsHandling.StartetBehandling,
): Søknadsbehandlingshendelse {
    return Søknadsbehandlingshendelse(
        tidspunkt = tidspunkt,
        saksbehandler = saksbehandler,
        handling = handling,
    )
}

fun nySøknadsbehandlingshistorikk(
    historikk: NonEmptyList<Søknadsbehandlingshendelse> = nonEmptyListOf(nySøknadsbehandlingshendelse()),
): Søknadsbehandlingshistorikk {
    return Søknadsbehandlingshistorikk.createFromExisting(historikk)
}

fun nySøknadsbehandlingshistorikkStartetBehandling(
    clock: Clock = fixedClock,
    tidspunkt: Tidspunkt = Tidspunkt.now(clock),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Søknadsbehandlingshistorikk {
    return nySøknadsbehandlingshistorikk(
        nonEmptyListOf(
            Søknadsbehandlingshendelse(
                tidspunkt = tidspunkt,
                saksbehandler = saksbehandler,
                handling = SøknadsbehandlingsHandling.StartetBehandling,
            ),
        ),
    )
}

fun nySøknadsbehandlingshistorikkOppdatertStønadsperiode(
    clock: Clock = fixedClock,
    tidspunkt: Tidspunkt = Tidspunkt.now(clock),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Søknadsbehandlingshistorikk {
    return nySøknadsbehandlingshistorikkStartetBehandling().leggTilNyHendelse(
        Søknadsbehandlingshendelse(
            tidspunkt = tidspunkt,
            saksbehandler = saksbehandler,
            handling = SøknadsbehandlingsHandling.OppdatertStønadsperiode,
        ),
    )
}

fun nySøknadsbehandlingshistorikkAlleVilkår(
    clock: Clock = fixedClock,
    tidspunkt: Tidspunkt = Tidspunkt.now(clock),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Søknadsbehandlingshistorikk {
    return nySøknadsbehandlingshistorikkOppdatertStønadsperiode().leggTilNyeHendelser(
        nonEmptyListOf(
            Søknadsbehandlingshendelse(
                tidspunkt = tidspunkt,
                saksbehandler = saksbehandler,
                handling = SøknadsbehandlingsHandling.OppdatertUførhet,
            ),
            Søknadsbehandlingshendelse(
                tidspunkt = tidspunkt,
                saksbehandler = saksbehandler,
                handling = SøknadsbehandlingsHandling.OppdatertFlyktning,
            ),
            Søknadsbehandlingshendelse(
                tidspunkt = tidspunkt,
                saksbehandler = saksbehandler,
                handling = SøknadsbehandlingsHandling.OppdatertLovligOpphold,
            ),
            Søknadsbehandlingshendelse(
                tidspunkt = tidspunkt,
                saksbehandler = saksbehandler,
                handling = SøknadsbehandlingsHandling.OppdatertFastOppholdINorge,
            ),
            Søknadsbehandlingshendelse(
                tidspunkt = tidspunkt,
                saksbehandler = saksbehandler,
                handling = SøknadsbehandlingsHandling.OppdatertInstitusjonsopphold,
            ),
            Søknadsbehandlingshendelse(
                tidspunkt = tidspunkt,
                saksbehandler = saksbehandler,
                handling = SøknadsbehandlingsHandling.OppdatertUtenlandsopphold,
            ),
            Søknadsbehandlingshendelse(
                tidspunkt = tidspunkt,
                saksbehandler = saksbehandler,
                handling = SøknadsbehandlingsHandling.TattStillingTilEPS,
            ),
            Søknadsbehandlingshendelse(
                tidspunkt = tidspunkt,
                saksbehandler = saksbehandler,
                handling = SøknadsbehandlingsHandling.OppdatertFormue,
            ),
            Søknadsbehandlingshendelse(
                tidspunkt = tidspunkt,
                saksbehandler = saksbehandler,
                handling = SøknadsbehandlingsHandling.OppdatertPersonligOppmøte,
            ),
        ),
    )
}

fun nySøknadsbehandlingshistorikkAlleVilkårMedBosituasjonOgFradrag(
    clock: Clock = fixedClock,
    tidspunkt: Tidspunkt = Tidspunkt.now(clock),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Søknadsbehandlingshistorikk {
    return nySøknadsbehandlingshistorikkAlleVilkår(
        clock,
        tidspunkt,
        saksbehandler,
    ).leggTilNyeHendelser(
        nonEmptyListOf(
            Søknadsbehandlingshendelse(
                tidspunkt = tidspunkt,
                saksbehandler = saksbehandler,
                handling = SøknadsbehandlingsHandling.OppdatertFradragsgrunnlag,
            ),
        ),
    )
}

fun nySøknadsbehandlingshistorikkBeregnet(
    clock: Clock = fixedClock,
    tidspunkt: Tidspunkt = Tidspunkt.now(clock),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Søknadsbehandlingshistorikk {
    return nySøknadsbehandlingshistorikkAlleVilkårMedBosituasjonOgFradrag(
        clock = clock,
        tidspunkt = tidspunkt,
        saksbehandler = saksbehandler,
    ).leggTilNyHendelse(
        nySøknadsbehandlingshendelse(clock, tidspunkt, saksbehandler, SøknadsbehandlingsHandling.Beregnet),
    )
}

fun nySøknadsbehandlingshistorikkSimulert(
    clock: Clock = fixedClock,
    tidspunkt: Tidspunkt = Tidspunkt.now(clock),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Søknadsbehandlingshistorikk {
    return nySøknadsbehandlingshistorikkBeregnet(
        clock = clock,
        tidspunkt = tidspunkt,
        saksbehandler = saksbehandler,
    ).leggTilNyHendelse(
        nySøknadsbehandlingshendelse(clock, tidspunkt, saksbehandler, SøknadsbehandlingsHandling.Simulert),
    )
}

fun nySøknadsbehandlingshistorikkSendtTilAttestering(
    clock: Clock = fixedClock,
    tidspunkt: Tidspunkt = Tidspunkt.now(clock),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Søknadsbehandlingshistorikk {
    return nySøknadsbehandlingshistorikkSimulert(
        clock = clock,
        tidspunkt = tidspunkt,
        saksbehandler = saksbehandler,
    ).leggTilNyHendelse(
        nySøknadsbehandlingshendelse(clock, tidspunkt, saksbehandler, SøknadsbehandlingsHandling.SendtTilAttestering),
    )
}

fun nySøknadsbehandlingshistorikkSendtTilAttesteringAvslåttBeregning(
    clock: Clock = fixedClock,
    tidspunkt: Tidspunkt = Tidspunkt.now(clock),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Søknadsbehandlingshistorikk {
    return nySøknadsbehandlingshistorikkBeregnet(
        clock = clock,
        tidspunkt = tidspunkt,
        saksbehandler = saksbehandler,
    ).leggTilNyHendelse(
        nySøknadsbehandlingshendelse(clock, tidspunkt, saksbehandler, SøknadsbehandlingsHandling.SendtTilAttestering),
    )
}

fun nySøknadsbehandlingshistorikkSendtTilAttesteringAvslått(
    clock: Clock = fixedClock,
    tidspunkt: Tidspunkt = Tidspunkt.now(clock),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Søknadsbehandlingshistorikk {
    return nySøknadsbehandlingshistorikkAlleVilkår(
        clock = clock,
        tidspunkt = tidspunkt,
        saksbehandler = saksbehandler,
    ).leggTilNyHendelse(
        nySøknadsbehandlingshendelse(clock, tidspunkt, saksbehandler, SøknadsbehandlingsHandling.SendtTilAttestering),
    )
}
