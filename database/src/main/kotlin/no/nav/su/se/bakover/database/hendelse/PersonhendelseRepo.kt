package no.nav.su.se.bakover.database.hendelse

import no.nav.su.se.bakover.domain.hendelse.Personhendelse

interface PersonhendelseRepo {
    fun lagre(personhendelse: Personhendelse.TilknyttetSak)
    fun hentPersonhendelserUtenOppgave(): List<Personhendelse.TilknyttetSak.IkkeSendtTilOppgave>
}
