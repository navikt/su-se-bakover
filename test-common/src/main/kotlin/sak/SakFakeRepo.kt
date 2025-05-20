package no.nav.su.se.bakover.test.sak

import arrow.atomic.Atomic
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.extensions.singleOrNullOrThrow
import no.nav.su.se.bakover.common.domain.sak.Behandlingssammendrag
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.sak.NySak
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import java.util.UUID

class SakFakeRepo : SakRepo {
    private val data = Atomic(mutableMapOf<UUID, Sak>())

    override fun hentSak(sakId: UUID): Sak? {
        return data.get()[sakId]
    }

    override fun hentSak(
        sakId: UUID,
        sessionContext: SessionContext,
    ): Sak? {
        return data.get()[sakId]
    }

    override fun hentSak(
        fnr: Fnr,
        type: Sakstype,
    ): Sak? {
        return data.get().values.singleOrNullOrThrow { it.fnr == fnr && it.type == type }
    }

    override fun hentSak(saksnummer: Saksnummer): Sak? {
        return data.get().values.singleOrNullOrThrow { it.saksnummer == saksnummer }
    }

    override fun hentSak(hendelseId: HendelseId): Sak? {
        TODO("Not yet implemented")
    }

    override fun hentSakInfoForIdenter(fnr: Fnr): List<SakInfo> {
        TODO("Not yet implemented")
    }

    override fun hentSakInfo(sakId: UUID): SakInfo? {
        return data.get()[sakId]?.let {
            SakInfo(
                sakId = it.id,
                fnr = it.fnr,
                saksnummer = it.saksnummer,
                type = it.type,
            )
        }
    }

    override fun hentSakInfo(fnr: Fnr): SakInfo? {
        return data.get().values.singleOrNullOrThrow { it.fnr == fnr }?.let {
            SakInfo(
                sakId = it.id,
                fnr = it.fnr,
                saksnummer = it.saksnummer,
                type = it.type,
            )
        }
    }

    override fun opprettSak(sak: NySak) {
        TODO("Not yet implemented")
    }

    override fun hentÅpneBehandlinger(): List<Behandlingssammendrag> {
        TODO("Not yet implemented")
    }

    override fun hentFerdigeBehandlinger(): List<Behandlingssammendrag> {
        TODO("Not yet implemented")
    }

    override fun hentSakIdSaksnummerOgFnrForAlleSaker(): List<SakInfo> {
        return data.get().values.map {
            SakInfo(
                sakId = it.id,
                fnr = it.fnr,
                saksnummer = it.saksnummer,
                type = it.type,
            )
        }
    }

    override fun hentSaker(fnr: Fnr): List<Sak> {
        return data.get().values.filter { it.fnr == fnr }
    }

    override fun hentSakForRevurdering(revurderingId: RevurderingId): Sak {
        return data.get().values.single { it.behandlinger.revurderinger == revurderingId }
    }

    override fun hentSakForRevurdering(
        revurderingId: RevurderingId,
        sessionContext: SessionContext,
    ): Sak {
        return data.get().values.single { it.behandlinger.revurderinger == revurderingId }
    }

    override fun hentSakForUtbetalingId(
        utbetalingId: UUID30,
        sessionContext: SessionContext?,
    ): Sak? {
        return data.get().values.singleOrNull { it.utbetalinger.any { utbetaling -> utbetaling.id == utbetalingId } }
    }

    override fun hentSakforSøknadsbehandling(søknadsbehandlingId: SøknadsbehandlingId): Sak {
        return data.get().values.single { it.behandlinger.søknadsbehandlinger.any { it.id == søknadsbehandlingId } }
    }

    override fun hentSakForSøknad(søknadId: UUID): Sak? {
        return data.get().values.singleOrNull { it.søknader.any { it.id == søknadId } }
    }

    override fun hentSakForVedtak(vedtakId: UUID): Sak? {
        return data.get().values.singleOrNull { it.vedtakListe.any { it.id == vedtakId } }
    }

    override fun oppdaterFødselsnummer(
        sakId: UUID,
        gammeltFnr: Fnr,
        nyttFnr: Fnr,
        endretAv: NavIdentBruker,
        endretTidspunkt: Tidspunkt,
        sessionContext: SessionContext?,
    ) {
        TODO("Not yet implemented")
    }
}
