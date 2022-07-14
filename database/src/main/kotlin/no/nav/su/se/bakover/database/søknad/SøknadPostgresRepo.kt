package no.nav.su.se.bakover.database.søknad

import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.database.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.database.PostgresSessionFactory
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.søknad.LukketJson.Companion.toLukketJson
import no.nav.su.se.bakover.database.søknad.SøknadRepoInternal.hentSøknadInternal
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.søknad.SøknadRepo
import java.util.UUID

internal class SøknadPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : SøknadRepo {

    override fun hentSøknad(søknadId: UUID): Søknad? {
        return dbMetrics.timeQuery("hentSøknad") {
            sessionFactory.withSession { hentSøknadInternal(søknadId, it) }
        }
    }

    override fun opprettSøknad(søknad: Søknad.Ny, identBruker: NavIdentBruker) {
        dbMetrics.timeQuery("opprettSøknad") {
            sessionFactory.withSession { session ->
                """
                    insert into søknad 
                        (id, 
                        sakId, 
                        søknadInnhold, 
                        opprettet, 
                        ident) 
                    values 
                        (:id, 
                        :sakId, 
                        to_json(:soknad::json), 
                        :opprettet, 
                        :ident)
                """.trimIndent().insert(
                    mapOf(
                        "id" to søknad.id,
                        "sakId" to søknad.sakId,
                        "soknad" to serialize(søknad.søknadInnhold),
                        "opprettet" to søknad.opprettet,
                        "ident" to identBruker.navIdent,
                    ),
                    session,
                )
            }
        }
    }

    override fun lukkSøknad(søknad: Søknad.Journalført.MedOppgave.Lukket, sessionContext: SessionContext) {
        dbMetrics.timeQuery("lukkSøknad") {
            sessionContext.withSession { session ->
                "update søknad set lukket=to_json(:lukket::json) where id=:id".oppdatering(
                    mapOf(
                        "id" to søknad.id,
                        "lukket" to serialize(søknad.toLukketJson()),
                    ),
                    session,
                )
            }
        }
    }

    override fun oppdaterjournalpostId(søknad: Søknad.Journalført.UtenOppgave) {
        dbMetrics.timeQuery("oppdaterjournalpostId") {
            sessionFactory.withSession { session ->
                "update søknad set journalpostId=:journalpostId where id=:id".oppdatering(
                    mapOf(
                        "id" to søknad.id,
                        "journalpostId" to søknad.journalpostId.toString(),
                    ),
                    session,
                )
            }
        }
    }

    override fun oppdaterOppgaveId(søknad: Søknad.Journalført.MedOppgave) {
        dbMetrics.timeQuery("oppdaterOppgaveId") {
            sessionFactory.withSession { session ->
                "update søknad set oppgaveId=:oppgaveId where id=:id".oppdatering(
                    mapOf(
                        "id" to søknad.id,
                        "oppgaveId" to søknad.oppgaveId.toString(),
                    ),
                    session,
                )
            }
        }
    }

    override fun hentSøknaderUtenJournalpost(): List<Søknad.Ny> {
        return dbMetrics.timeQuery("hentSøknaderUtenJournalpost") {
            sessionFactory.withSession { session ->
                "select * from søknad where journalpostId is null".hentListe(
                    session = session,
                ) {
                    it.toSøknad()
                }.filterIsInstance(Søknad.Ny::class.java)
            }
        }
    }

    override fun hentSøknaderMedJournalpostMenUtenOppgave(): List<Søknad.Journalført.UtenOppgave> {
        return dbMetrics.timeQuery("hentSøknaderMedJournalpostMenUtenOppgave") {
            sessionFactory.withSession { session ->
                "select * from søknad where journalpostId is not null and oppgaveId is null".hentListe(
                    session = session,
                ) {
                    it.toSøknad()
                }.filterIsInstance(Søknad.Journalført.UtenOppgave::class.java)
            }
        }
    }

    override fun defaultSessionContext(): SessionContext {
        return sessionFactory.newSessionContext()
    }
}
