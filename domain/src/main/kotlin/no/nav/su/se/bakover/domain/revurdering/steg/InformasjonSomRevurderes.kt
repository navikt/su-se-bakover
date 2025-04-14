package no.nav.su.se.bakover.domain.revurdering.steg

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import org.jetbrains.kotlin.utils.keysToMap

data class InformasjonSomRevurderes private constructor(
    val informasjonSomRevurderes: Map<Revurderingsteg, Vurderingstatus>,
) : Map<Revurderingsteg, Vurderingstatus> by informasjonSomRevurderes {

    fun markerSomVurdert(revurderingsteg: Revurderingsteg): InformasjonSomRevurderes {
        return copy(
            informasjonSomRevurderes = informasjonSomRevurderes + mapOf(revurderingsteg to Vurderingstatus.Vurdert),
        )
    }

    fun harValgtFormue(): Boolean = this.containsKey(Revurderingsteg.Formue)
    fun harValgtUtenlandsopphold(): Boolean = this.containsKey(Revurderingsteg.Utenlandsopphold)

    companion object {

        fun opprettUtenVurderinger(
            sakstype: Sakstype,
            revurderingsteg: List<Revurderingsteg>,
        ): InformasjonSomRevurderes {
            return opprettUtenVurderingerMedFeilmelding(
                sakstype,
                revurderingsteg,
            ).getOrElse { throw IllegalArgumentException(it.toString()) }
        }

        fun opprettUtenVurderingerMedFeilmelding(
            sakstype: Sakstype,
            revurderingsteg: List<Revurderingsteg>,
        ): Either<UgyldigForRevurdering, InformasjonSomRevurderes> {
            val ugyldigForRevurdering = ugyldigForRevurdering(sakstype, revurderingsteg)
            if (ugyldigForRevurdering != null) return ugyldigForRevurdering.left()
            return InformasjonSomRevurderes(revurderingsteg.keysToMap { Vurderingstatus.IkkeVurdert }).right()
        }

        fun opprettMedVurderinger(
            sakstype: Sakstype,
            revurderingsteg: Map<Revurderingsteg, Vurderingstatus>,
        ): InformasjonSomRevurderes {
            return opprettMedVurderingerOgFeilmelding(
                sakstype,
                revurderingsteg,
            ).getOrElse { throw IllegalArgumentException(it.toString()) }
        }

        private fun opprettMedVurderingerOgFeilmelding(
            sakstype: Sakstype,
            revurderingsteg: Map<Revurderingsteg, Vurderingstatus>,
        ): Either<UgyldigForRevurdering, InformasjonSomRevurderes> {
            val ugyldigForRevurdering = ugyldigForRevurdering(sakstype, revurderingsteg.keys.toList())
            if (ugyldigForRevurdering != null) return ugyldigForRevurdering.left()
            return InformasjonSomRevurderes(revurderingsteg).right()
        }

        private fun ugyldigForRevurdering(
            sakstype: Sakstype,
            revurderingsteg: List<Revurderingsteg>,
        ): UgyldigForRevurdering? {
            if (revurderingsteg.isEmpty()) return MåRevurdereMinstEnTing
            when (sakstype) {
                Sakstype.ALDER -> {
                    if (revurderingsteg.contains(Revurderingsteg.Uførhet)) return UførhetErUgyldigForAlder
                    if (revurderingsteg.contains(Revurderingsteg.Flyktning)) return FlyktningErUgyldigForAlder
                }

                Sakstype.UFØRE -> {
                    if (revurderingsteg.contains(Revurderingsteg.Familiegjenforening)) return FamiliegjenforeningErUgyldigForUføre
                    if (revurderingsteg.contains(Revurderingsteg.Pensjon)) return PensjonErUgyldigForUføre
                }
            }
            return null
        }
    }

    sealed interface UgyldigForRevurdering
    data object MåRevurdereMinstEnTing : UgyldigForRevurdering
    data object UførhetErUgyldigForAlder : UgyldigForRevurdering
    data object FlyktningErUgyldigForAlder : UgyldigForRevurdering
    data object FamiliegjenforeningErUgyldigForUføre : UgyldigForRevurdering
    data object PensjonErUgyldigForUføre : UgyldigForRevurdering
}
