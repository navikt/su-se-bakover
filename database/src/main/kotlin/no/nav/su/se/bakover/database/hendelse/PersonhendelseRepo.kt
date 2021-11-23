package no.nav.su.se.bakover.database.hendelse

import no.nav.su.se.bakover.domain.hendelse.Personhendelse

interface PersonhendelseRepo {
    fun lagre(personhendelse: Personhendelse.TilknyttetSak.SendtTilOppgave)
    fun lagre(personhendelse: Personhendelse.TilknyttetSak.IkkeSendtTilOppgave)
    fun hentPersonhendelserUtenOppgave(): List<Personhendelse.TilknyttetSak.IkkeSendtTilOppgave>
    fun inkrementerAntallFeiledeForsøk(personhendelse: Personhendelse.TilknyttetSak)
}
