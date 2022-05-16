package no.nav.su.se.bakover.database.søknad

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.util.UUID

internal object SøknadRepoInternal {
    fun hentSøknadInternal(søknadId: UUID, session: Session): Søknad? = "select * from søknad where id=:id"
        .hent(mapOf("id" to søknadId), session) {
            it.toSøknad()
        }

    fun hentSøknaderInternal(sakId: UUID, session: Session) = "select * from søknad where sakId=:sakId"
        .hentListe(mapOf("sakId" to sakId), session) {
            it.toSøknad()
        }
}

internal fun Row.toSøknad(): Søknad {
    val sakId: UUID = uuid("sakId")
    val id: UUID = uuid("id")
    val søknadInnhold: SøknadInnhold = deserialize(string("søknadInnhold"))
    val opprettet: Tidspunkt = tidspunkt("opprettet")
    val lukket: LukketJson? = stringOrNull("lukket")?.let { objectMapper.readValue(it) }
    val oppgaveId: OppgaveId? = stringOrNull("oppgaveId")?.let { OppgaveId(it) }
    val journalpostId: JournalpostId? = stringOrNull("journalpostId")?.let { JournalpostId(it) }

    return when {
        lukket != null -> Søknad.Journalført.MedOppgave.Lukket(
            sakId = sakId,
            id = id,
            opprettet = opprettet,
            søknadInnhold = søknadInnhold,
            journalpostId = journalpostId!!,
            oppgaveId = oppgaveId!!,
            lukketAv = NavIdentBruker.Saksbehandler(lukket.saksbehandler),
            lukketTidspunkt = lukket.tidspunkt,
            lukketType = lukket.type,
        )
        journalpostId == null -> Søknad.Ny(
            sakId = sakId,
            id = id,
            opprettet = opprettet,
            søknadInnhold = søknadInnhold,
        )
        oppgaveId == null -> Søknad.Journalført.UtenOppgave(
            sakId = sakId,
            id = id,
            opprettet = opprettet,
            søknadInnhold = søknadInnhold,
            journalpostId = journalpostId,
        )
        else -> Søknad.Journalført.MedOppgave.IkkeLukket(
            sakId = sakId,
            id = id,
            opprettet = opprettet,
            søknadInnhold = søknadInnhold,
            journalpostId = journalpostId,
            oppgaveId = oppgaveId,
        )
    }
}
