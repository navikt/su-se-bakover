package no.nav.su.se.bakover.service.søknad
import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.sak.SakService
import org.slf4j.LoggerFactory
import java.util.UUID

internal class SøknadServiceImpl(
    private val søknadRepo: SøknadRepo,
    private val sakService: SakService,
    private val brevService: BrevService,
) : SøknadService {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun opprettSøknad(sakId: UUID, søknad: Søknad): Søknad {
        return søknadRepo.opprettSøknad(sakId, søknad)
    }

    override fun hentSøknad(søknadId: UUID): Either<KunneIkkeLukkeSøknad.FantIkkeSøknad, Søknad> {
        return søknadRepo.hentSøknad(søknadId)?.right() ?: KunneIkkeLukkeSøknad.FantIkkeSøknad.left()
    }

    override fun lukkSøknad(
        søknadId: UUID,
        saksbehandler: Saksbehandler,
        lukketSøknadBody: Søknad.LukketSøknadBody
    ): Either<KunneIkkeLukkeSøknad, Sak> {
        return trekkSøknad(
            søknadId = søknadId,
            saksbehandler = saksbehandler,
            loggtema = "Trekking av søknad",
            lukketSøknadBody = lukketSøknadBody
        )
    }

    private fun trekkSøknad(
        søknadId: UUID,
        saksbehandler: Saksbehandler,
        loggtema: String,
        lukketSøknadBody: Søknad.LukketSøknadBody
    ): Either<KunneIkkeLukkeSøknad, Sak> {
        val søknad = hentSøknad(søknadId).getOrElse {
            log.error("$loggtema: Fant ikke søknad")
            return KunneIkkeLukkeSøknad.FantIkkeSøknad.left()
        }
        if (søknad.lukket != null) {
            log.error("$loggtema: Prøver å lukke en allerede trukket søknad")
            return KunneIkkeLukkeSøknad.SøknadErAlleredeLukket.left()
        }
        if (søknadRepo.harSøknadPåbegyntBehandling(søknadId)) {
            log.error("$loggtema: Kan ikke lukke søknad. Finnes en behandling")
            return KunneIkkeLukkeSøknad.SøknadHarEnBehandling.left()
        }

        return brevService.journalførLukketSøknadOgSendBrev(
            sakId = søknad.sakId,
            søknad = søknad,
            lukketSøknadBody = lukketSøknadBody
        ).fold(
            ifLeft = {
                log.error("$loggtema: Kunne ikke sende brev for å lukke søknad")
                KunneIkkeLukkeSøknad.KunneIkkeSendeBrev.left()
            },
            ifRight = {
                log.info("Bestilt distribusjon av brev for trukket søknad. Bestillings-id: $it")
                søknadRepo.lukkSøknad(
                    søknadId = søknadId,
                    lukket = Søknad.Lukket.Trukket(
                        tidspunkt = Tidspunkt.now(),
                        saksbehandler = saksbehandler,
                        typeLukking = Søknad.TypeLukking.Trukket
                    )
                )
                log.info("Trukket søknad $søknadId")
                return sakService.hentSak(søknad.sakId).mapLeft {
                    return KunneIkkeLukkeSøknad.KunneIkkeSendeBrev.left()
                }
            }
        )
    }

    override fun lagLukketSøknadBrevutkast(
        søknadId: UUID,
        lukketSøknadBody: Søknad.LukketSøknadBody
    ): Either<KunneIkkeLageBrevutkast, ByteArray> {
        val søknad = hentSøknad(søknadId).getOrElse {
            log.error("Lukket brevutkast: Fant ikke søknad")
            return KunneIkkeLageBrevutkast.FantIkkeSøknad.left()
        }

        return brevService.lagLukketSøknadBrevutkast(
            sakId = søknad.sakId,
            søknad = søknad,
            lukketSøknadBody = lukketSøknadBody
        ).mapLeft {
            log.error("Lukket brevutkast: feil ved generering av brevutkast")
            KunneIkkeLageBrevutkast.FeilVedGenereringAvBrevutkast
        }
    }
}
