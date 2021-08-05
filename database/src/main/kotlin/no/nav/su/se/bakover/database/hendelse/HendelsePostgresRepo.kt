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
import no.nav.su.se.bakover.domain.hendelse.PdlHendelse
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.time.LocalDate
import javax.sql.DataSource

class HendelsePostgresRepo(private val datasource: DataSource) : HendelseRepo {
    private enum class PersonhendelseType(val value: String) {
        DØDSFALL("dødsfall"),
        UTFLYTTING_FRA_NORGE("utflytting_fra_norge");
    }

    override fun lagre(pdlHendelse: PdlHendelse.Ny, saksnummer: Saksnummer) {
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
                    "id" to pdlHendelse.hendelseId,
                    "offset" to pdlHendelse.offset,
                    "opprettet" to tidspunkt,
                    "endret" to tidspunkt,
                    "aktoerId" to pdlHendelse.gjeldendeAktørId.toString(),
                    "endringstype" to pdlHendelse.endringstype.value,
                    "saksnummer" to saksnummer.nummer,
                    "hendelse" to when (pdlHendelse.hendelse) {
                        is PdlHendelse.Hendelse.Dødsfall -> objectMapper.writeValueAsString(pdlHendelse.hendelse)
                        is PdlHendelse.Hendelse.UtflyttingFraNorge -> objectMapper.writeValueAsString(pdlHendelse.hendelse)
                    },
                    "oppgaveId" to null,
                    "type" to pdlHendelse.hendelse.type()
                ),
                session,
            )
        }
    }

    override fun hent(hendelseId: String): PdlHendelse.Persistert? = datasource.withSession { session ->
        """
        select * from personhendelse where id = :hendelseId
        """.trimIndent()
            .hent(
                mapOf(
                    "hendelseId" to hendelseId,
                ),
                session,
            ) {
                PdlHendelse.Persistert(
                    hendelseId = it.string("id"),
                    gjeldendeAktørId = AktørId(it.string("aktørId")),
                    endringstype = PdlHendelse.Endringstype.valueOf(it.string("endringstype")),
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

    private fun Row.hentHendelse(): PdlHendelse.Hendelse = when (string("type")) {
        PersonhendelseType.DØDSFALL.value -> {
            objectMapper.readValue<PdlHendelse.Hendelse.Dødsfall>(string("hendelse"))
        }
        PersonhendelseType.UTFLYTTING_FRA_NORGE.value -> {
            objectMapper.readValue<PdlHendelse.Hendelse.UtflyttingFraNorge>(string("hendelse"))
        }
        else -> throw RuntimeException("Feil skjedde ved deserialisering av personhendelse")
    }

    private fun PdlHendelse.Hendelse.type(): String = when (this) {
        is PdlHendelse.Hendelse.Dødsfall -> PersonhendelseType.DØDSFALL.value
        is PdlHendelse.Hendelse.UtflyttingFraNorge -> PersonhendelseType.UTFLYTTING_FRA_NORGE.value
    }
}
