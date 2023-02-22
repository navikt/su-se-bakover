package no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.person.Person
import java.time.Clock
import java.time.LocalDate
import java.time.Year

sealed interface Aldersvurdering {
    val stønadsperiode: Stønadsperiode

    /**
     * Brukes ved avslag pga manglende dokumentasjon dersom det ikke finnes en eksisterende alersvurdering på det tidspunktet
     */
    data class SkalIkkeVurderes(override val stønadsperiode: Stønadsperiode) : Aldersvurdering

    /**
     * Historiske søknadsbehandlinger som ikke har fått en vurdering
     * Er implisitt innvilget
     */
    data class Historisk(override val stønadsperiode: Stønadsperiode) : Aldersvurdering

    data class Vurdert(
        val maskinellVurdering: MaskinellAldersvurderingMedGrunnlagsdata,
        val saksbehandlersAvgjørelse: SaksbehandlersAvgjørelse?,
        val aldersinformasjon: Aldersinformasjon,
    ) : Aldersvurdering {
        val fødselsdato: LocalDate? get() = maskinellVurdering.fødselsdato
        val fødselsår: Year? get() = maskinellVurdering.fødselsår
        override val stønadsperiode: Stønadsperiode get() = maskinellVurdering.stønadsperiode

        companion object {
            fun vurder(
                stønadsperiode: Stønadsperiode,
                person: Person,
                saksbehandlersAvgjørelse: SaksbehandlersAvgjørelse?,
                clock: Clock,
            ): Either<Vurdert, Vurdert> {
                return Vurdert(
                    maskinellVurdering = MaskinellAldersvurderingMedGrunnlagsdata.avgjørBasertPåFødselsdatoEllerFødselsår(
                        stønadsperiode = stønadsperiode,
                        fødsel = person.fødsel,
                    ),
                    saksbehandlersAvgjørelse = saksbehandlersAvgjørelse,
                    aldersinformasjon = Aldersinformasjon.createAldersinformasjon(person, clock),
                ).let {
                    when (it.maskinellVurdering) {
                        is MaskinellAldersvurderingMedGrunnlagsdata.RettPåUføre -> {
                            it.also {
                                if (saksbehandlersAvgjørelse is SaksbehandlersAvgjørelse.Avgjort) {
                                    throw IllegalArgumentException("Saksbehandler kan ikke ta en avgjørelse på en maskinell vurdering som gir rett på uføre")
                                }
                            }.right()
                        }

                        is MaskinellAldersvurderingMedGrunnlagsdata.IkkeRettPåUføre,
                        is MaskinellAldersvurderingMedGrunnlagsdata.Ukjent,
                        -> {
                            if (saksbehandlersAvgjørelse is SaksbehandlersAvgjørelse.Avgjort) {
                                it.right()
                            } else {
                                it.left()
                            }
                        }
                    }
                }
            }
        }
    }
}
