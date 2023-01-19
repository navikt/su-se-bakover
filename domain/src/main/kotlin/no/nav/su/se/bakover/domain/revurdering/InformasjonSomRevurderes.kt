package no.nav.su.se.bakover.domain.revurdering

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import org.jetbrains.kotlin.utils.keysToMap

data class InformasjonSomRevurderes private constructor(
    val informasjonSomRevurderes: Map<Revurderingsteg, Vurderingstatus>,
) : Map<Revurderingsteg, Vurderingstatus> by informasjonSomRevurderes {

    fun markerSomVurdert(revurderingsteg: Revurderingsteg): InformasjonSomRevurderes {
        return copy(
            informasjonSomRevurderes = informasjonSomRevurderes + mapOf(revurderingsteg to Vurderingstatus.Vurdert),
        )
    }

    fun markerSomIkkeVurdert(revurderingsteg: Revurderingsteg): InformasjonSomRevurderes {
        return copy(
            informasjonSomRevurderes = informasjonSomRevurderes + mapOf(revurderingsteg to Vurderingstatus.IkkeVurdert),
        )
    }

    fun harValgtBosituasjon(): Boolean = this.containsKey(Revurderingsteg.Bosituasjon)
    fun harValgtInntekt(): Boolean = this.containsKey(Revurderingsteg.Inntekt)
    fun harValgtFormue(): Boolean = this.containsKey(Revurderingsteg.Formue)
    fun harValgtUtenlandsopphold(): Boolean = this.containsKey(Revurderingsteg.Utenlandsopphold)

    companion object {
        fun create(revurderingsteg: List<Revurderingsteg>): InformasjonSomRevurderes {
            return tryCreate(revurderingsteg).getOrElse { throw IllegalArgumentException(it.toString()) }
        }

        fun tryCreate(revurderingsteg: List<Revurderingsteg>): Either<MåRevurdereMinstEnTing, InformasjonSomRevurderes> {
            if (revurderingsteg.isEmpty()) return MåRevurdereMinstEnTing.left()
            return InformasjonSomRevurderes(revurderingsteg.keysToMap { Vurderingstatus.IkkeVurdert }).right()
        }

        fun create(revurderingsteg: Map<Revurderingsteg, Vurderingstatus>): InformasjonSomRevurderes {
            return tryCreate(revurderingsteg).getOrElse { throw IllegalArgumentException(it.toString()) }
        }

        fun tryCreate(revurderingsteg: Map<Revurderingsteg, Vurderingstatus>): Either<MåRevurdereMinstEnTing, InformasjonSomRevurderes> {
            if (revurderingsteg.isEmpty()) return MåRevurdereMinstEnTing.left()
            return InformasjonSomRevurderes(revurderingsteg).right()
        }
    }

    object MåRevurdereMinstEnTing
}
