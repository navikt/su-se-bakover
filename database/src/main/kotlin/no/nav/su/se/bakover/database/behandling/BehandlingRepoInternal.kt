package no.nav.su.se.bakover.database.behandling

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.beregning.BeregningRepoInternal
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggRepoInternal
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.søknad.SøknadRepoInternal
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.hendelseslogg.Hendelseslogg
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import java.util.UUID

internal object BehandlingRepoInternal {
    fun hentBehandling(behandlingId: UUID, session: Session): Behandling? =
        "select b.*, s.fnr from behandling b left outer join sak s on s.id = b.sakId where b.id=:id"
            .hent(mapOf("id" to behandlingId), session) { row ->
                row.toBehandling(session)
            }

    fun hentBehandlingerForSak(sakId: UUID, session: Session) = "select b.*, s.fnr from behandling b left outer join sak s on s.id = b.sakId where b.sakId=:sakId"
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
        simulering = stringOrNull("simulering")?.let { objectMapper.readValue(it, Simulering::class.java) },
        status = Behandling.BehandlingsStatus.valueOf(string("status")),
        attestant = stringOrNull("attestant")?.let { NavIdentBruker.Attestant(it) },
        saksbehandler = stringOrNull("saksbehandler")?.let { NavIdentBruker.Saksbehandler(it) },
        sakId = uuid("sakId"),
        hendelseslogg = HendelsesloggRepoInternal.hentHendelseslogg(behandlingId.toString(), session) ?: Hendelseslogg(
            behandlingId.toString()
        ),
        fnr = Fnr(string("fnr"))
    )
}
