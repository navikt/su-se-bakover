package no.nav.su.se.bakover.database.sak

import kotliquery.Row
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.behandling.BehandlingRepoInternal.hentBehandlingerForSak
import no.nav.su.se.bakover.database.oppdrag.OppdragRepoInternal.hentOppdragForSak
import no.nav.su.se.bakover.database.søknad.SøknadRepoInternal.hentSøknaderInternal
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import java.util.UUID

internal fun Row.toSak(session: Session): Sak {
    val sakId = UUID.fromString(string("id"))
    return Sak(
        id = sakId,
        fnr = Fnr(string("fnr")),
        opprettet = tidspunkt("opprettet"),
        søknader = hentSøknaderInternal(sakId, session),
        behandlinger = hentBehandlingerForSak(sakId, session),
        oppdrag = hentOppdragForSak(sakId, session)!!
    )
}
