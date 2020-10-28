package no.nav.su.se.bakover.service.søknad.lukk

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.søknad.LukkSøknadRequest
import org.slf4j.LoggerFactory
import java.util.UUID

internal class LukkSøknadServiceImpl(
    private val søknadRepo: SøknadRepo,
    private val sakService: SakService,
    private val brevService: BrevService
) : LukkSøknadService {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun lukkSøknad(request: LukkSøknadRequest): Either<KunneIkkeLukkeSøknad, Sak> {
        val søknad = hentSøknad(request.søknadId).getOrElse {
            return KunneIkkeLukkeSøknad.FantIkkeSøknad.left()
        }
        return sjekkOmSøknadKanLukkes(søknad)
            .mapLeft { it }
            .flatMap {
                return when (request) {
                    is LukkSøknadRequest.TrekkSøknad -> trekkSøknad(request, it)
                    is LukkSøknadRequest.BortfaltSøknad -> bortfaltSøknad(request, it)
                    is LukkSøknadRequest.AvvistSøknad -> avvistSøknad(request, it)
                }
            }
    }

    override fun lagBrevutkast(
        request: LukkSøknadRequest
    ): Either<KunneIkkeLageBrevutkast, ByteArray> {
        return hentSøknad(request.søknadId).mapLeft {
            KunneIkkeLageBrevutkast.FantIkkeSøknad
        }.flatMap {
            val brevRequest = when (request) {
                is LukkSøknadRequest.TrekkSøknad -> LagBrevRequest.TrukketSøknad(it, request.trukketDato)
                is LukkSøknadRequest.BortfaltSøknad -> return KunneIkkeLageBrevutkast.UkjentBrevtype.left()
                is LukkSøknadRequest.AvvistSøknad.UtenBrev -> return KunneIkkeLageBrevutkast.UkjentBrevtype.left()
                is LukkSøknadRequest.AvvistSøknad.MedBrev -> return KunneIkkeLageBrevutkast.UkjentBrevtype.left() // TODO implement
            }
            return brevService.lagBrev(brevRequest)
                .mapLeft {
                    KunneIkkeLageBrevutkast.KunneIkkeLageBrev
                }
        }
    }

    private fun hentSøknad(søknadId: UUID): Either<KunneIkkeLukkeSøknad.FantIkkeSøknad, Søknad> {
        return søknadRepo.hentSøknad(søknadId)?.right() ?: KunneIkkeLukkeSøknad.FantIkkeSøknad.left()
    }

    private fun sjekkOmSøknadKanLukkes(søknad: Søknad): Either<KunneIkkeLukkeSøknad, Søknad> {
        if (søknad.lukket != null) {
            log.info("Prøver å lukke en allerede trukket søknad")
            return KunneIkkeLukkeSøknad.SøknadErAlleredeLukket.left()
        }
        if (søknadRepo.harSøknadPåbegyntBehandling(søknad.id)) {
            log.info("Kan ikke lukke søknad. Finnes en behandling")
            return KunneIkkeLukkeSøknad.SøknadHarEnBehandling.left()
        }
        return søknad.right()
    }

    private fun avvistSøknad(
        request: LukkSøknadRequest.AvvistSøknad,
        søknad: Søknad
    ): Either<KunneIkkeLukkeSøknad, Sak> {
        lagreLukketSøknad(request)
        when (request) {
            is LukkSøknadRequest.AvvistSøknad.MedBrev -> {
            } // TODO journalfør og distribuer
            is LukkSøknadRequest.AvvistSøknad.UtenBrev -> {
            } // noop
        }
        return sakService.hentSak(søknad.sakId).orNull()!!.right()
    }

    private fun bortfaltSøknad(
        request: LukkSøknadRequest.BortfaltSøknad,
        søknad: Søknad
    ): Either<KunneIkkeLukkeSøknad, Sak> {
        lagreLukketSøknad(request)
        return sakService.hentSak(søknad.sakId).orNull()!!.right()
    }

    private fun trekkSøknad(
        request: LukkSøknadRequest.TrekkSøknad,
        søknad: Søknad
    ): Either<KunneIkkeLukkeSøknad, Sak> {
        lagreLukketSøknad(request)
        return journalførOgDistribuerBrev(
            request = LagBrevRequest.TrukketSøknad(søknad, request.trukketDato),
            søknad = søknad
        ).mapLeft {
            it
        }.map {
            return sakService.hentSak(søknad.sakId).orNull()!!.right()
        }
    }

    private fun journalførOgDistribuerBrev(
        request: LagBrevRequest,
        søknad: Søknad
    ): Either<KunneIkkeLukkeSøknad, Unit> {
        return brevService.journalførBrev(
            request = request,
            sakId = søknad.sakId
        ).mapLeft {
            KunneIkkeLukkeSøknad.KunneIkkeJournalføreBrev
        }.map {
            return brevService.distribuerBrev(it)
                .mapLeft { KunneIkkeLukkeSøknad.KunneIkkeDistribuereBrev }
                .map { Unit }
        }
    }

    private fun lagreLukketSøknad(request: LukkSøknadRequest) {
        søknadRepo.lukkSøknad(
            søknadId = request.søknadId,
            lukket = Søknad.Lukket(
                tidspunkt = Tidspunkt.now(),
                saksbehandler = request.saksbehandler.navIdent,
                type = when (request) {
                    is LukkSøknadRequest.TrekkSøknad -> Søknad.LukketType.TRUKKET
                    is LukkSøknadRequest.BortfaltSøknad -> Søknad.LukketType.BORTFALT
                    is LukkSøknadRequest.AvvistSøknad -> Søknad.LukketType.AVVIST
                }
            )
        )
    }
}
