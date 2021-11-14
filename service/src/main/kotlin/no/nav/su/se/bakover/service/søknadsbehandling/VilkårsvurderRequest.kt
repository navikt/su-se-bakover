package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import java.util.UUID

data class VilkårsvurderRequest(
    val behandlingId: UUID,
    private val behandlingsinformasjon: Behandlingsinformasjon,
) {

    sealed class FeilVedValideringAvBehandlingsinformasjon {
        object DepositumIkkeMindreEnnInnskudd : FeilVedValideringAvBehandlingsinformasjon()
        object BosituasjonOgFormueForEpsErIkkeKonsistent : FeilVedValideringAvBehandlingsinformasjon()
    }

    fun hentValidertBehandlingsinformasjon(
        bosituasjon: Grunnlag.Bosituasjon?,
    ): Either<FeilVedValideringAvBehandlingsinformasjon, Behandlingsinformasjon> {
        val borSøkerMedEPS = bosituasjon?.harEktefelle() ?: false

        if (behandlingsinformasjon.formue?.erDepositumHøyereEnnInnskud() == true) {
            return FeilVedValideringAvBehandlingsinformasjon.DepositumIkkeMindreEnnInnskudd.left()
        }

        if (!erEpsFormueOgBosituasjonKonsistent(borSøkerMedEPS)) {
            return FeilVedValideringAvBehandlingsinformasjon.BosituasjonOgFormueForEpsErIkkeKonsistent.left()
        }

        return behandlingsinformasjon.right()
    }

    private fun erEpsFormueOgBosituasjonKonsistent(
        borSøkerMedEPS: Boolean,
    ): Boolean {
        // i noen tilfeller, har vi EPS, men formue objektet er null, da vil vi eksplisitt sjekke at epsVerdier ikke er null, og ikke safe call med formue
        if (behandlingsinformasjon.formue != null) {
            if (borSøkerMedEPS && behandlingsinformasjon.formue!!.epsVerdier == null) {
                return false
            }
            if (!borSøkerMedEPS && behandlingsinformasjon.formue!!.epsVerdier != null) {
                return false
            }
        }
        return true
    }
}
