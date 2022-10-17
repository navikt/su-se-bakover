package no.nav.su.se.bakover.utenlandsopphold.domain.korriger

sealed interface KunneIkkeKorrigereUtenlandsopphold {
    object OverlappendePeriode : KunneIkkeKorrigereUtenlandsopphold
    object UtdatertSaksversjon : KunneIkkeKorrigereUtenlandsopphold
}
