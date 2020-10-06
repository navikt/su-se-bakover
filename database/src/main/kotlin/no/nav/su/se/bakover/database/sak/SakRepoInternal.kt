package no.nav.su.se.bakover.database.sak

import kotliquery.Row
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.behandling.BehandlingRepoInternal
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.oppdrag.OppdragRepoInternal
import no.nav.su.se.bakover.database.søknad.SøknadRepoInternal
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import java.util.UUID

internal object SakRepoInternal {
    fun hentSakInternal(fnr: Fnr, session: Session): Sak? = "select * from sak where fnr=:fnr"
        .hent(mapOf("fnr" to fnr.toString()), session) { it.toSak(session) }

    fun hentSakInternal(sakId: UUID, session: Session): Sak? = "select * from sak where id=:sakId"
        .hent(mapOf("sakId" to sakId), session) { it.toSak(session) }
}

internal fun Row.toSak(session: Session): Sak {
    val sakId = UUID.fromString(string("id"))
    return Sak(
        id = sakId,
        fnr = Fnr(string("fnr")),
        opprettet = tidspunkt("opprettet"),
        søknader = SøknadRepoInternal.hentSøknaderInternal(sakId, session),
        behandlinger = BehandlingRepoInternal.hentBehandlingerForSak(sakId, session),
        oppdrag = OppdragRepoInternal.hentOppdragForSak(sakId, session)!!
    )
}
