package no.nav.su.se.bakover.database.behandling

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.beregning.BeregningPostgresRepoInternal
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
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.BehandlingFactory
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.hendelseslogg.Hendelseslogg
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
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
    ): Behandling {
        dataSource.withSession { session ->
            "update behandling set behandlingsinformasjon = to_json(:behandlingsinformasjon::json) where id = :id".oppdatering(
                mapOf(
                    "id" to behandlingId,
                    "behandlingsinformasjon" to objectMapper.writeValueAsString(behandlingsinformasjon)
                ),
                session
            )
        }
        return hentBehandling(behandlingId)!!
    }

    override fun oppdaterBehandlingStatus(
        behandlingId: UUID,
        status: Behandling.BehandlingsStatus
    ): Behandling {
        dataSource.withSession { session ->
            "update behandling set status = :status where id = :id".oppdatering(
                mapOf(
                    "id" to behandlingId,
                    "status" to status.name
                ),
                session
            )
        }
        return hentBehandling(behandlingId)!!
    }

    override fun leggTilUtbetaling(behandlingId: UUID, utbetalingId: UUID30): Behandling {
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
        return hentBehandling(behandlingId)!!
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

    override fun settSaksbehandler(behandlingId: UUID, saksbehandler: NavIdentBruker.Saksbehandler): Behandling {
        dataSource.withSession { session ->
            "update behandling set saksbehandler = :saksbehandler where id=:id".oppdatering(
                mapOf(
                    "id" to behandlingId,
                    "saksbehandler" to saksbehandler.navIdent
                ),
                session
            )
        }
        return hentBehandling(behandlingId)!!
    }

    override fun attester(behandlingId: UUID, attestant: NavIdentBruker.Attestant): Behandling {
        dataSource.withSession { session ->
            "update behandling set attestant = :attestant where id=:id".oppdatering(
                mapOf(
                    "id" to behandlingId,
                    "attestant" to attestant.navIdent
                ),
                session
            )
        }
        return hentBehandling(behandlingId)!!
    }

    override fun opprettSøknadsbehandling(
        nySøknadsbehandling: NySøknadsbehandling
    ) {
        dataSource.withSession { session ->
            """
            insert into behandling
                (id, sakId, søknadId, opprettet, status, behandlingsinformasjon)
            values
                (:id, :sakId, :soknadId, :opprettet, :status, to_json(:behandlingsinformasjon::json))
            """.oppdatering(
                mapOf(
                    "id" to nySøknadsbehandling.id,
                    "sakId" to nySøknadsbehandling.sakId,
                    "soknadId" to nySøknadsbehandling.søknadId,
                    "opprettet" to nySøknadsbehandling.opprettet,
                    "status" to nySøknadsbehandling.status.name,
                    "behandlingsinformasjon" to objectMapper.writeValueAsString(nySøknadsbehandling.behandlingsinformasjon)
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
        return behandlingFactory.createBehandling(
            id = behandlingId,
            behandlingsinformasjon = objectMapper.readValue(string("behandlingsinformasjon")),
            opprettet = tidspunkt("opprettet"),
            søknad = SøknadRepoInternal.hentSøknadInternal(uuid("søknadId"), session)!!,
            beregning = BeregningPostgresRepoInternal.hentBeregningForBehandling(behandlingId, session),
            simulering = stringOrNull("simulering")?.let { objectMapper.readValue(it, Simulering::class.java) },
            status = Behandling.BehandlingsStatus.valueOf(string("status")),
            attestant = stringOrNull("attestant")?.let { NavIdentBruker.Attestant(it) },
            saksbehandler = stringOrNull("saksbehandler")?.let { NavIdentBruker.Saksbehandler(it) },
            sakId = uuid("sakId"),
            hendelseslogg = HendelsesloggRepoInternal.hentHendelseslogg(behandlingId.toString(), session)
                ?: Hendelseslogg(
                    behandlingId.toString()
                ),
            fnr = Fnr(string("fnr"))
        )
    }
}
