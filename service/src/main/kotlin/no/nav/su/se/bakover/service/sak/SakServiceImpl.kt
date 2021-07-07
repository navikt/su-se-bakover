package no.nav.su.se.bakover.service.sak

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.database.sak.SakRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NySak
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.ÅpenBehandling
import no.nav.su.se.bakover.domain.behandling.ÅpenBehandlingStatus
import no.nav.su.se.bakover.domain.behandling.ÅpenBehandlingType
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import org.slf4j.LoggerFactory
import java.util.UUID

internal class SakServiceImpl(
    private val sakRepo: SakRepo,
) : SakService {
    private val log = LoggerFactory.getLogger(this::class.java)
    val observers: MutableList<EventObserver> = mutableListOf()

    override fun hentSak(sakId: UUID): Either<FantIkkeSak, Sak> {
        return sakRepo.hentSak(sakId)?.right() ?: FantIkkeSak.left()
    }

    override fun hentSak(fnr: Fnr): Either<FantIkkeSak, Sak> {
        return sakRepo.hentSak(fnr)?.right() ?: FantIkkeSak.left()
    }

    override fun hentSak(saksnummer: Saksnummer): Either<FantIkkeSak, Sak> {
        return sakRepo.hentSak(saksnummer)?.right() ?: FantIkkeSak.left()
    }

    override fun opprettSak(sak: NySak) {
        sakRepo.opprettSak(sak).also {
            hentSak(sak.id).fold(
                ifLeft = { log.error("Opprettet sak men feilet ved henting av den.") },
                ifRight = {
                    observers.forEach { observer -> observer.handle(Event.Statistikk.SakOpprettet(it)) }
                },
            )
        }
    }

    override fun hentÅpneBehandlingerForAlleSaker(): List<ÅpenBehandling> {
        val alleSaker = sakRepo.hentAlleSaker()

        return alleSaker.flatMap { sak ->
            val åpneSøknader = sak.hentÅpneSøknader().map { søknad ->
                ÅpenBehandling(
                    saksnummer = sak.saksnummer,
                    behandlingsId = søknad.id,
                    åpenBehandlingType = ÅpenBehandlingType.SØKNADSBEHANDLING,
                    status = ÅpenBehandlingStatus.NY_SØKNAD,
                    opprettet = søknad.opprettet,
                )
            }
            val åpneSøknadsbehandlinger = sak.hentÅpneSøknadsbehandlinger().map { søknadsbehandling ->
                ÅpenBehandling(
                    saksnummer = sak.saksnummer,
                    behandlingsId = søknadsbehandling.id,
                    åpenBehandlingType = ÅpenBehandlingType.SØKNADSBEHANDLING,
                    status = ÅpenBehandlingStatus.søknadsbehandlingTilStatus(søknadsbehandling),
                    opprettet = søknadsbehandling.opprettet,
                )
            }
            val åpneRevurderinger = sak.hentÅpneRevurderinger().map { revurdering ->
                ÅpenBehandling(
                    saksnummer = sak.saksnummer,
                    behandlingsId = revurdering.id,
                    åpenBehandlingType = ÅpenBehandlingType.REVURDERING,
                    status = ÅpenBehandlingStatus.revurderingTilStatus(revurdering),
                    opprettet = revurdering.opprettet,
                )
            }

            åpneSøknader + åpneSøknadsbehandlinger + åpneRevurderinger
        }
    }
}
