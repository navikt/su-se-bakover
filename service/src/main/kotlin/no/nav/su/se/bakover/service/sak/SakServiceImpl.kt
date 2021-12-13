package no.nav.su.se.bakover.service.sak

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.BegrensetSakinfo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NySak
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.sak.SakRestans
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

internal class SakServiceImpl(
    private val sakRepo: SakRepo,
    private val clock: Clock,
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

    override fun hentRestanserForAlleSaker(): List<SakRestans> {
        return sakRepo.hentSakRestanser()
    }

    override fun hentBegrensetSakinfo(fnr: Fnr): Either<FantIkkeSak, BegrensetSakinfo> {
        return hentSak(fnr)
            .map { sak ->
                val now = LocalDate.now(clock)
                BegrensetSakinfo(
                    harÅpenSøknad = sak.søknader
                        .any { søknad ->
                            val behandling = sak.søknadsbehandlinger
                                .find { b -> b.søknad.id == søknad.id }
                            (
                                søknad !is Søknad.Journalført.MedOppgave.Lukket &&
                                    (behandling == null || !behandling.erIverksatt)
                                )
                        },
                    iverksattInnvilgetStønadsperiode = sak
                        .hentPerioderMedLøpendeYtelse()
                        .filter { it.inneholder(now) }
                        .maxByOrNull { it.tilOgMed },
                )
            }
    }
}
