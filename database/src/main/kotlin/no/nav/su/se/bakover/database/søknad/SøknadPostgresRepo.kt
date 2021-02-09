package no.nav.su.se.bakover.database.søknad

import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.søknad.LukketJson.Companion.toLukketJson
import no.nav.su.se.bakover.database.søknad.SøknadRepoInternal.hentSøknadInternal
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.Søknad
import java.util.UUID
import javax.sql.DataSource

internal class SøknadPostgresRepo(
    private val dataSource: DataSource
) : SøknadRepo {
    override fun hentSøknad(søknadId: UUID): Søknad? = dataSource.withSession { hentSøknadInternal(søknadId, it) }

    override fun opprettSøknad(søknad: Søknad.Ny) {
        dataSource.withSession { session ->
            "insert into søknad (id, sakId, søknadInnhold, opprettet) values (:id, :sakId, to_json(:soknad::json), :opprettet)".oppdatering(
                mapOf(
                    "id" to søknad.id,
                    "sakId" to søknad.sakId,
                    "soknad" to objectMapper.writeValueAsString(søknad.søknadInnhold),
                    "opprettet" to søknad.opprettet,
                ),
                session
            )
        }
    }

    override fun oppdaterSøknad(søknad: Søknad.Lukket) {
        dataSource.withSession { session ->
            "update søknad set lukket=to_json(:lukket::json) where id=:id".oppdatering(
                mapOf(
                    "id" to søknad.id,
                    "lukket" to objectMapper.writeValueAsString(søknad.toLukketJson())
                ),
                session
            )
        }
    }

    override fun harSøknadPåbegyntBehandling(søknadId: UUID): Boolean {
        return dataSource.withSession { session ->
            "select * from behandling where søknadId=:soknadId".hentListe(
                mapOf("soknadId" to søknadId), session
            ) { it.stringOrNull("søknadId") }
        }.isNotEmpty()
    }

    override fun oppdaterjournalpostId(søknad: Søknad.Journalført.UtenOppgave) {
        dataSource.withSession { session ->
            "update søknad set journalpostId=:journalpostId where id=:id".oppdatering(
                mapOf(
                    "id" to søknad.id,
                    "journalpostId" to søknad.journalpostId.toString()
                ),
                session
            )
        }
    }

    override fun oppdaterOppgaveId(søknad: Søknad.Journalført.MedOppgave) {
        dataSource.withSession { session ->
            "update søknad set oppgaveId=:oppgaveId where id=:id".oppdatering(
                mapOf(
                    "id" to søknad.id,
                    "oppgaveId" to søknad.oppgaveId.toString()
                ),
                session
            )
        }
    }

    override fun hentSøknaderUtenJournalpost(): List<Søknad.Ny> {
        return dataSource.withSession { session ->
            "select * from søknad where journalpostId is null".hentListe(
                session = session
            ) {
                it.toSøknad()
            }.filterIsInstance(Søknad.Ny::class.java)
        }
    }

    override fun hentSøknaderMedJournalpostMenUtenOppgave(): List<Søknad.Journalført.UtenOppgave> {
        return dataSource.withSession { session ->
            "select * from søknad where journalpostId is not null and oppgaveId is null".hentListe(
                session = session
            ) {
                it.toSøknad()
            }.filterIsInstance(Søknad.Journalført.UtenOppgave::class.java)
        }
    }
}
