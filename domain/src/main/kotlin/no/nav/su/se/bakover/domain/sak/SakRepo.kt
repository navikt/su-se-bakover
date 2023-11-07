package no.nav.su.se.bakover.domain.sak

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Behandlingssammendrag
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import java.util.UUID

interface SakRepo {
    fun hentSak(sakId: UUID): Sak?
    fun hentSak(sakId: UUID, sessionContext: SessionContext): Sak?
    fun hentSak(fnr: Fnr, type: Sakstype): Sak?
    fun hentSak(saksnummer: Saksnummer): Sak?
    fun hentSak(hendelseId: HendelseId): Sak?
    fun hentSakInfoForIdenter(personidenter: NonEmptyList<String>): SakInfo?
    fun hentSakInfo(sakId: UUID): SakInfo?
    fun opprettSak(sak: NySak)
    fun hentÅpneBehandlinger(): List<Behandlingssammendrag>
    fun hentFerdigeBehandlinger(): List<Behandlingssammendrag>
    fun hentSakIdSaksnummerOgFnrForAlleSaker(): List<SakInfo>
    fun hentSaker(fnr: Fnr): List<Sak>

    fun hentSakForRevurdering(revurderingId: UUID): Sak

    fun hentSakForRevurdering(revurderingId: UUID, sessionContext: SessionContext): Sak

    fun hentSakforSøknadsbehandling(søknadsbehandlingId: UUID): Sak
    fun hentSakForSøknad(søknadId: UUID): Sak?
    fun hentSakForVedtak(vedtakId: UUID): Sak?
    fun oppdaterFødselsnummer(
        sakId: UUID,
        gammeltFnr: Fnr,
        nyttFnr: Fnr,
        endretAv: NavIdentBruker,
        endretTidspunkt: Tidspunkt,
        sessionContext: SessionContext? = null,
    )
}
