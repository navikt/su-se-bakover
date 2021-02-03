package no.nav.su.se.bakover.service.statistikk

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.ForNav
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import java.time.Clock

internal class TilAttesteringMapper(
    private val clock: Clock
) {
    fun map(søknadsbehandlingTilAttestering: Søknadsbehandling.TilAttestering): Statistikk {
        val beregning = when (søknadsbehandlingTilAttestering) {
            is Søknadsbehandling.TilAttestering.Avslag.MedBeregning -> søknadsbehandlingTilAttestering.beregning
            is Søknadsbehandling.TilAttestering.Avslag.UtenBeregning -> null
            is Søknadsbehandling.TilAttestering.Innvilget -> søknadsbehandlingTilAttestering.beregning
        }

        return when (søknadsbehandlingTilAttestering) {
            is Søknadsbehandling.TilAttestering.Avslag.MedBeregning,
            is Søknadsbehandling.TilAttestering.Avslag.UtenBeregning,
            is Søknadsbehandling.TilAttestering.Innvilget -> Statistikk.Behandling(
                funksjonellTid = when (beregning) {
                    null -> søknadsbehandlingTilAttestering.opprettet
                    else -> beregning.getPeriode().getFraOgMed().startOfDay(zoneIdOslo)
                },
                tekniskTid = Tidspunkt.now(clock),
                registrertDato = when (val forNav = søknadsbehandlingTilAttestering.søknad.søknadInnhold.forNav) {
                    is ForNav.DigitalSøknad -> søknadsbehandlingTilAttestering.opprettet.toLocalDate(zoneIdOslo)
                    is ForNav.Papirsøknad -> forNav.mottaksdatoForSøknad
                },
                mottattDato = søknadsbehandlingTilAttestering.opprettet.toLocalDate(zoneIdOslo),
                behandlingId = søknadsbehandlingTilAttestering.id,
                sakId = søknadsbehandlingTilAttestering.sakId,
                saksnummer = søknadsbehandlingTilAttestering.saksnummer.nummer,
                behandlingStatus = søknadsbehandlingTilAttestering.status,
                versjon = clock.millis(),
                saksbehandler = søknadsbehandlingTilAttestering.saksbehandler.navIdent,
            )
        }
    }
}
