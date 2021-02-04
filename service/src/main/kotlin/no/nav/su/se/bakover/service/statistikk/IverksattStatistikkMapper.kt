package no.nav.su.se.bakover.service.statistikk

import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.ForNav
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import java.time.Clock

internal class IverksattStatistikkMapper(
    private val clock: Clock
) {
    fun map(søknadsbehandling: Søknadsbehandling.Iverksatt): Statistikk =
        when (søknadsbehandling) {
            is Søknadsbehandling.Iverksatt.Innvilget -> medBeregning(
                søknadsbehandling,
                søknadsbehandling.beregning
            )
            is Søknadsbehandling.Iverksatt.Avslag.MedBeregning -> medBeregning(
                søknadsbehandling,
                søknadsbehandling.beregning
            )
            is Søknadsbehandling.Iverksatt.Avslag.UtenBeregning -> utenBeregning(søknadsbehandling)
        }

    private fun medBeregning(søknadsbehandling: Søknadsbehandling.Iverksatt, beregning: no.nav.su.se.bakover.domain.beregning.Beregning) =
        Statistikk.Behandling(
            funksjonellTid = beregning.getPeriode().getFraOgMed().startOfDay(no.nav.su.se.bakover.common.zoneIdOslo),
            tekniskTid = no.nav.su.se.bakover.common.Tidspunkt.now(clock),
            registrertDato = when (val forNav = søknadsbehandling.søknad.søknadInnhold.forNav) {
                is no.nav.su.se.bakover.domain.ForNav.DigitalSøknad -> søknadsbehandling.opprettet.toLocalDate(no.nav.su.se.bakover.common.zoneIdOslo)
                is no.nav.su.se.bakover.domain.ForNav.Papirsøknad -> forNav.mottaksdatoForSøknad
            },
            mottattDato = søknadsbehandling.opprettet.toLocalDate(no.nav.su.se.bakover.common.zoneIdOslo),
            behandlingId = søknadsbehandling.id,
            sakId = søknadsbehandling.sakId,
            saksnummer = søknadsbehandling.saksnummer.nummer,
            behandlingStatus = søknadsbehandling.status,
            versjon = clock.millis(),
            saksbehandler = søknadsbehandling.saksbehandler.navIdent,
            beslutter = søknadsbehandling.attestering.attestant.navIdent,
            resultat = when (søknadsbehandling) {
                is Søknadsbehandling.Iverksatt.Innvilget -> "Innvilget"
                is Søknadsbehandling.Iverksatt.Avslag.MedBeregning,
                is Søknadsbehandling.Iverksatt.Avslag.UtenBeregning -> "Avslått"
            },
            resultatBegrunnelse = (søknadsbehandling as? Søknadsbehandling.Iverksatt.Avslag.MedBeregning)?.avslagsgrunner?.joinToString(","),
        )

    private fun utenBeregning(søknadsbehandling: Søknadsbehandling.Iverksatt.Avslag.UtenBeregning) = Statistikk.Behandling(
        funksjonellTid = søknadsbehandling.opprettet,
        tekniskTid = no.nav.su.se.bakover.common.Tidspunkt.now(clock),
        registrertDato = when (val forNav = søknadsbehandling.søknad.søknadInnhold.forNav) {
            is ForNav.DigitalSøknad -> søknadsbehandling.opprettet.toLocalDate(no.nav.su.se.bakover.common.zoneIdOslo)
            is ForNav.Papirsøknad -> forNav.mottaksdatoForSøknad
        },
        mottattDato = søknadsbehandling.opprettet.toLocalDate(no.nav.su.se.bakover.common.zoneIdOslo),
        behandlingId = søknadsbehandling.id,
        sakId = søknadsbehandling.sakId,
        saksnummer = søknadsbehandling.saksnummer.nummer,
        behandlingStatus = søknadsbehandling.status,
        versjon = clock.millis(),
        saksbehandler = søknadsbehandling.saksbehandler.navIdent,
        beslutter = søknadsbehandling.attestering.attestant.navIdent,
        resultat = "Avslått",
        resultatBegrunnelse = søknadsbehandling.avslagsgrunner.joinToString(","),
    )
}
