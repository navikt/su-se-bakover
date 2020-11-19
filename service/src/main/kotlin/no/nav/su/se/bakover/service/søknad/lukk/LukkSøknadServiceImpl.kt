package no.nav.su.se.bakover.service.søknad.lukk

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
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
        val søknad = hentSøknad(request.søknadId).getOrHandle {
            return it.left()
        }
        val opprettetDato = søknad.opprettet.toLocalDate()
        if (request is LukkSøknadRequest.MedBrev.TrekkSøknad && !request.erDatoGyldig(opprettetDato)) {
            log.info("Kan ikke lukke søknad. Dato ${request.trukketDato} må være mellom $opprettetDato og idag")
            return KunneIkkeLukkeSøknad.UgyldigDato.left()
        }
        if (søknadRepo.harSøknadPåbegyntBehandling(søknad.id)) {
            log.info("Kan ikke lukke søknad. Finnes en behandling")
            return KunneIkkeLukkeSøknad.SøknadHarEnBehandling.left()
        }
        return when (søknad) {
            is Søknad.Lukket -> {
                log.info("Søknad var allerede lukket.")
                KunneIkkeLukkeSøknad.SøknadErAlleredeLukket.left()
            }
            is Søknad.Ny -> {
                log.info("Lukker ikke-journalført søknad.")
                lukkSøknad(request, søknad)
            }
            is Søknad.Journalført.UtenOppgave -> {
                log.info("Lukker journalført søknad uten oppgave.")
                lukkSøknad(request, søknad)
            }
            is Søknad.Journalført.MedOppgave -> {
                log.info("Lukker journalført søknad og tilhørende oppgave.")
                lukkSøknad(request, søknad).map {
                    oppgaveService.lukkOppgave(søknad.oppgaveId).mapLeft {
                        log.warn("Kunne ikke lukke oppgave ${søknad.oppgaveId} for søknad ${søknad.id}")
                    }
                    it
                }
            }
        }
    }

    private fun lukkSøknad(request: LukkSøknadRequest, søknad: Søknad): Either<KunneIkkeLukkeSøknad, Sak> {
        return when (request) {
            is LukkSøknadRequest.MedBrev -> lukkSøknadMedBrev(request, søknad)
            is LukkSøknadRequest.UtenBrev -> lukkSøknadUtenBrev(request, søknad)
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

    private fun lukkSøknadUtenBrev(
        request: LukkSøknadRequest.UtenBrev,
        søknad: Søknad
    ): Either<KunneIkkeLukkeSøknad, Sak> {
        lagreLukketSøknad(request, søknad)
        return sakService.hentSak(søknad.sakId).orNull()!!.right()
    }

    private fun lukkSøknadMedBrev(
        request: LukkSøknadRequest.MedBrev,
        søknad: Søknad
    ): Either<KunneIkkeLukkeSøknad, Sak> {
        lagreLukketSøknad(request, søknad)
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
            // TODO: Her kan vi ende opp med å svare med feil, selvom vi har persistert søknaden som lukket
            KunneIkkeLukkeSøknad.KunneIkkeJournalføreBrev
        }.map {
            return brevService.distribuerBrev(it)
                .mapLeft { KunneIkkeLukkeSøknad.KunneIkkeDistribuereBrev }
                .map { Unit }
        }
    }

    private fun lagreLukketSøknad(request: LukkSøknadRequest, søknad: Søknad) {
        søknadRepo.oppdaterSøknad(
            søknad.lukk(
                lukketAv = request.saksbehandler,
                type = when (request) {
                    is LukkSøknadRequest.MedBrev.TrekkSøknad -> Søknad.Lukket.LukketType.TRUKKET
                    is LukkSøknadRequest.MedBrev.AvvistSøknad -> Søknad.Lukket.LukketType.AVVIST
                    is LukkSøknadRequest.UtenBrev.BortfaltSøknad -> Søknad.Lukket.LukketType.BORTFALT
                    is LukkSøknadRequest.UtenBrev.AvvistSøknad -> Søknad.Lukket.LukketType.AVVIST
                }
            )
        )
    }
}
