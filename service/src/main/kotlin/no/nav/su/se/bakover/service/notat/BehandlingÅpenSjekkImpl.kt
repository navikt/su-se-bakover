package no.nav.su.se.bakover.service.notat

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import behandling.klage.domain.KlageId
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.domain.klage.KlageTilAttestering
import no.nav.su.se.bakover.domain.notat.BehandlingStatus
import no.nav.su.se.bakover.domain.notat.BehandlingÅpenSjekk
import no.nav.su.se.bakover.domain.notat.NotatFeil
import no.nav.su.se.bakover.domain.notat.ReferanseType
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.repo.RevurderingRepo
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingTilAttestering
import java.util.UUID

class BehandlingÅpenSjekkImpl(
    private val revurderingRepo: RevurderingRepo,
    private val søknadsbehandlingRepo: SøknadsbehandlingRepo,
    private val klageRepo: KlageRepo,
    private val søknadRepo: SøknadRepo,
) : BehandlingÅpenSjekk {

    override fun hentStatus(referanseId: UUID, referanseType: ReferanseType): Either<NotatFeil, BehandlingStatus> {
        return when (referanseType) {
            ReferanseType.SØKNADSBEHANDLING -> {
                val behandling = søknadsbehandlingRepo.hent(SøknadsbehandlingId(referanseId))
                    ?: return NotatFeil.FantIkkeBehandling.left()
                BehandlingStatus(
                    erÅpen = behandling.erÅpen(),
                    erTilAttestering = behandling is SøknadsbehandlingTilAttestering,
                ).right()
            }

            ReferanseType.REVURDERING -> {
                val rev = revurderingRepo.hent(RevurderingId(referanseId))
                    ?: return NotatFeil.FantIkkeBehandling.left()
                BehandlingStatus(
                    erÅpen = rev.erÅpen(),
                    erTilAttestering = rev is RevurderingTilAttestering,
                ).right()
            }

            ReferanseType.SØKNAD -> {
                val søknad = søknadRepo.hentSøknad(referanseId)
                    ?: return NotatFeil.FantIkkeSøknad.left()
                val erÅpen = when (søknad) {
                    is Søknad.Journalført.MedOppgave.IkkeLukket -> true
                    is Søknad.Journalført.MedOppgave.Lukket.Avvist -> false
                    is Søknad.Journalført.MedOppgave.Lukket.Bortfalt -> false
                    is Søknad.Journalført.MedOppgave.Lukket.TrukketAvSøker -> false
                    is Søknad.Journalført.UtenOppgave -> true
                    is Søknad.Ny -> true
                }
                BehandlingStatus(
                    erÅpen = erÅpen,
                    erTilAttestering = false,
                ).right()
            }

            ReferanseType.KLAGE -> {
                val klage = klageRepo.hentKlage(KlageId(referanseId))
                    ?: return NotatFeil.FantIkkeBehandling.left()
                BehandlingStatus(
                    erÅpen = klage.erÅpen(),
                    erTilAttestering = klage is KlageTilAttestering,
                ).right()
            }
        }
    }
}
