package vilkår.personligoppmøte.domain

import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import vilkår.common.domain.Vurdering
import vilkår.common.domain.grunnlag.Grunnlag
import java.util.UUID

sealed interface PersonligOppmøteÅrsak {
    fun tilVurdering(): Vurdering

    data object MøttPersonlig : PersonligOppmøteÅrsak {
        override fun tilVurdering(): Vurdering {
            return Vurdering.Innvilget
        }
    }

    data object IkkeMøttMenVerge : PersonligOppmøteÅrsak {
        override fun tilVurdering(): Vurdering {
            return Vurdering.Innvilget
        }
    }

    data object IkkeMøttMenSykMedLegeerklæringOgFullmakt : PersonligOppmøteÅrsak {
        override fun tilVurdering(): Vurdering {
            return Vurdering.Innvilget
        }
    }

    data object IkkeMøttMenKortvarigSykMedLegeerklæring : PersonligOppmøteÅrsak {
        override fun tilVurdering(): Vurdering {
            return Vurdering.Innvilget
        }
    }

    data object IkkeMøttMenMidlertidigUnntakFraOppmøteplikt : PersonligOppmøteÅrsak {
        override fun tilVurdering(): Vurdering {
            return Vurdering.Innvilget
        }
    }

    data object IkkeMøttPersonlig : PersonligOppmøteÅrsak {
        override fun tilVurdering(): Vurdering {
            return Vurdering.Avslag
        }
    }

    data object Uavklart : PersonligOppmøteÅrsak {
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
) : Grunnlag,
    KanPlasseresPåTidslinje<PersonligOppmøteGrunnlag> {

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
    }

    override fun erLik(other: Grunnlag): Boolean {
        return other is PersonligOppmøteGrunnlag && årsak == other.årsak
    }

    override fun copyWithNewId(): PersonligOppmøteGrunnlag = this.copy(id = UUID.randomUUID())
}
