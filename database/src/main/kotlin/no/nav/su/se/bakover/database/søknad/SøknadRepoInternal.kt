package no.nav.su.se.bakover.database.søknad

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.domain.Søknad
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
        }.toMutableList()
}

internal fun Row.toSøknad(): Søknad {
    return Søknad(
        sakId = uuid("sakId"),
        id = uuid("id"),
        søknadInnhold = objectMapper.readValue(string("søknadInnhold")),
        opprettet = tidspunkt("opprettet"),
        lukket = stringOrNull("lukket")?.let { objectMapper.readValue<Søknad.Lukket>(it) },
        oppgaveId = stringOrNull("oppgaveId")?.let { OppgaveId(it) },
        journalpostId = stringOrNull("journalpostId")?.let { JournalpostId(it) },
    )
}
