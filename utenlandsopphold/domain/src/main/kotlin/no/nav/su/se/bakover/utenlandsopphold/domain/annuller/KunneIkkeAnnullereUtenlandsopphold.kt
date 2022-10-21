package no.nav.su.se.bakover.utenlandsopphold.domain.annuller

sealed interface KunneIkkeAnnullereUtenlandsopphold {
    object UtdatertSaksversjon : KunneIkkeAnnullereUtenlandsopphold
}
