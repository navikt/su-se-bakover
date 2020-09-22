package no.nav.su.se.bakover.database.behandlinger.stopp

import kotliquery.Row
import kotliquery.sessionOf
import kotliquery.using
import no.nav.su.se.bakover.database.ObjectRepo
import no.nav.su.se.bakover.database.behandlinger.stopp.StoppbehandlingJdbcRepo.Status.Companion.toStatus
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.database.uuid30
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.behandlinger.stopp.Stoppbehandling
import no.nav.su.se.bakover.domain.behandlinger.stopp.StoppbehandlingRepo
import java.util.UUID
import javax.sql.DataSource

class StoppbehandlingJdbcRepo(
    private val dataSource: DataSource,
    private val objectRepo: ObjectRepo
) : StoppbehandlingRepo {

    override fun opprettStoppbehandling(nyBehandling: Stoppbehandling.Simulert) =
        using(sessionOf(dataSource)) { session ->
            "insert into stoppbehandling (id, opprettet, sakId, status, utbetaling, stoppÅrsak, saksbehandler) values (:id, :opprettet, :sakId, :status, :utbetaling, :stoppArsak, :saksbehandler)".oppdatering(
                params = mapOf(
                    "id" to nyBehandling.id,
                    "opprettet" to nyBehandling.opprettet,
                    "sakId" to nyBehandling.sakId,
                    "status" to nyBehandling.toStatus().value,
                    "utbetaling" to nyBehandling.utbetaling.id.toString(),
                    "stoppArsak" to nyBehandling.stoppÅrsak,
                    "saksbehandler" to nyBehandling.saksbehandler.id,
                ),
                session = session
            ).let {
                nyBehandling
            }
        }

    override fun hentPågåendeStoppbehandling(sakId: UUID): Stoppbehandling? {
        return using(sessionOf(dataSource)) { session ->
            val behandlinger =
                "select * from stoppbehandling where sakId=:sakId and status != '${Stoppbehandling.Iverksatt.STATUS}'".hentListe(
                    params = mapOf(
                        "sakId" to sakId
                    ),
                    session = session
                ) {
                    it.toStoppbehandling()
                }
            when (behandlinger.size) {
                0 -> null
                1 -> behandlinger[0]
                // TODO jah: Alert
                else -> throw IllegalStateException("Databasen inneholder mer enn en pågående stoppbehandling.")
            }
        }
    }

    private fun Row.toStoppbehandling() = this.string("status")
        .let { Status.fromString(it) }
        .let { status ->
            val id = uuid("id")
            val opprettet = instant("opprettet")
            val sakId = uuid("sakId")
            val utbetaling = objectRepo.hentUtbetaling(uuid30("utbetaling"))!!
            val stoppÅrsak = string("stoppÅrsak")
            val saksbehandler = Saksbehandler(string("saksbehandler"))

            when (status) {
                Status.SIMULERT -> Stoppbehandling.Simulert(
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    utbetaling = utbetaling,
                    stoppÅrsak = stoppÅrsak,
                    saksbehandler = saksbehandler,
                )
                Status.TIL_ATTESTERING -> Stoppbehandling.TilAttestering(
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    utbetaling = utbetaling,
                    stoppÅrsak = stoppÅrsak,
                    saksbehandler = saksbehandler,
                )
                Status.IVERKSATT -> Stoppbehandling.Iverksatt(
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    utbetaling = utbetaling,
                    stoppÅrsak = stoppÅrsak,
                    saksbehandler = saksbehandler,
                    attestant = Attestant(string("attestant"))
                )
            }
        }

    private enum class Status(val value: String) {
        SIMULERT("SIMULERT"),
        TIL_ATTESTERING("TIL_ATTESTERING"),
        IVERKSATT("IVERKSATT");

        override fun toString() = value

        companion object {
            private val legalValues = values().joinToString { it.value }

            fun fromString(value: String): Status = values().firstOrNull {
                it.value == value
            } ?: throw IllegalStateException("Ukjent Stoppbehandlingsstatus $value, gyldige verdier: $legalValues")

            fun Stoppbehandling.toStatus(): Status = when (this) {
                is Stoppbehandling.Simulert -> SIMULERT
                is Stoppbehandling.TilAttestering -> TIL_ATTESTERING
                is Stoppbehandling.Iverksatt -> IVERKSATT
            }
        }
    }
}
