package no.nav.su.se.bakover.database.søknad

import kotliquery.Row
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.deserializeNullable
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.brev.Brevvalg
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadinnhold.SøknadInnhold
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

    val innsendtAv = stringOrNull("ident")?.let {
        /**
         * Vi har ikke alltid lagret denne informasjonen og verdien eksisterer dermed ikke for alle søknader.
         * Gjøre et kvalifisert gjett på om det var en saksbehandler eller veilder som sendte inn basert på om det er
         * en papirsøknad eller ei - dette bør ikke ha veldig stor betydning i praksis.
         */
        when (søknadInnhold.erPapirsøknad()) {
            true -> NavIdentBruker.Saksbehandler(it)
            false -> NavIdentBruker.Veileder(it)
        }
    } ?: when (søknadInnhold.erPapirsøknad()) {
        true -> NavIdentBruker.Saksbehandler("Ukjent")
        false -> NavIdentBruker.Veileder("Ukjent")
    }

    val opprettet: Tidspunkt = tidspunkt("opprettet")
    val lukket: LukketJson? = deserializeNullable(stringOrNull("lukket"))
    val oppgaveId: OppgaveId? = stringOrNull("oppgaveId")?.let { OppgaveId(it) }
    val journalpostId: JournalpostId? = stringOrNull("journalpostId")?.let { JournalpostId(it) }

    return when {
        lukket != null -> when (lukket.type) {
            LukketJson.Type.BORTFALT -> Søknad.Journalført.MedOppgave.Lukket.Bortfalt(
                sakId = sakId,
                id = id,
                opprettet = opprettet,
                søknadInnhold = søknadInnhold,
                journalpostId = journalpostId!!,
                oppgaveId = oppgaveId!!,
                lukketAv = NavIdentBruker.Saksbehandler(lukket.saksbehandler),
                lukketTidspunkt = lukket.tidspunkt,
                innsendtAv = innsendtAv,
            )
            LukketJson.Type.AVVIST -> Søknad.Journalført.MedOppgave.Lukket.Avvist(
                sakId = sakId,
                id = id,
                opprettet = opprettet,
                søknadInnhold = søknadInnhold,
                journalpostId = journalpostId!!,
                oppgaveId = oppgaveId!!,
                lukketAv = NavIdentBruker.Saksbehandler(lukket.saksbehandler),
                lukketTidspunkt = lukket.tidspunkt,
                brevvalg = lukket.toBrevvalg() as Brevvalg.SaksbehandlersValg,
                innsendtAv = innsendtAv,
            )
            LukketJson.Type.TRUKKET -> Søknad.Journalført.MedOppgave.Lukket.TrukketAvSøker(
                sakId = sakId,
                id = id,
                opprettet = opprettet,
                søknadInnhold = søknadInnhold,
                journalpostId = journalpostId!!,
                oppgaveId = oppgaveId!!,
                lukketAv = NavIdentBruker.Saksbehandler(lukket.saksbehandler),
                lukketTidspunkt = lukket.tidspunkt,
                trukketDato = lukket.trukketDato!!,
                innsendtAv = innsendtAv,
            )
        }
        journalpostId == null -> Søknad.Ny(
            sakId = sakId,
            id = id,
            opprettet = opprettet,
            søknadInnhold = søknadInnhold,
            innsendtAv = innsendtAv,
        )
        oppgaveId == null -> Søknad.Journalført.UtenOppgave(
            sakId = sakId,
            id = id,
            opprettet = opprettet,
            søknadInnhold = søknadInnhold,
            journalpostId = journalpostId,
            innsendtAv = innsendtAv,
        )
        else -> Søknad.Journalført.MedOppgave.IkkeLukket(
            sakId = sakId,
            id = id,
            opprettet = opprettet,
            søknadInnhold = søknadInnhold,
            innsendtAv = innsendtAv,
            journalpostId = journalpostId,
            oppgaveId = oppgaveId,
        )
    }
}
