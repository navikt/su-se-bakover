package no.nav.su.se.bakover.domain.hendelse

interface PersonhendelseRepo {
    fun lagre(personhendelse: Personhendelse.TilknyttetSak.SendtTilOppgave)
    fun lagre(personhendelse: Personhendelse.TilknyttetSak.IkkeSendtTilOppgave)
    fun hentPersonhendelserUtenOppgave(): List<Personhendelse.TilknyttetSak.IkkeSendtTilOppgave>
    fun inkrementerAntallFeiledeForsøk(personhendelse: Personhendelse.TilknyttetSak)
}
