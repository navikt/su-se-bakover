package no.nav.su.se.bakover.domain.grunnlag

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.domain.vilkår.Vurdering
import java.util.UUID

sealed class PersonligOppmøteÅrsak {
    abstract fun tilVurdering(): Vurdering

    object MøttPersonlig : PersonligOppmøteÅrsak() {
        override fun tilVurdering(): Vurdering {
            return Vurdering.Innvilget
        }
    }

    object IkkeMøttMenVerge : PersonligOppmøteÅrsak() {
        override fun tilVurdering(): Vurdering {
            return Vurdering.Innvilget
        }
    }

    object IkkeMøttMenSykMedLegeerklæringOgFullmakt : PersonligOppmøteÅrsak() {
        override fun tilVurdering(): Vurdering {
            return Vurdering.Innvilget
        }
    }

    object IkkeMøttMenKortvarigSykMedLegeerklæring : PersonligOppmøteÅrsak() {
        override fun tilVurdering(): Vurdering {
            return Vurdering.Innvilget
        }
    }

    object IkkeMøttMenMidlertidigUnntakFraOppmøteplikt : PersonligOppmøteÅrsak() {
        override fun tilVurdering(): Vurdering {
            return Vurdering.Innvilget
        }
    }

    object IkkeMøttPersonlig : PersonligOppmøteÅrsak() {
        override fun tilVurdering(): Vurdering {
            return Vurdering.Avslag
        }
    }

    object Uavklart : PersonligOppmøteÅrsak() {
        override fun tilVurdering(): Vurdering {
            return Vurdering.Uavklart
        }
    }
}

data class PersonligOppmøteGrunnlag(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Tidspunkt,
    override val periode: Periode,
    val årsak: PersonligOppmøteÅrsak,
) : Grunnlag(), KanPlasseresPåTidslinje<PersonligOppmøteGrunnlag> {

    fun vurdering(): Vurdering {
        return årsak.tilVurdering()
    }

    fun oppdaterPeriode(periode: Periode): PersonligOppmøteGrunnlag {
        return copy(periode = periode)
    }

    override fun copy(args: CopyArgs.Tidslinje): PersonligOppmøteGrunnlag = when (args) {
        CopyArgs.Tidslinje.Full -> {
            copy(id = UUID.randomUUID())
        }
        is CopyArgs.Tidslinje.NyPeriode -> {
            copy(id = UUID.randomUUID(), periode = args.periode)
        }
        is CopyArgs.Tidslinje.Maskert -> {
            copy(args.args).copy(opprettet = opprettet.plusUnits(1))
        }
    }

    override fun erLik(other: Grunnlag): Boolean {
        return other is PersonligOppmøteGrunnlag && årsak == other.årsak
    }
}
