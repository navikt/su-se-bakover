package no.nav.su.se.bakover.database.søknad

import no.nav.su.se.bakover.domain.Søknad
import java.util.UUID

interface SøknadRepo {
    fun hentSøknad(søknadId: UUID): Søknad?
    fun opprettSøknad(søknad: Søknad.Ny)
    fun oppdaterSøknad(søknad: Søknad.Lukket)
    fun harSøknadPåbegyntBehandling(søknadId: UUID): Boolean
    fun oppdaterjournalpostId(søknad: Søknad.Journalført.UtenOppgave)
    fun oppdaterOppgaveId(søknad: Søknad.Journalført.MedOppgave)
    fun hentSøknaderUtenJournalpost(): List<Søknad.Ny>
    fun hentSøknaderMedJournalpostMenUtenOppgave(): List<Søknad.Journalført.UtenOppgave>
}
