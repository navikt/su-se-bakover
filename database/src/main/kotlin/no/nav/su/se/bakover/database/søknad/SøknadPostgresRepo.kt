package no.nav.su.se.bakover.database.søknad

import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.database.søknad.LukketJson.Companion.toLukketJson
import no.nav.su.se.bakover.database.søknad.SøknadRepoInternal.hentSøknadInternal
import no.nav.su.se.bakover.domain.søknad.Søknad
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

    override fun opprettSøknad(søknad: Søknad.Ny) {
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
                        "ident" to søknad.innsendtAv.navIdent,
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
                        "lukket" to søknad.toLukketJson(),
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
                """
                    select s.*,
                           d.id             as dokumentid,
                           dd.brevbestillingid,
                           dd.journalpostid as journalpostidDokument
                    from søknad s
                             left join dokument d on s.id = d.søknadid
                             left join dokument_distribusjon dd on d.id = dd.dokumentid
                    where s.journalpostId is null
                    and d.duplikatAv is null
                """.trimIndent().hentListe(
                    session = session,
                ) {
                    it.toSøknad()
                }.filterIsInstance(Søknad.Ny::class.java).also {
                    check(it.distinctBy { it.id }.size == it.size)
                }
            }
        }
    }

    override fun hentSøknaderMedJournalpostMenUtenOppgave(): List<Søknad.Journalført.UtenOppgave> {
        return dbMetrics.timeQuery("hentSøknaderMedJournalpostMenUtenOppgave") {
            sessionFactory.withSession { session ->
                """
                    select s.*,
                           d.id             as dokumentid,
                           dd.brevbestillingid,
                           dd.journalpostid as journalpostidDokument
                    from søknad s
                             left join dokument d on s.id = d.søknadid
                             left join dokument_distribusjon dd on d.id = dd.dokumentid
                    where s.journalpostId is not null 
                    and oppgaveId is null
                    and d.duplikatAv is null
                """.trimIndent().hentListe(
                    session = session,
                ) {
                    it.toSøknad()
                }.filterIsInstance(Søknad.Journalført.UtenOppgave::class.java).also {
                    check(it.distinctBy { it.id }.size == it.size)
                }
            }
        }
    }

    override fun defaultSessionContext(): SessionContext {
        return sessionFactory.newSessionContext()
    }
}
