package no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode

import no.nav.su.se.bakover.domain.person.Person

sealed interface VurdertStønadsperiodeOppMotPersonsAlder {

    companion object {
        fun vurder(
            stønadsperiode: Stønadsperiode,
            person: Person,
        ): VurdertStønadsperiodeOppMotPersonsAlder {
            return when (val av = person.aldersvilkår(stønadsperiode.periode)) {
                is Aldersvilkår.RettPåAlder -> SøkerErForGammel(av)
                is Aldersvilkår.Ukjent -> RettPåUføre.SaksbehandlerMåKontrollereManuelt(stønadsperiode, av)
                is Aldersvilkår.RettPåUføre -> RettPåUføre.KontrollertAutomatisk(stønadsperiode, av)
            }
        }
    }

    sealed interface RettPåUføre : VurdertStønadsperiodeOppMotPersonsAlder {
        val stønadsperiode: Stønadsperiode

        data class KontrollertAutomatisk(
            override val stønadsperiode: Stønadsperiode,
            val vilkår: Aldersvilkår.RettPåUføre,
        ) : RettPåUføre

        data class SaksbehandlerMåKontrollereManuelt(
            override val stønadsperiode: Stønadsperiode,
            val vilkår: Aldersvilkår.Ukjent,
        ) : RettPåUføre
    }

    data class SøkerErForGammel(
        val vilkår: Aldersvilkår.RettPåAlder,
    ) : VurdertStønadsperiodeOppMotPersonsAlder
}
