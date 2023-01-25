package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingsHandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandlingshendelse
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandlingshistorikk
import no.nav.su.se.bakover.domain.vilkår.Inngangsvilkår
import java.time.Clock

/**
 * @param clock ignorerer clock hvis vi sender med tidspunkt
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
    historikk: List<Søknadsbehandlingshendelse> = listOf(nySøknadsbehandlingshendelse()),
): Søknadsbehandlingshistorikk {
    return Søknadsbehandlingshistorikk.createFromExisting(historikk)
}

fun nySøknadsbehandlingshistorikkStartetBehandling(
    clock: Clock = fixedClock,
    tidspunkt: Tidspunkt = Tidspunkt.now(clock),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): Søknadsbehandlingshistorikk {
    return nySøknadsbehandlingshistorikk(
        listOf(
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
        listOf(
            Søknadsbehandlingshendelse(
                tidspunkt = tidspunkt,
                saksbehandler = saksbehandler,
                handling = SøknadsbehandlingsHandling.OppdatertVilkår(Inngangsvilkår.Uførhet),
            ),
            Søknadsbehandlingshendelse(
                tidspunkt = tidspunkt,
                saksbehandler = saksbehandler,
                handling = SøknadsbehandlingsHandling.OppdatertVilkår(Inngangsvilkår.Flyktning),
            ),
            Søknadsbehandlingshendelse(
                tidspunkt = tidspunkt,
                saksbehandler = saksbehandler,
                handling = SøknadsbehandlingsHandling.OppdatertVilkår(Inngangsvilkår.LovligOpphold),
            ),
            Søknadsbehandlingshendelse(
                tidspunkt = tidspunkt,
                saksbehandler = saksbehandler,
                handling = SøknadsbehandlingsHandling.OppdatertVilkår(Inngangsvilkår.FastOppholdINorge),
            ),
            Søknadsbehandlingshendelse(
                tidspunkt = tidspunkt,
                saksbehandler = saksbehandler,
                handling = SøknadsbehandlingsHandling.OppdatertVilkår(Inngangsvilkår.Institusjonsopphold),
            ),
            Søknadsbehandlingshendelse(
                tidspunkt = tidspunkt,
                saksbehandler = saksbehandler,
                handling = SøknadsbehandlingsHandling.OppdatertVilkår(Inngangsvilkår.Utenlandsopphold),
            ),
            Søknadsbehandlingshendelse(
                tidspunkt = tidspunkt,
                saksbehandler = saksbehandler,
                handling = SøknadsbehandlingsHandling.TattStillingTilEPS,
            ),
            Søknadsbehandlingshendelse(
                tidspunkt = tidspunkt,
                saksbehandler = saksbehandler,
                handling = SøknadsbehandlingsHandling.OppdatertVilkår(Inngangsvilkår.Formue),
            ),
            Søknadsbehandlingshendelse(
                tidspunkt = tidspunkt,
                saksbehandler = saksbehandler,
                handling = SøknadsbehandlingsHandling.OppdatertVilkår(Inngangsvilkår.PersonligOppmøte),
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
        listOf(
            Søknadsbehandlingshendelse(
                tidspunkt = tidspunkt,
                saksbehandler = saksbehandler,
                handling = SøknadsbehandlingsHandling.FullførBosituasjon,
            ),
            Søknadsbehandlingshendelse(
                tidspunkt = tidspunkt,
                saksbehandler = saksbehandler,
                handling = SøknadsbehandlingsHandling.OppdatertFradrag,
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
