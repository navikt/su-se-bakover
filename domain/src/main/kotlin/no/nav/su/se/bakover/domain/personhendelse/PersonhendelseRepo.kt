package no.nav.su.se.bakover.domain.personhendelse

interface PersonhendelseRepo {
    fun lagre(personhendelse: List<Personhendelse.TilknyttetSak.SendtTilOppgave>)
    fun lagre(personhendelse: Personhendelse.TilknyttetSak.IkkeSendtTilOppgave)
    fun hentPersonhendelserUtenOppgave(): List<Personhendelse.TilknyttetSak.IkkeSendtTilOppgave>
    fun inkrementerAntallFeiledeForsøk(personhendelse: List<Personhendelse.TilknyttetSak>)
}
