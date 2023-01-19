package no.nav.su.se.bakover.domain.vilkår.bosituasjon

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Fnr
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
) {
    sealed class KunneIkkeFullføreBosituasjon {
        object HarIkkeValgtEps : KunneIkkeFullføreBosituasjon()
    }

    private fun hentFnrForUnder67(ufullstendigBosituasjon: Grunnlag.Bosituasjon): Either<KunneIkkeFullføreBosituasjon, Fnr> =
        when (ufullstendigBosituasjon) {
            is Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen,
            is Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps,
            is Grunnlag.Bosituasjon.Fullstendig.Enslig,
            is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre,
            -> KunneIkkeFullføreBosituasjon.HarIkkeValgtEps.left()

            is Grunnlag.Bosituasjon.Ufullstendig.HarEps -> ufullstendigBosituasjon.fnr.right()
            is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning -> ufullstendigBosituasjon.fnr.right()
            is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning -> ufullstendigBosituasjon.fnr.right()
        }

    fun toBosituasjon(
        ufullstendigBosituasjon: Grunnlag.Bosituasjon,
        clock: Clock,
    ): Either<KunneIkkeFullføreBosituasjon, Grunnlag.Bosituasjon.Fullstendig> {
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
                fnr = hentFnrForUnder67(ufullstendigBosituasjon).getOrElse { return it.left() },
            )
            BosituasjonValg.EPS_IKKE_UFØR_FLYKTNING -> Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(clock),
                periode = ufullstendigBosituasjon.periode,
                fnr = hentFnrForUnder67(ufullstendigBosituasjon).getOrElse { return it.left() },
            )
            BosituasjonValg.EPS_67_ELLER_OVER -> Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(clock),
                periode = ufullstendigBosituasjon.periode,
                fnr = when (ufullstendigBosituasjon) {
                    is Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen,
                    is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning,
                    is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning,
                    is Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps,
                    is Grunnlag.Bosituasjon.Fullstendig.Enslig,
                    -> return KunneIkkeFullføreBosituasjon.HarIkkeValgtEps.left()

                    is Grunnlag.Bosituasjon.Ufullstendig.HarEps -> ufullstendigBosituasjon.fnr
                    is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre -> ufullstendigBosituasjon.fnr
                },
            )
        }.right()
    }
}
