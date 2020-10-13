package no.nav.su.se.bakover.service.søknad
import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadTrukket
import org.slf4j.LoggerFactory
import java.util.UUID

internal class SøknadServiceImpl(
    private val søknadRepo: SøknadRepo,
    private val behandlingRepo: BehandlingRepo
) : SøknadService {
    val log = LoggerFactory.getLogger(this::class.java)

    override fun opprettSøknad(sakId: UUID, søknad: Søknad): Søknad {
        return søknadRepo.opprettSøknad(sakId, søknad)
    }

    override fun hentSøknad(søknadId: UUID): Either<SøknadServiceFeil.FantIkkeSøknad, Søknad> {
        return søknadRepo.hentSøknad(søknadId)?.right() ?: SøknadServiceFeil.FantIkkeSøknad.left()
    }

    override fun trekkSøknad(
        søknadId: UUID,
        saksbehandler: Saksbehandler
    ): Either<SøknadServiceFeil, SøknadTrukketOk> {
        val loggtema = "Trekking av søknad"

        val søknad = hentSøknad(søknadId).getOrElse {
            log.error("$loggtema: Fant ikke søknad")
            return SøknadServiceFeil.FantIkkeSøknad.left()
        }
        if (søknad.søknadTrukket != null) {
            log.error("$loggtema: Prøver å trekke en allerede trukket søknad")
            return SøknadServiceFeil.SøknadErAlleredeTrukket.left()
        }
        if (behandlingRepo.harSøknadsbehandling(søknadId)) {
            log.error("$loggtema: Kan ikke trekke søknad. Finnes en behandling")
            return SøknadServiceFeil.SøknadHarEnBehandling.left()
        }

        søknadRepo.trekkSøknad(
            søknadId = søknadId,
            søknadTrukket = SøknadTrukket(
                tidspunkt = Tidspunkt.now(),
                saksbehandler = saksbehandler
            )
        )
        return SøknadTrukketOk.right()
    }
}
