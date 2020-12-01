package no.nav.su.se.bakover.database.behandling

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.beregning.Beregnet
import no.nav.su.se.bakover.database.beregning.toSnapshot
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggRepoInternal
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.søknad.SøknadRepoInternal
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.BehandlingFactory
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.hendelseslogg.Hendelseslogg
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.util.UUID
import javax.sql.DataSource

internal class BehandlingPostgresRepo(
    private val dataSource: DataSource,
    private val behandlingFactory: BehandlingFactory,
) : BehandlingRepo {
    override fun hentBehandling(behandlingId: UUID): Behandling? =
        dataSource.withSession { hentBehandling(behandlingId, it) }

    override fun oppdaterBehandlingsinformasjon(
        behandlingId: UUID,
        behandlingsinformasjon: Behandlingsinformasjon
    ) {
        dataSource.withSession { session ->
            "update behandling set behandlingsinformasjon = to_json(:behandlingsinformasjon::json) where id = :id".oppdatering(
                mapOf(
                    "id" to behandlingId,
                    "behandlingsinformasjon" to objectMapper.writeValueAsString(behandlingsinformasjon)
                ),
                session
            )
        }
    }

    override fun oppdaterBehandlingStatus(
        behandlingId: UUID,
        status: Behandling.BehandlingsStatus
    ) {
        dataSource.withSession { session ->
            "update behandling set status = :status where id = :id".oppdatering(
                mapOf(
                    "id" to behandlingId,
                    "status" to status.name
                ),
                session
            )
        }
    }

    override fun leggTilUtbetaling(behandlingId: UUID, utbetalingId: UUID30) {
        dataSource.withSession { session ->
            """
            update behandling set utbetalingId=:utbetalingId where id=:id
        """.oppdatering(
                mapOf(
                    "id" to behandlingId,
                    "utbetalingId" to utbetalingId
                ),
                session
            )
        }
    }

    override fun leggTilSimulering(behandlingId: UUID, simulering: Simulering) {
        dataSource.withSession { session ->
            """
            update behandling set simulering=to_json(:simulering::json) where id=:id
        """.oppdatering(
                mapOf(
                    "id" to behandlingId,
                    "simulering" to objectMapper.writeValueAsString(simulering)
                ),
                session
            )
        }
    }

    override fun leggTilBeregning(behandlingId: UUID, beregning: Beregning) {
        dataSource.withSession { session ->
            """
            update behandling set beregning=to_json(:beregning::json) where id=:id
        """.oppdatering(
                mapOf(
                    "id" to behandlingId,
                    "beregning" to objectMapper.writeValueAsString(beregning.toSnapshot())
                ),
                session
            )
        }
    }

    override fun slettBeregning(behandlingId: UUID) {
        dataSource.withSession {
            """
                update behandling set beregning=null where id=:id
            """.oppdatering(
                mapOf(
                    "id" to behandlingId
                ),
                it
            )
        }
    }

    override fun settSaksbehandler(behandlingId: UUID, saksbehandler: NavIdentBruker.Saksbehandler) {
        dataSource.withSession { session ->
            "update behandling set saksbehandler = :saksbehandler where id=:id".oppdatering(
                mapOf(
                    "id" to behandlingId,
                    "saksbehandler" to saksbehandler.navIdent
                ),
                session
            )
        }
    }

    override fun oppdaterAttestering(behandlingId: UUID, attestering: Attestering) {
        dataSource.withSession { session ->
            "update behandling set attestant = :attestant where id=:id".oppdatering(
                mapOf(
                    "id" to behandlingId,
                    "attestant" to attestering.attestant.navIdent
                ),
                session
            )
        }
    }

    override fun opprettSøknadsbehandling(
        nySøknadsbehandling: NySøknadsbehandling
    ) {
        dataSource.withSession { session ->
            """
            insert into behandling
                (id, sakId, søknadId, opprettet, status, behandlingsinformasjon, oppgaveId)
            values
                (:id, :sakId, :soknadId, :opprettet, :status, to_json(:behandlingsinformasjon::json), :oppgaveId)
            """.oppdatering(
                mapOf(
                    "id" to nySøknadsbehandling.id,
                    "sakId" to nySøknadsbehandling.sakId,
                    "soknadId" to nySøknadsbehandling.søknadId,
                    "opprettet" to nySøknadsbehandling.opprettet,
                    "status" to nySøknadsbehandling.status.name,
                    "behandlingsinformasjon" to objectMapper.writeValueAsString(nySøknadsbehandling.behandlingsinformasjon),
                    "oppgaveId" to nySøknadsbehandling.oppgaveId.toString()
                ),
                session
            )
        }
    }

    override fun oppdaterOppgaveId(behandlingId: UUID, oppgaveId: OppgaveId) {
        dataSource.withSession { session ->
            "update behandling set oppgaveId = :oppgaveId where id=:id".oppdatering(
                mapOf(
                    "id" to behandlingId,
                    "oppgaveId" to oppgaveId.toString()
                ),
                session
            )
        }
    }

    override fun oppdaterIverksattJournalpostId(behandlingId: UUID, journalpostId: JournalpostId) {
        dataSource.withSession { session ->
            "update behandling set iverksattJournalpostId = :journalpostId where id=:id".oppdatering(
                mapOf(
                    "id" to behandlingId,
                    "journalpostId" to journalpostId.toString()
                ),
                session
            )
        }
    }

    override fun oppdaterIverksattBrevbestillingId(behandlingId: UUID, bestillingId: BrevbestillingId) {
        dataSource.withSession { session ->
            "update behandling set iverksattBrevbestillingId = :bestillingId where id=:id".oppdatering(
                mapOf(
                    "id" to behandlingId,
                    "bestillingId" to bestillingId.toString()
                ),
                session
            )
        }
    }

    internal fun hentBehandling(behandlingId: UUID, session: Session): Behandling? =
        "select b.*, s.fnr from behandling b inner join sak s on s.id = b.sakId where b.id=:id"
            .hent(mapOf("id" to behandlingId), session) { row ->
                row.toBehandling(session)
            }

    internal fun hentBehandlingerForSak(sakId: UUID, session: Session): List<Behandling> =
        "select b.*, s.fnr from behandling b inner join sak s on s.id = b.sakId where b.sakId=:sakId"
            .hentListe(mapOf("sakId" to sakId), session) {
                it.toBehandling(session)
            }

    private fun Row.toBehandling(session: Session): Behandling {
        val behandlingId = uuid("id")
        val søknad = SøknadRepoInternal.hentSøknadInternal(uuid("søknadId"), session)!!
        if (søknad !is Søknad.Journalført.MedOppgave) {
            throw IllegalStateException("Kunne ikke hente behandling med søknad som ikke er journalført med oppgave.")
        }
        return behandlingFactory.createBehandling(
            id = behandlingId,
            behandlingsinformasjon = objectMapper.readValue(string("behandlingsinformasjon")),
            opprettet = tidspunkt("opprettet"),
            søknad = søknad,
            beregning = stringOrNull("beregning")?.let { objectMapper.readValue<Beregnet>(it) },
            simulering = stringOrNull("simulering")?.let { objectMapper.readValue<Simulering>(it) },
            status = Behandling.BehandlingsStatus.valueOf(string("status")),
            attestering = stringOrNull("attestering")?.let { objectMapper.readValue<Attestering>(it) },
            saksbehandler = stringOrNull("saksbehandler")?.let { NavIdentBruker.Saksbehandler(it) },
            sakId = uuid("sakId"),
            hendelseslogg = HendelsesloggRepoInternal.hentHendelseslogg(behandlingId.toString(), session)
                ?: Hendelseslogg(
                    behandlingId.toString()
                ),
            fnr = Fnr(string("fnr")),
            oppgaveId = OppgaveId(string("oppgaveId")),
            iverksattJournalpostId = stringOrNull("iverksattJournalpostId")?.let { JournalpostId(it) },
            iverksattBrevbestillingId = stringOrNull("iverksattBrevbestillingId")?.let { BrevbestillingId(it) },
        )
    }
}
