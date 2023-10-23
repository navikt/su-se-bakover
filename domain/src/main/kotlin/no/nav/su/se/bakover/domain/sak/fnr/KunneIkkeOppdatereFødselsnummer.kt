package no.nav.su.se.bakover.domain.sak.fnr

sealed interface KunneIkkeOppdatereFødselsnummer {
    data object SakHarAlleredeSisteFødselsnummer : KunneIkkeOppdatereFødselsnummer
}
