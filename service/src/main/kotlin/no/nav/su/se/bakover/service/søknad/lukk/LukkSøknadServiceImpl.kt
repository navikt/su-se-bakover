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
import no.nav.su.se.bakover.domain.brev.søknad.lukk.AvvistSøknadBrevRequest
import no.nav.su.se.bakover.domain.brev.søknad.lukk.TrukketSøknadBrevRequest
import no.nav.su.se.bakover.domain.søknad.LukkSøknadRequest
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.sak.SakService
import org.slf4j.LoggerFactory
import java.util.UUID

internal class LukkSøknadServiceImpl(
    private val søknadRepo: SøknadRepo,
    private val sakService: SakService,
    private val brevService: BrevService,
    private val oppgaveService: OppgaveService
) : LukkSøknadService {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun lukkSøknad(request: LukkSøknadRequest): Either<KunneIkkeLukkeSøknad, Sak> {
        val søknad = hentSøknad(request.søknadId).getOrElse {
            return KunneIkkeLukkeSøknad.FantIkkeSøknad.left()
        }

        val oppgaveId = søknad.oppgaveId

        return sjekkOmSøknadKanLukkes(søknad)
            .map {
                return when (request) {
                    is LukkSøknadRequest.MedBrev -> lukkSøknadMedBrev(request, it)
                    is LukkSøknadRequest.UtenBrev -> lukkSøknadUtenBrev(request, it)
                }.map { sak ->
                    if (oppgaveId == null) {
                        log.info("Kunne ikke lukke oppgave da oppgave ikke var opprettet")
                    } else {
                        oppgaveService.lukkOppgave(oppgaveId).mapLeft {
                            log.warn("Kunne ikke lukke oppgave $oppgaveId for søknad ${søknad.id}")
                        }
                    }
                    sak
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
                is LukkSøknadRequest.MedBrev -> lagBrevRequest(it, request)
                is LukkSøknadRequest.UtenBrev -> return KunneIkkeLageBrevutkast.UkjentBrevtype.left()
            }
            return brevService.lagBrev(brevRequest)
                .mapLeft {
                    KunneIkkeLageBrevutkast.KunneIkkeLageBrev
                }
        }
    }

    private fun lagBrevRequest(søknad: Søknad, request: LukkSøknadRequest.MedBrev): LagBrevRequest {
        return when (request) {
            is LukkSøknadRequest.MedBrev.TrekkSøknad -> TrukketSøknadBrevRequest(søknad, request.trukketDato)
            is LukkSøknadRequest.MedBrev.AvvistSøknad -> AvvistSøknadBrevRequest(søknad, request.brevConfig)
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

    private fun lukkSøknadUtenBrev(
        request: LukkSøknadRequest.UtenBrev,
        søknad: Søknad
    ): Either<KunneIkkeLukkeSøknad, Sak> {
        lagreLukketSøknad(request)
        return sakService.hentSak(søknad.sakId).orNull()!!.right()
    }

    private fun lukkSøknadMedBrev(
        request: LukkSøknadRequest.MedBrev,
        søknad: Søknad
    ): Either<KunneIkkeLukkeSøknad, Sak> {
        lagreLukketSøknad(request)
        return journalførOgDistribuerBrev(
            request = lagBrevRequest(søknad, request),
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
                    is LukkSøknadRequest.MedBrev.TrekkSøknad -> Søknad.LukketType.TRUKKET
                    is LukkSøknadRequest.MedBrev.AvvistSøknad -> Søknad.LukketType.AVVIST
                    is LukkSøknadRequest.UtenBrev.BortfaltSøknad -> Søknad.LukketType.BORTFALT
                    is LukkSøknadRequest.UtenBrev.AvvistSøknad -> Søknad.LukketType.AVVIST
                }
            )
        )
    }
}
