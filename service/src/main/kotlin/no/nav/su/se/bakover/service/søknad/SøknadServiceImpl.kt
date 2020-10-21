package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.LukketSøknadBrevinnhold
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.sak.SakService
import org.slf4j.LoggerFactory
import java.util.UUID

internal class SøknadServiceImpl(
    private val søknadRepo: SøknadRepo,
    private val sakService: SakService,
    private val brevService: BrevService,
    private val personOppslag: PersonOppslag,
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
        lukketSøknad: Søknad.Lukket
    ): Either<KunneIkkeLukkeSøknad, Sak> {
        return trekkSøknad(
            søknadId = søknadId,
            loggtema = "Trekking av søknad",
            lukketSøknad = lukketSøknad
        )
    }

    private fun trekkSøknad(
        søknadId: UUID,
        loggtema: String,
        lukketSøknad: Søknad.Lukket
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
            lukketSøknad = lukketSøknad
        ).fold(
            ifLeft = {
                log.error("$loggtema: Kunne ikke sende brev for å lukke søknad")
                KunneIkkeLukkeSøknad.KunneIkkeSendeBrev.left()
            },
            ifRight = {
                log.info("Bestilt distribusjon av brev for trukket søknad. Bestillings-id: $it")
                søknadRepo.lukkSøknad(
                    søknadId = søknadId,
                    lukket = lukketSøknad
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
        lukketSøknad: Søknad.Lukket
    ): Either<KunneIkkeLageBrevutkast, ByteArray> {
        val søknad = hentSøknad(søknadId).getOrElse {
            log.error("Lukket brevutkast: Fant ikke søknad")
            return KunneIkkeLageBrevutkast.FantIkkeSøknad.left()
        }

        val person = hentPersonFraFnr(søknad.søknadInnhold.personopplysninger.fnr).fold(
            { return KunneIkkeLageBrevutkast.FeilVedHentingAvPerson.left() },
            { it }
        )

        val lukketSøknadBrevinnhold = LukketSøknadBrevinnhold.lagLukketSøknadBrevinnhold(
            person = person,
            søknad = søknad,
            lukketSøknad = lukketSøknad
        )
        return brevService.lagBrev(lukketSøknadBrevinnhold)
            .mapLeft { KunneIkkeLageBrevutkast.FeilVedGenereringAvBrevutkast }
    }

    private fun hentPersonFraFnr(fnr: Fnr) = personOppslag.person(fnr)
        .mapLeft {
            log.error("Fant ikke person i eksternt system basert på sakens fødselsnummer.")
            it
        }.map {
            log.info("Hentet person fra eksternt system OK")
            it
        }
}
