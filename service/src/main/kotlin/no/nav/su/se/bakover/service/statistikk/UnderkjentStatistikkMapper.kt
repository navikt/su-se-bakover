package no.nav.su.se.bakover.service.statistikk

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.ForNav
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import java.time.Clock

internal class UnderkjentStatistikkMapper(
    private val clock: Clock
) {
    fun map(søknadsbehandling: Søknadsbehandling.Underkjent): Statistikk =
        when (søknadsbehandling) {
            is Søknadsbehandling.Underkjent.Innvilget -> medBeregning(
                søknadsbehandling,
                søknadsbehandling.beregning
            )
            is Søknadsbehandling.Underkjent.Avslag.MedBeregning -> medBeregning(
                søknadsbehandling,
                søknadsbehandling.beregning
            )
            is Søknadsbehandling.Underkjent.Avslag.UtenBeregning -> utenBeregning(søknadsbehandling)
        }

    private fun medBeregning(søknadsbehandling: Søknadsbehandling.Underkjent, beregning: Beregning) =
        Statistikk.Behandling(
            funksjonellTid = beregning.getPeriode().getFraOgMed().startOfDay(zoneIdOslo),
            tekniskTid = Tidspunkt.now(clock),
            registrertDato = when (val forNav = søknadsbehandling.søknad.søknadInnhold.forNav) {
                is ForNav.DigitalSøknad -> søknadsbehandling.opprettet.toLocalDate(zoneIdOslo)
                is ForNav.Papirsøknad -> forNav.mottaksdatoForSøknad
            },
            mottattDato = søknadsbehandling.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = søknadsbehandling.id,
            sakId = søknadsbehandling.sakId,
            saksnummer = søknadsbehandling.saksnummer.nummer,
            behandlingStatus = søknadsbehandling.status,
            behandlingStatusBeskrivelse = "Sendt tilbake til saksbehandler",
            versjon = clock.millis(),
            saksbehandler = søknadsbehandling.saksbehandler.navIdent,
            beslutter = søknadsbehandling.attestering.attestant.navIdent,
        )

    private fun utenBeregning(søknadsbehandling: Søknadsbehandling.Underkjent) = Statistikk.Behandling(
        funksjonellTid = søknadsbehandling.opprettet,
        tekniskTid = Tidspunkt.now(clock),
        registrertDato = when (val forNav = søknadsbehandling.søknad.søknadInnhold.forNav) {
            is ForNav.DigitalSøknad -> søknadsbehandling.opprettet.toLocalDate(zoneIdOslo)
            is ForNav.Papirsøknad -> forNav.mottaksdatoForSøknad
        },
        mottattDato = søknadsbehandling.opprettet.toLocalDate(zoneIdOslo),
        behandlingId = søknadsbehandling.id,
        sakId = søknadsbehandling.sakId,
        saksnummer = søknadsbehandling.saksnummer.nummer,
        behandlingStatus = søknadsbehandling.status,
        behandlingStatusBeskrivelse = "Sendt tilbake til saksbehandler",
        versjon = clock.millis(),
        saksbehandler = søknadsbehandling.saksbehandler.navIdent,
        beslutter = søknadsbehandling.attestering.attestant.navIdent,
    )
}
