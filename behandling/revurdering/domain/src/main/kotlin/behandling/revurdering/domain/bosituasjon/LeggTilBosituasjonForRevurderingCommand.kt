package behandling.revurdering.domain.bosituasjon

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import person.domain.KunneIkkeHentePerson
import person.domain.Person
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon
import java.time.Clock
import java.util.UUID

data class LeggTilBosituasjonForRevurderingCommand(
    val periode: Periode,
    val epsFnr: String?,
    val delerBolig: Boolean?,
    val ektemakeEllerSamboerUførFlyktning: Boolean?,
    val epsFylt67: Boolean?,
) {
    fun toDomain(
        clock: Clock,
        hentPerson: (fnr: Fnr) -> Either<KunneIkkeHentePerson, Person>,
    ): Either<KunneIkkeLeggeTilBosituasjongrunnlagForRevurdering, Bosituasjon.Fullstendig> {
        if ((epsFnr == null && delerBolig == null) || (epsFnr != null && delerBolig != null) || (epsFnr != null && epsFylt67 == null)) {
            return KunneIkkeLeggeTilBosituasjongrunnlagForRevurdering.UgyldigData.left()
        }

        if (epsFnr != null) {
            val eps = hentPerson(Fnr(epsFnr)).getOrElse {
                return KunneIkkeLeggeTilBosituasjongrunnlagForRevurdering.KunneIkkeSlåOppEPS.left()
            }
            val epsFnr = eps.ident.fnr

            return when {
                epsFylt67!! -> Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(clock),
                    periode = periode,
                    fnr = epsFnr,
                ).right()

                else -> when (ektemakeEllerSamboerUførFlyktning) {
                    true -> Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(clock),
                        periode = periode,
                        fnr = epsFnr,
                    ).right()

                    false -> Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(clock),
                        periode = periode,
                        fnr = epsFnr,
                    ).right()

                    null -> return KunneIkkeLeggeTilBosituasjongrunnlagForRevurdering.UgyldigData.left()
                }
            }
        }

        if (delerBolig != null) {
            return when (delerBolig) {
                true -> Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(clock),
                    periode = periode,
                ).right()

                false -> Bosituasjon.Fullstendig.Enslig(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(clock),
                    periode = periode,
                ).right()
            }
        }

        return KunneIkkeLeggeTilBosituasjongrunnlagForRevurdering.UgyldigData.left()
    }
}
