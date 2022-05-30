package no.nav.su.se.bakover.domain.sak

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NySak
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import java.util.UUID

interface SakRepo {
    fun hentSak(sakId: UUID): Sak?
    fun hentSak(fnr: Fnr): Sak?
    fun hentSak(saksnummer: Saksnummer): Sak?
    fun hentSakInfoForIdenter(personidenter: NonEmptyList<String>): SakInfo?
    fun opprettSak(sak: NySak)
    fun hentÅpneBehandlinger(): List<Behandlingsoversikt>
    fun hentFerdigeBehandlinger(): List<Behandlingsoversikt>
    fun hentSakIdSaksnummerOgFnrForAlleSaker(): List<SakInfo>
    fun hentSakerSomVenterPåForhåndsvarsling(): List<Saksnummer>
}
