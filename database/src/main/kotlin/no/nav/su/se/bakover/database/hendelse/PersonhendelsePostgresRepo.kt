package no.nav.su.se.bakover.database.hendelse

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.hendelse.PersonhendelsePostgresRepo.HendelseJson.Companion.toJson
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.hendelse.Personhendelse
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource

class PersonhendelsePostgresRepo(private val datasource: DataSource) : PersonhendelseRepo {

    override fun lagre(personhendelse: Personhendelse.Ny, id: UUID, sakId: UUID) {
        val tidspunkt = Tidspunkt.now()
        datasource.withSession { session ->
            """
                insert into personhendelse (id, sakId, hendelseId, meldingoffset, opprettet, endret, aktørId, endringstype, hendelse, oppgaveId, type)
                values(
                    :id,
                    :sakId,
                    :hendelseId,
                    :offset,
                    :opprettet,
                    :endret,
                    :aktoerId,
                    :endringstype,
                    to_jsonb(:hendelse::jsonb),
                    :oppgaveId,
                    :type
                )
                on conflict do nothing
            """.trimIndent().insert(
                mapOf(
                    "id" to id,
                    "sakId" to sakId,
                    "hendelseId" to personhendelse.hendelseId,
                    "offset" to personhendelse.offset,
                    "opprettet" to tidspunkt,
                    "endret" to tidspunkt,
                    "aktoerId" to personhendelse.gjeldendeAktørId.toString(),
                    "endringstype" to personhendelse.endringstype.value,
                    "hendelse" to objectMapper.writeValueAsString(personhendelse.hendelse.toJson()),
                    "oppgaveId" to null,
                    "type" to personhendelse.hendelse.type(),
                ),
                session,
            )
        }
    }

    internal fun hent(id: UUID): Personhendelse.TilknyttetSak? = datasource.withSession { session ->
        """
        select p.*, s.saksnummer as saksnummer from personhendelse p
        left join sak s on s.id = p.sakId
        where p.id = :id
        """.trimIndent()
            .hent(
                mapOf(
                    "id" to id,
                ),
                session,
            ) {
                Personhendelse.TilknyttetSak(
                    id = id,
                    sakId = it.uuid("sakId"),
                    gjeldendeAktørId = AktørId(it.string("aktørId")),
                    endringstype = Personhendelse.Endringstype.valueOf(it.string("endringstype")),
                    hendelse = it.hentHendelse(),
                    saksnummer = Saksnummer(it.long("saksnummer")),
                    oppgaveId = it.stringOrNull("oppgaveId")?.let { id -> OppgaveId(id) },
                )
            }
    }

    private fun Row.hentHendelse(): Personhendelse.Hendelse = when (val type = string("type")) {
        PersonhendelseType.DØDSFALL.value -> {
            objectMapper.readValue<HendelseJson.DødsfallJson>(string("hendelse")).toDomain()
        }
        PersonhendelseType.UTFLYTTING_FRA_NORGE.value -> {
            objectMapper.readValue<HendelseJson.UtflyttingFraNorgeJson>(string("hendelse")).toDomain()
        }
        else -> throw RuntimeException("Kunne ikke deserialisere [Personhendelse]. Ukjent type: $type")
    }

    // TODO jah: Denne er litt premature. Trengs ikke før neste PR.
    // override fun oppdaterOppgave(hendelseId: String, oppgaveId: OppgaveId) {
    //     datasource.withSession { session ->
    //         """
    //             update personhendelse set oppgaveId=:oppgaveId, endret=:endret where id=:hendelseId
    //         """.trimIndent().oppdatering(
    //             mapOf(
    //                 "hendelseId" to hendelseId,
    //                 "endret" to LocalDate.now(),
    //                 "oppgaveId" to oppgaveId.toString()
    //             ),
    //             session
    //         )
    //     }
    // }

    private fun Personhendelse.Hendelse.type(): String = when (this) {
        is Personhendelse.Hendelse.Dødsfall -> PersonhendelseType.DØDSFALL.value
        is Personhendelse.Hendelse.UtflyttingFraNorge -> PersonhendelseType.UTFLYTTING_FRA_NORGE.value
    }

    private enum class PersonhendelseType(val value: String) {
        DØDSFALL("dødsfall"),
        UTFLYTTING_FRA_NORGE("utflytting_fra_norge");
    }

    /**
     * Dto som persisteres som JSON i databasen. Tilbyr mapping til/fra domenetypen.
     */
    private sealed class HendelseJson {
        data class DødsfallJson(val dødsdato: LocalDate?) : HendelseJson()
        data class UtflyttingFraNorgeJson(val utflyttingsdato: LocalDate?) : HendelseJson()

        fun toDomain(): Personhendelse.Hendelse = when (this) {
            is DødsfallJson -> Personhendelse.Hendelse.Dødsfall(dødsdato)
            is UtflyttingFraNorgeJson -> Personhendelse.Hendelse.UtflyttingFraNorge(utflyttingsdato)
        }

        companion object {
            fun Personhendelse.Hendelse.toJson(): HendelseJson = when (this) {
                is Personhendelse.Hendelse.Dødsfall -> DødsfallJson(dødsdato)
                is Personhendelse.Hendelse.UtflyttingFraNorge -> UtflyttingFraNorgeJson(utflyttingsdato)
            }
        }
    }
}
