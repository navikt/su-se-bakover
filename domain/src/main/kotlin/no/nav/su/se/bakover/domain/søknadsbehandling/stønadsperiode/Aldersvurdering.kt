package no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode

import no.nav.su.se.bakover.domain.person.Person
import java.time.Clock
import java.time.LocalDate
import java.time.Year


sealed interface Aldersvurdering {
    val stønadsperiode: Stønadsperiode

    /**
     * Brukes ved avslag pga manglende dokumentasjon
     */
    data class SkalIkkeVurderes(override val stønadsperiode: Stønadsperiode) : Aldersvurdering

    /**
     * Historiske søknadsbehandlinger som ikke har fått en vurdering
     * Er implisitt innvilget
     */
    data class Historisk(override val stønadsperiode: Stønadsperiode) : Aldersvurdering

    data class Vurdert(
        val maskinellVurdering: MaskinellAldersvurderingMedGrunnlagsdata,
        val saksbehandlersAvgjørelse: SaksbehandlersAvgjørelse,
        val aldersinformasjon: Aldersinformasjon,
    ) : Aldersvurdering {
        val fødselsdato: LocalDate? get() = maskinellVurdering.fødselsdato
        val fødselsår: Year? get() = maskinellVurdering.fødselsår
        override val stønadsperiode: Stønadsperiode get() = maskinellVurdering.stønadsperiode

        companion object {
            fun vurder(
                stønadsperiode: Stønadsperiode,
                person: Person,
                saksbehandlersAvgjørelse: SaksbehandlersAvgjørelse,
                clock: Clock,
            ): Vurdert {
                return Vurdert(
                    maskinellVurdering = MaskinellAldersvurderingMedGrunnlagsdata.avgjørBasertPåFødselsdatoEllerFødselsår(
                        stønadsperiode = stønadsperiode,
                        fødsel = person.fødsel,
                    ),
                    saksbehandlersAvgjørelse = saksbehandlersAvgjørelse,
                    aldersinformasjon = Aldersinformasjon.createAldersinformasjon(person, clock),
                )
            }
        }
    }
}
