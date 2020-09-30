package no.nav.su.se.bakover.database.behandling

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.beregning.BeregningRepoInternal
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggRepoInternal.hentHendelseslogg
import no.nav.su.se.bakover.database.søknad.SøknadRepoInternal
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.utbetaling.UtbetalingInternalRepo.hentUtbetalingInternal
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.hendelseslogg.Hendelseslogg

internal fun Row.toBehandling(session: Session): Behandling {
    val behandlingId = uuid("id")
    return Behandling(
        id = behandlingId,
        behandlingsinformasjon = objectMapper.readValue(string("behandlingsinformasjon")),
        opprettet = tidspunkt("opprettet"),
        søknad = SøknadRepoInternal.hentSøknadInternal(uuid("søknadId"), session)!!,
        beregning = BeregningRepoInternal.hentBeregningForBehandling(behandlingId, session),
        utbetaling = stringOrNull("utbetalingId")?.let {
            hentUtbetalingInternal(
                utbetalingId = UUID30.fromString(it),
                session = session
            )
        },
        status = Behandling.BehandlingsStatus.valueOf(string("status")),
        attestant = stringOrNull("attestant")?.let { Attestant(it) },
        saksbehandler = stringOrNull("saksbehandler")?.let { Saksbehandler(it) },
        sakId = uuid("sakId"),
        hendelseslogg = hentHendelseslogg(behandlingId.toString(), session) ?: Hendelseslogg(behandlingId.toString())
    )
}
