package no.nav.su.se.bakover.utenlandsopphold.domain.registrer

sealed interface KunneIkkeRegistereUtenlandsopphold {
    object OverlappendePeriode : KunneIkkeRegistereUtenlandsopphold
    object UtdatertSaksversjon : KunneIkkeRegistereUtenlandsopphold
}
