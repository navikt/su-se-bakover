package no.nav.su.se.bakover.web.services.PdlHendelser

interface LeesahService {
    enum class Opplysningstype(val value: String) {
        DØDSFALL("DOEDSFALL_V1"),
        UTFLYTTING_FRA_NORGE("UTFLYTTING_FRA_NORGE")
    }

    fun prosesserNyMelding(pdlHendelse: PdlHendelse)
}

sealed class KunneIkkeLageRevurderingsoppgave {
    object FantIkkeSak : KunneIkkeLageRevurderingsoppgave()
    object KunneIkkeHentePerson : KunneIkkeLageRevurderingsoppgave()
    object KallMotOppgaveFeilet : KunneIkkeLageRevurderingsoppgave()
}
