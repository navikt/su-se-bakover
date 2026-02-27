package no.nav.su.se.bakover.domain.personhendelse

import java.util.UUID

interface PersonhendelseRepo {
    data class PdlVurdering(
        val id: UUID,
        val relevant: Boolean,
        val pdlSnapshot: String?,
        val pdlDiff: String?,
    )

    fun lagre(personhendelse: List<Personhendelse.TilknyttetSak.SendtTilOppgave>)
    fun lagre(personhendelse: Personhendelse.TilknyttetSak.IkkeSendtTilOppgave)
    fun hentPersonhendelserUtenOppgave(): List<Personhendelse.TilknyttetSak.IkkeSendtTilOppgave>
    fun hentPersonhendelserUtenPdlVurdering(): List<Personhendelse.TilknyttetSak.IkkeSendtTilOppgave>
    fun hentPersonhendelserKlareForOppgave(): List<Personhendelse.TilknyttetSak.IkkeSendtTilOppgave>
    fun oppdaterPdlVurdering(vurderinger: List<PdlVurdering>)
    fun inkrementerAntallFeiledeForsøk(personhendelse: List<Personhendelse.TilknyttetSak>)
}
