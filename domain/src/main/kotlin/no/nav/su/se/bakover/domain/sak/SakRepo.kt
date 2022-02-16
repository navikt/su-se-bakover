package no.nav.su.se.bakover.domain.sak

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.domain.person.Fnr
import java.util.UUID

interface SakRepo {
    fun hentSak(sakId: UUID): Sak?
    fun hentSak(fnr: Fnr): Sak?
    fun hentSak(saksnummer: Saksnummer): Sak?
    fun hentSakIdOgNummerForIdenter(personidenter: NonEmptyList<String>): SakIdOgNummer?
    fun opprettSak(sak: NySak)
    fun hentÅpneBehandlinger(): List<Behandlingsoversikt>
    fun hentFerdigeBehandlinger(): List<Behandlingsoversikt>
}
