package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.database.søknad.KunneIkkeTrekkeSøknad
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.database.søknad.SøknadTrukketOk
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.TrukketSøknadBody
import no.nav.su.se.bakover.service.brev.BrevService
import org.slf4j.LoggerFactory
import java.util.UUID

internal class SøknadServiceImpl(
    private val søknadRepo: SøknadRepo,
    private val behandlingRepo: BehandlingRepo,
    private val brevService: BrevService
) : SøknadService {
    val log = LoggerFactory.getLogger(this::class.java)

    override fun opprettSøknad(sakId: UUID, søknad: Søknad): Søknad {
        return søknadRepo.opprettSøknad(sakId, søknad)
    }

    override fun hentSøknad(søknadId: UUID): Either<FantIkkeSøknad, Søknad> {
        return søknadRepo.hentSøknad(søknadId)?.right() ?: FantIkkeSøknad.left()
    }

    override fun trekkSøknad(
        trukketSøknadBody: TrukketSøknadBody
    ): Either<KunneIkkeTrekkeSøknad, SøknadTrukketOk> {
        val loggtema = "Avslutting av søknadsbehandling"

        if (søknadRepo.hentSøknad(trukketSøknadBody.søknadId)!!.søknadTrukket) {
            log.error("$loggtema: Prøve å trekke en allerede trukket søknad")
            return KunneIkkeTrekkeSøknad.left()
        }

        if (behandlingRepo.finnesBehandlingForSøknad(trukketSøknadBody.søknadId)) {
            log.error("$loggtema: Kan ikke trekke søknad. Finnes en behandling")
            return KunneIkkeTrekkeSøknad.left()
        }

        return brevService.journalførTrukketSøknadOgSendBrev(trukketSøknadBody).fold(
            ifLeft = {
                log.error("$loggtema: Kunne ikke sende brev for å trekke søknad")
                KunneIkkeTrekkeSøknad.left()
            },
            ifRight = {
                log.info("Bestillings id for trekking av søknad: $it")
                søknadRepo.trekkSøknad(trukketSøknadBody)
                SøknadTrukketOk.right()
            }
        )
    }
}
