package no.nav.su.se.bakover.utenlandsopphold.domain.annuller

sealed interface KunneIkkeAnnullereUtenlandsopphold {
    data object UtdatertSaksversjon : KunneIkkeAnnullereUtenlandsopphold
}
