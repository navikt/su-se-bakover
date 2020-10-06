package no.nav.su.se.bakover.database.behandling

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.behandling.BehandlingRepoInternal.hentBehandling
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.AvsluttetBegrunnelse
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import java.util.UUID
import javax.sql.DataSource

internal class BehandlingPostgresRepo(
    private val dataSource: DataSource
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

    override fun settSaksbehandler(behandlingId: UUID, saksbehandler: Saksbehandler): Behandling {
        dataSource.withSession { session ->
            "update behandling set saksbehandler = :saksbehandler where id=:id".oppdatering(
                mapOf(
                    "id" to behandlingId,
                    "saksbehandler" to saksbehandler.id
                ),
                session
            )
        }
        return hentBehandling(behandlingId)!!
    }

    override fun attester(behandlingId: UUID, attestant: Attestant): Behandling {
        dataSource.withSession { session ->
            "update behandling set attestant = :attestant where id=:id".oppdatering(
                mapOf(
                    "id" to behandlingId,
                    "attestant" to attestant.id
                ),
                session
            )
        }
        return hentBehandling(behandlingId)!!
    }

    override fun opprettSøknadsbehandling(
        sakId: UUID,
        behandling: Behandling
    ): Behandling {
        dataSource.withSession { session ->
            """
            insert into behandling
                (id, sakId, søknadId, opprettet, status, behandlingsinformasjon)
            values
                (:id, :sakId, :soknadId, :opprettet, :status, to_json(:behandlingsinformasjon::json))
            """.oppdatering(
                mapOf(
                    "id" to behandling.id,
                    "sakId" to sakId,
                    "soknadId" to behandling.søknad.id,
                    "opprettet" to behandling.opprettet,
                    "status" to behandling.status().name,
                    "behandlingsinformasjon" to objectMapper.writeValueAsString(behandling.behandlingsinformasjon())
                ),
                session
            )
        }
        return hentBehandling(behandling.id)!!
    }

    override fun slettBehandlingForBehandling(søknadId: UUID, avsluttetBegrunnelse: AvsluttetBegrunnelse) {
        dataSource.withSession { session ->
            "update behandling set avsluttetBegrunnelse = :avsluttetBegrunnelse where søknadid=:id".oppdatering(
                mapOf(
                    "id" to søknadId,
                    "avsluttetBegrunnelse" to avsluttetBegrunnelse.toString()
                ),
                session
            )
        }
    }
}
