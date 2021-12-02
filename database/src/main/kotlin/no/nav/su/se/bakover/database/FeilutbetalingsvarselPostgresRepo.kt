package no.nav.su.se.bakover.database

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.oppdrag.Feilutbetalingsvarsel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import java.util.UUID
import javax.sql.DataSource

interface FeilutbetalingsvarselRepo {
    fun hent(sakId: UUID): List<Feilutbetalingsvarsel>
}

internal class FeilutbetalingsvarselPostgresRepo(
    private val dataSource: DataSource,
) : FeilutbetalingsvarselRepo {

    fun lagre(sakId: UUID, behandlingId: UUID, feilutbetalingsvarsel: Feilutbetalingsvarsel) {
        dataSource.withTransaction { tx ->
            slettForBehandling(behandlingId, tx)
            when (feilutbetalingsvarsel) {
                is Feilutbetalingsvarsel.Ingen -> {
                    insert(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(),
                        sakId = sakId,
                        behandlingId = behandlingId,
                        simulering = null,
                        feilutbetalingslinje = null,
                        type = Type.INGEN,
                        tx = tx,
                    )
                }
                is Feilutbetalingsvarsel.KanAvkortes -> {
                    insert(
                        id = feilutbetalingsvarsel.id,
                        opprettet = feilutbetalingsvarsel.opprettet,
                        sakId = sakId,
                        behandlingId = behandlingId,
                        simulering = feilutbetalingsvarsel.simulering,
                        feilutbetalingslinje = feilutbetalingsvarsel.feilutbetalingslinje,
                        type = Type.KAN_AVKORTES,
                        tx = tx,
                    )
                }
                is Feilutbetalingsvarsel.MåTilbakekreves -> {
                    insert(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(),
                        sakId = sakId,
                        behandlingId = behandlingId,
                        simulering = null,
                        feilutbetalingslinje = null,
                        type = Type.MÅ_TILBAKEKREVES,
                        tx = tx,
                    )
                }
            }
        }
    }

    private fun insert(
        id: UUID,
        opprettet: Tidspunkt,
        sakId: UUID,
        behandlingId: UUID,
        simulering: Simulering?,
        feilutbetalingslinje: Feilutbetalingsvarsel.Feilutbetalingslinje?,
        type: Type,
        tx: TransactionalSession,
    ) {
        """insert into feilutbetalingsvarsel (
            id, 
            opprettet, 
            sakId, 
            behandlingId,
            simulering, 
            feilutbetalingslinje,
            type
            ) values (
                :id, 
                :opprettet, 
                :sakId, 
                :behandlingId,
                to_jsonb(:simulering::json), 
                to_jsonb(:feilutbetalingslinje::json),
                :type
             )""".trimMargin()
            .insert(
                mapOf(
                    "id" to id,
                    "opprettet" to opprettet,
                    "sakId" to sakId,
                    "behandlingId" to behandlingId,
                    "simulering" to simulering?.let { objectMapper.writeValueAsString(it) },
                    "feilutbetalingslinje" to feilutbetalingslinje?.let { objectMapper.writeValueAsString(it) },
                    "type" to type.toString(),
                ),
                tx,
            )
    }

    override fun hent(sakId: UUID): List<Feilutbetalingsvarsel> {
        return dataSource.withSession { session ->
            """select * from feilutbetalingsvarsel where sakid = :sakid""".hentListe(
                mapOf(
                    "sakid" to sakId,
                ),
                session,
            ) {
                it.toFeilutbetalingsvarsel()
            }
        }
    }

    fun slettForBehandling(behandlingId: UUID, tx: TransactionalSession) {
        """delete from feilutbetalingsvarsel where behandlingId = :behandlingId""".oppdatering(
            mapOf(
                "behandlingId" to behandlingId,
            ),
            tx,
        )
    }

    fun hentForBehandling(behandlingId: UUID): Feilutbetalingsvarsel? {
        return dataSource.withSession { session ->
            """select * from feilutbetalingsvarsel where behandlingId = :behandlingId""".hent(
                mapOf(
                    "behandlingId" to behandlingId,
                ),
                session,
            ) {
                it.toFeilutbetalingsvarsel()
            }
        }
    }

    private fun Row.toFeilutbetalingsvarsel(): Feilutbetalingsvarsel {
        return when (Type.valueOf(string("type"))) {
            Type.KAN_AVKORTES -> {
                Feilutbetalingsvarsel.KanAvkortes(
                    id = uuid("id"),
                    opprettet = tidspunkt("opprettet"),
                    simulering = string("simulering").let { objectMapper.readValue(it) },
                    feilutbetalingslinje = string("feilutbetalingslinje").let { objectMapper.readValue(it) },
                )
            }
            Type.MÅ_TILBAKEKREVES -> {
                Feilutbetalingsvarsel.MåTilbakekreves
            }
            Type.INGEN -> {
                Feilutbetalingsvarsel.Ingen
            }
        }
    }

    private enum class Type {
        KAN_AVKORTES,
        MÅ_TILBAKEKREVES,
        INGEN
    }
}
