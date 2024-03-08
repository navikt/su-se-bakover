package vilkår.utenlandsopphold.domain.annuller

sealed interface KunneIkkeAnnullereUtenlandsopphold {
    data object UtdatertSaksversjon : KunneIkkeAnnullereUtenlandsopphold
}
