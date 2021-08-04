package no.nav.su.se.bakover.database.hendelse

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.hendelse.PdlHendelse
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import javax.sql.DataSource

class HendelsePostgresRepo(private val datasource: DataSource) : HendelseRepo {
    private enum class PersonhendelseType(val value: String) {
        DØDSFALL("dødsfall"),
        UTFLYTTING_FRA_NORGE("utflytting_fra_norge");
    }

    override fun lagre(pdlHendelse: PdlHendelse, saksnummer: Saksnummer, melding: String) {
        val tidspunkt = Tidspunkt.now()
        datasource.withSession { session ->
            """
                insert into personhendelse(id, opprettet, endret, saksnummer, hendelse, meldingJson, oppgaveId)
                values(
                    :opprettet,
                    :endret,
                    :saksnummer,
                    :hendelse,
                    :meldingJson,
                    :oppgaveId
                )
                on conflict do nothing
            """.trimIndent().insert(
                mapOf(
                    "opprettet" to tidspunkt,
                    "endret" to tidspunkt,
                    "saksnummer" to saksnummer,
                    "hendelse" to objectMapper.writeValueAsString(pdlHendelse),
                    "meldingJson" to melding,
                    "oppgaveId" to null,
                ),
                session,
            )
        }
    }

    override fun hent(hendelseId: String) {
        datasource.withSession { session ->
            """
            select * from personhendelse where id = :hendelseId
            """.trimIndent()
                .hent(
                    mapOf(
                        "hendelseId" to hendelseId,
                    ),
                    session,
                ) {
                    it.toPdlHendelse()
                }
        }
    }

    override fun oppdaterOppgave(hendelseId: String, oppgaveId: OppgaveId) {}

    private fun Row.toPdlHendelse(): PdlHendelse = when (string("type")) {
        PersonhendelseType.DØDSFALL.value -> {
            objectMapper.readValue<PdlHendelse.Dødsfall>(string("hendelse"))
        }
        PersonhendelseType.UTFLYTTING_FRA_NORGE.value -> {
            objectMapper.readValue<PdlHendelse.UtflyttingFraNorge>(string("hendelse"))
        }
        else -> throw RuntimeException("Feil skjedde ved deserialisering av personhendelse")
    }
}

