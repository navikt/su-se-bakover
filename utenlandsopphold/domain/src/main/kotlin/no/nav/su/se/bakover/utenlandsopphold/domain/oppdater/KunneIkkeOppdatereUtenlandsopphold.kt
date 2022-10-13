package no.nav.su.se.bakover.utenlandsopphold.domain.oppdater

sealed interface KunneIkkeOppdatereUtenlandsopphold {
    object OverlappendePeriode : KunneIkkeOppdatereUtenlandsopphold
    object UtdatertSaksversjon : KunneIkkeOppdatereUtenlandsopphold
}
