package no.nav.su.se.bakover.database.hendelse

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.hendelse.Personhendelse
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.time.LocalDate
import javax.sql.DataSource

class HendelsePostgresRepo(private val datasource: DataSource) : HendelseRepo {
    private enum class PersonhendelseType(val value: String) {
        DØDSFALL("dødsfall"),
        UTFLYTTING_FRA_NORGE("utflytting_fra_norge");
    }

    override fun lagre(personhendelse: Personhendelse.Ny, saksnummer: Saksnummer) {
        val tidspunkt = Tidspunkt.now()
        datasource.withSession { session ->
            """
                insert into personhendelse (id, meldingoffset, opprettet, endret, aktørId, endringstype, saksnummer, hendelse, oppgaveId, type)
                values(
                    :id,
                    :offset,
                    :opprettet,
                    :endret,
                    :aktoerId,
                    :endringstype,
                    :saksnummer,
                    to_jsonb(:hendelse::jsonb),
                    :oppgaveId,
                    :type
                )
                on conflict do nothing
            """.trimIndent().insert(
                mapOf(
                    "id" to personhendelse.hendelseId,
                    "offset" to personhendelse.offset,
                    "opprettet" to tidspunkt,
                    "endret" to tidspunkt,
                    "aktoerId" to personhendelse.gjeldendeAktørId.toString(),
                    "endringstype" to personhendelse.endringstype.value,
                    "saksnummer" to saksnummer.nummer,
                    "hendelse" to when (personhendelse.hendelse) {
                        is Personhendelse.Hendelse.Dødsfall -> objectMapper.writeValueAsString(personhendelse.hendelse)
                        is Personhendelse.Hendelse.UtflyttingFraNorge -> objectMapper.writeValueAsString(personhendelse.hendelse)
                    },
                    "oppgaveId" to null,
                    "type" to personhendelse.hendelse.type(),
                ),
                session,
            )
        }
    }

    override fun hent(hendelseId: String): Personhendelse.Persistert? = datasource.withSession { session ->
        """
        select * from personhendelse where id = :hendelseId
        """.trimIndent()
            .hent(
                mapOf(
                    "hendelseId" to hendelseId,
                ),
                session,
            ) {
                Personhendelse.Persistert(
                    hendelseId = it.string("id"),
                    gjeldendeAktørId = AktørId(it.string("aktørId")),
                    endringstype = Personhendelse.Endringstype.valueOf(it.string("endringstype")),
                    hendelse = it.hentHendelse(),
                    saksnummer = Saksnummer(it.long("saksnummer")),
                    oppgaveId = it.stringOrNull("oppgaveId")?.let { id -> OppgaveId(id) },
                )
            }
    }

    override fun oppdaterOppgave(hendelseId: String, oppgaveId: OppgaveId) {
        datasource.withSession { session ->
            """
                update personhendelse set oppgaveId=:oppgaveId, endret=:endret where id=:hendelseId
            """.trimIndent().oppdatering(
                mapOf(
                    "hendelseId" to hendelseId,
                    "endret" to LocalDate.now(),
                    "oppgaveId" to oppgaveId.toString()
                ),
                session
            )
        }
    }

    private fun Row.hentHendelse(): Personhendelse.Hendelse = when (string("type")) {
        PersonhendelseType.DØDSFALL.value -> {
            objectMapper.readValue<Personhendelse.Hendelse.Dødsfall>(string("hendelse"))
        }
        PersonhendelseType.UTFLYTTING_FRA_NORGE.value -> {
            objectMapper.readValue<Personhendelse.Hendelse.UtflyttingFraNorge>(string("hendelse"))
        }
        else -> throw RuntimeException("Feil skjedde ved deserialisering av personhendelse")
    }

    private fun Personhendelse.Hendelse.type(): String = when (this) {
        is Personhendelse.Hendelse.Dødsfall -> PersonhendelseType.DØDSFALL.value
        is Personhendelse.Hendelse.UtflyttingFraNorge -> PersonhendelseType.UTFLYTTING_FRA_NORGE.value
    }
}
