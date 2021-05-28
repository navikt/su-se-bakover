package no.nav.su.se.bakover.service.vilkår

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import java.time.Clock
import java.util.UUID

enum class BosituasjonValg {
    DELER_BOLIG_MED_VOKSNE,
    BOR_ALENE,
    EPS_UFØR_FLYKTNING,
    EPS_IKKE_UFØR_FLYKTNING,
    EPS_67_ELLER_OVER,
}

data class FullførBosituasjonRequest(
    val behandlingId: UUID,
    val bosituasjon: BosituasjonValg,
    val begrunnelse: String?,
) {
    sealed class KunneIkkeFullføreBosituasjon {
        object HarIkkeValgtEps : KunneIkkeFullføreBosituasjon()
    }
    fun toBosituasjon(ufullstendigBosituasjon: Grunnlag.Bosituasjon, clock: Clock): Either<KunneIkkeFullføreBosituasjon, Grunnlag.Bosituasjon.Fullstendig> {
        return when (bosituasjon) {
            BosituasjonValg.DELER_BOLIG_MED_VOKSNE -> Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(clock),
                periode = ufullstendigBosituasjon.periode,
            )
            BosituasjonValg.BOR_ALENE -> Grunnlag.Bosituasjon.Fullstendig.Enslig(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(clock),
                periode = ufullstendigBosituasjon.periode,
            )
            BosituasjonValg.EPS_UFØR_FLYKTNING -> Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(clock),
                periode = ufullstendigBosituasjon.periode,
                fnr = (ufullstendigBosituasjon as? Grunnlag.Bosituasjon.Ufullstendig.HarEpsIkkeValgtUførFlyktning)?.fnr ?: return KunneIkkeFullføreBosituasjon.HarIkkeValgtEps.left()
            )
            BosituasjonValg.EPS_IKKE_UFØR_FLYKTNING -> Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(clock),
                periode = ufullstendigBosituasjon.periode,
                fnr = (ufullstendigBosituasjon as? Grunnlag.Bosituasjon.Ufullstendig.HarEpsIkkeValgtUførFlyktning)?.fnr ?: return KunneIkkeFullføreBosituasjon.HarIkkeValgtEps.left()
            )
            BosituasjonValg.EPS_67_ELLER_OVER -> Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(clock),
                periode = ufullstendigBosituasjon.periode,
                fnr = (ufullstendigBosituasjon as? Grunnlag.Bosituasjon.Ufullstendig.HarEpsIkkeValgtUførFlyktning)?.fnr ?: return KunneIkkeFullføreBosituasjon.HarIkkeValgtEps.left()
            )
        }.right()
    }
}
