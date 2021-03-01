package no.nav.su.se.bakover.database.søknadsbehandling

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import java.time.LocalDate
import java.util.UUID

interface SøknadsbehandlingRepo {
    fun lagre(søknadsbehandling: Søknadsbehandling)
    fun hent(id: UUID): Søknadsbehandling?
    fun hentForSak(sakId: UUID, session: Session): List<Søknadsbehandling>
    fun hentEventuellTidligereAttestering(id: UUID): Attestering?
    fun hentIverksatteBehandlingerUtenJournalposteringer(): List<Søknadsbehandling.Iverksatt.Innvilget>
    fun hentIverksatteBehandlingerUtenBrevbestillinger(): List<Søknadsbehandling.Iverksatt>
    fun hentAktiveInnvilgetBehandlinger(aktivDato: LocalDate): List<Søknadsbehandling.Iverksatt.Innvilget>
    fun hentBehandlingForUtbetaling(utbetalingId: UUID30): Søknadsbehandling.Iverksatt.Innvilget?
}
