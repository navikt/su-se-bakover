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
        object DepositumErHøyereEnnInnskudd : FeilVedValideringAvBehandlingsinformasjon()
        object BosituasjonOgFormueForEpsErIkkeKonsistent : FeilVedValideringAvBehandlingsinformasjon()
        object KanIkkeLeggeTilFormueFørBosituasjon : FeilVedValideringAvBehandlingsinformasjon()
    }

    fun hentValidertBehandlingsinformasjon(
        bosituasjon: Grunnlag.Bosituasjon?,
    ): Either<FeilVedValideringAvBehandlingsinformasjon, Behandlingsinformasjon> {
        val formue = behandlingsinformasjon.formue ?: return behandlingsinformasjon.right()

        val borSøkerMedEPS = bosituasjon?.harEktefelle()
            ?: return FeilVedValideringAvBehandlingsinformasjon.KanIkkeLeggeTilFormueFørBosituasjon.left()

        if (formue.erDepositumHøyereEnnInnskudd()) {
            return FeilVedValideringAvBehandlingsinformasjon.DepositumErHøyereEnnInnskudd.left()
        }

        if (!erEpsFormueOgBosituasjonKonsistent(formue, borSøkerMedEPS)) {
            return FeilVedValideringAvBehandlingsinformasjon.BosituasjonOgFormueForEpsErIkkeKonsistent.left()
        }

        return behandlingsinformasjon.right()
    }

    private fun erEpsFormueOgBosituasjonKonsistent(
        formue: Behandlingsinformasjon.Formue,
        borSøkerMedEPS: Boolean,
    ): Boolean {
        if (borSøkerMedEPS && formue.epsVerdier == null) {
            return false
        }
        if (!borSøkerMedEPS && formue.epsVerdier != null) {
            return false
        }

        return true
    }
}
