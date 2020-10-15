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
import no.nav.su.se.bakover.service.sak.SakService
import org.slf4j.LoggerFactory
import java.util.UUID

internal class SøknadServiceImpl(
    private val søknadRepo: SøknadRepo,
    private val sakService: SakService
) : SøknadService {
    val log = LoggerFactory.getLogger(this::class.java)

    override fun opprettSøknad(sakId: UUID, søknad: Søknad): Søknad {
        return søknadRepo.opprettSøknad(sakId, søknad)
    }

    override fun hentSøknad(søknadId: UUID): Either<KunneIkkeLukkeSøknad.FantIkkeSøknad, Søknad> {
        return søknadRepo.hentSøknad(søknadId)?.right() ?: KunneIkkeLukkeSøknad.FantIkkeSøknad.left()
    }

    private fun lukkSøknad(
        søknadId: UUID,
        saksbehandler: Saksbehandler,
        begrunnelse: String,
        loggtema: String
    ): Either<KunneIkkeLukkeSøknad, Sak> {
        val søknad = hentSøknad(søknadId).getOrElse {
            log.info("$loggtema: Fant ikke søknad")
            return KunneIkkeLukkeSøknad.FantIkkeSøknad.left()
        }
        if (søknad.lukket != null) {
            log.info("$loggtema: Prøver å lukke en allerede trukket søknad")
            return KunneIkkeLukkeSøknad.SøknadErAlleredeLukket.left()
        }
        if (søknadRepo.harSøknadPåbegyntBehandling(søknadId)) {
            log.info("$loggtema: Kan ikke lukke søknad. Finnes en behandling")
            return KunneIkkeLukkeSøknad.SøknadHarEnBehandling.left()
        }

        søknadRepo.lukkSøknad(
            søknadId = søknadId,
            lukket = Søknad.Lukket.Trukket(
                tidspunkt = Tidspunkt.now(),
                saksbehandler = saksbehandler,
                begrunnelse = begrunnelse
            )
        )
        return sakService.hentSak(søknad.sakId).orNull()!!.right()
    }

    override fun trekkSøknad(
        søknadId: UUID,
        saksbehandler: Saksbehandler,
        begrunnelse: String
    ): Either<KunneIkkeLukkeSøknad, Sak> {
        return lukkSøknad(
            søknadId = søknadId,
            saksbehandler = saksbehandler,
            begrunnelse = begrunnelse,
            loggtema = "Trekking av søknad"
        )
    }
}
