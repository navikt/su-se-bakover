package no.nav.su.se.bakover.domain.søknad

import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.domain.Søknad
import java.util.UUID

interface SøknadRepo {
    fun hentSøknad(søknadId: UUID): Søknad?
    fun opprettSøknad(søknad: Søknad.Ny)
    fun lukkSøknad(søknad: Søknad.Journalført.MedOppgave.Lukket, sessionContext: SessionContext = defaultSessionContext())
    fun oppdaterjournalpostId(søknad: Søknad.Journalført.UtenOppgave)
    fun oppdaterOppgaveId(søknad: Søknad.Journalført.MedOppgave)
    fun hentSøknaderUtenJournalpost(): List<Søknad.Ny>
    fun hentSøknaderMedJournalpostMenUtenOppgave(): List<Søknad.Journalført.UtenOppgave>

    fun defaultSessionContext(): SessionContext
}
