package no.nav.su.se.bakover.domain.sak

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.domain.Sak
import java.util.UUID

interface SakRepo {
    fun hentSak(sakId: UUID): Sak?
    fun hentSak(sakId: UUID, sessionContext: SessionContext): Sak?
    fun hentSak(fnr: Fnr, type: Sakstype): Sak?
    fun hentSak(saksnummer: Saksnummer): Sak?
    fun hentSakInfoForIdenter(personidenter: NonEmptyList<String>): SakInfo?
    fun hentSakInfo(sakId: UUID): SakInfo?
    fun opprettSak(sak: NySak)
    fun hentÅpneBehandlinger(): List<Behandlingsoversikt>
    fun hentFerdigeBehandlinger(): List<Behandlingsoversikt>
    fun hentSakIdSaksnummerOgFnrForAlleSaker(): List<SakInfo>
    fun hentSakerSomVenterPåForhåndsvarsling(): List<Saksnummer>
    fun hentSaker(fnr: Fnr): List<Sak>

    fun hentSakForRevurdering(revurderingId: UUID): Sak

    fun hentSakForRevurdering(revurderingId: UUID, sessionContext: SessionContext): Sak

    fun hentSakforSøknadsbehandling(søknadsbehandlingId: UUID): Sak
    fun hentSakForSøknad(søknadId: UUID): Sak?
}
