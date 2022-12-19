package no.nav.su.se.bakover.domain.søknadsbehandling.opprett

import arrow.core.Either
import arrow.core.Tuple4
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import java.time.Clock
import java.util.UUID

/**
 * Begrensninger for opprettelse av ny søknadsbehandling:
 * - Kun én søknadsbehandling per søknad. På sikt kan denne begrensningen løses litt opp. Eksempelvis ved omgjøring etter klage eller eget tiltak.
 * - Søknaden må være journalført, oppgave må ha vært opprettet og søknaden kan ikke være lukket.
 *
 * Siden stønadsperioden velges etter man har opprettet søknadsbehandlingen, vil ikke stønadsperiodebegresningene gjelde for dette steget.
 */
fun Sak.opprettNySøknadsbehandling(
    søknadId: UUID,
    clock: Clock,
    saksbehandler: NavIdentBruker.Saksbehandler,
): Either<Sak.KunneIkkeOppretteSøknadsbehandling, Tuple4<Sak, NySøknadsbehandling, Søknadsbehandling.Vilkårsvurdert.Uavklart, StatistikkEvent.Behandling.Søknad.Opprettet>> {
    if (!harIngenÅpneBehandlinger()) {
        // TODO jah: Fjern denne når de nye begrensningene er på plass for opprettelse og iverksettelse.
        return Sak.KunneIkkeOppretteSøknadsbehandling.HarÅpenBehandling.left()
    }
    val søknad = hentSøknad(søknadId).fold(
        ifLeft = { throw IllegalArgumentException("Fant ikke søknad $søknadId") },
        ifRight = {
            if (it is Søknad.Journalført.MedOppgave.Lukket) {
                return Sak.KunneIkkeOppretteSøknadsbehandling.ErLukket.left()
            }
            if (it !is Søknad.Journalført.MedOppgave) {
                // TODO Prøv å opprette oppgaven hvis den mangler? (systembruker blir kanskje mest riktig?)
                return Sak.KunneIkkeOppretteSøknadsbehandling.ManglerOppgave.left()
            }
            if (hentSøknadsbehandlingForSøknad(søknadId).isNotEmpty()) {
                return Sak.KunneIkkeOppretteSøknadsbehandling.FinnesAlleredeSøknadsehandlingForSøknad.left()
            }
            it
        },
    )

    return NySøknadsbehandling(
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.now(clock),
        sakId = this.id,
        søknad = søknad,
        oppgaveId = søknad.oppgaveId,
        fnr = søknad.fnr,
        avkorting = this.hentUteståendeAvkortingForSøknadsbehandling().fold({ it }, { it }).kanIkke(),
        sakstype = søknad.type,
        saksbehandler = saksbehandler,
    ).let { nySøknadsbehandling ->
        val søknadsbehandling = nySøknadsbehandling.toSøknadsbehandling(this.saksnummer)
        Tuple4(
            this.copy(
                søknadsbehandlinger = this.søknadsbehandlinger + søknadsbehandling,
            ),
            nySøknadsbehandling,
            søknadsbehandling,
            StatistikkEvent.Behandling.Søknad.Opprettet(søknadsbehandling, saksbehandler),
        ).right()
    }
}
