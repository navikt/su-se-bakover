package no.nav.su.se.bakover.database.behandling

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.beregning.BeregningRepoInternal
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggRepoInternal
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.søknad.SøknadRepoInternal
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.utbetaling.UtbetalingInternalRepo
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.hendelseslogg.Hendelseslogg
import java.util.UUID

internal object BehandlingRepoInternal {
    fun hentBehandling(behandlingId: UUID, session: Session): Behandling? =
        "select * from behandling where id=:id"
            .hent(mapOf("id" to behandlingId), session) { row ->
                row.toBehandling(session)
            }

    fun hentBehandlingerForSak(sakId: UUID, session: Session) = "select * from behandling where sakId=:sakId"
        .hentListe(mapOf("sakId" to sakId), session) {
            it.toBehandling(session)
        }.toMutableList()
}

internal fun Row.toBehandling(session: Session): Behandling {
    val behandlingId = uuid("id")
    return Behandling(
        id = behandlingId,
        behandlingsinformasjon = objectMapper.readValue(string("behandlingsinformasjon")),
        opprettet = tidspunkt("opprettet"),
        søknad = SøknadRepoInternal.hentSøknadInternal(uuid("søknadId"), session)!!,
        beregning = BeregningRepoInternal.hentBeregningForBehandling(behandlingId, session),
        utbetaling = stringOrNull("utbetalingId")?.let {
            UtbetalingInternalRepo.hentUtbetalingInternal(
                utbetalingId = UUID30.fromString(it),
                session = session
            )
        },
        status = Behandling.BehandlingsStatus.valueOf(string("status")),
        attestant = stringOrNull("attestant")?.let { Attestant(it) },
        saksbehandler = stringOrNull("saksbehandler")?.let { Saksbehandler(it) },
        sakId = uuid("sakId"),
        hendelseslogg = HendelsesloggRepoInternal.hentHendelseslogg(behandlingId.toString(), session) ?: Hendelseslogg(behandlingId.toString())
    )
}
