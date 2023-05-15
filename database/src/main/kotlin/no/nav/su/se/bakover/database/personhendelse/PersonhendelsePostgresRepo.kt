package no.nav.su.se.bakover.database.personhendelse

import kotliquery.Row
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.persistence.DbMetrics
import no.nav.su.se.bakover.common.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.persistence.hent
import no.nav.su.se.bakover.common.persistence.hentListe
import no.nav.su.se.bakover.common.persistence.insert
import no.nav.su.se.bakover.common.persistence.oppdatering
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.database.personhendelse.PersonhendelsePostgresRepo.HendelseJson.Companion.toJson
import no.nav.su.se.bakover.database.personhendelse.PersonhendelsePostgresRepo.MetadataJson.Companion.toJson
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.SivilstandTyper
import no.nav.su.se.bakover.domain.personhendelse.Personhendelse
import no.nav.su.se.bakover.domain.personhendelse.PersonhendelseRepo
import no.nav.su.se.bakover.domain.sak.Saksnummer
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

internal class PersonhendelsePostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
    private val clock: Clock,
) : PersonhendelseRepo {

    override fun lagre(personhendelse: Personhendelse.TilknyttetSak.IkkeSendtTilOppgave) {
        val tidspunkt = Tidspunkt.now(clock)
        dbMetrics.timeQuery("lagrePersonhendelseTilknyttetSakMenIkkeSendtTilOppgave") {
            sessionFactory.withSession { session ->
                """
                insert into personhendelse (id, sakId, opprettet, endret, endringstype, hendelse, oppgaveId, type, metadata)
                values(
                    :id,
                    :sakId,
                    :opprettet,
                    :endret,
                    :endringstype,
                    to_jsonb(:hendelse::jsonb),
                    :oppgaveId,
                    :type,
                    to_jsonb(:metadata::jsonb)
                )
                on conflict do nothing
                """.trimIndent().insert(
                    mapOf(
                        "id" to personhendelse.id,
                        "sakId" to personhendelse.sakId,
                        "opprettet" to tidspunkt,
                        "endret" to tidspunkt,
                        "endringstype" to personhendelse.endringstype.toDatabasetype(),
                        "hendelse" to serialize(personhendelse.hendelse.toJson()),
                        "oppgaveId" to null,
                        "type" to personhendelse.hendelse.toDatabasetype(),
                        "metadata" to serialize(personhendelse.metadata.toJson()),
                    ),
                    session,
                )
            }
        }
    }

    override fun lagre(personhendelse: Personhendelse.TilknyttetSak.SendtTilOppgave) {
        dbMetrics.timeQuery("lagrePersonhendelseTilknyttetSakOgSendtTilOppgave") {
            sessionFactory.withSession { session ->
                """
                update personhendelse set oppgaveId = :oppgaveId, endret = :endret where id = :id
                """.trimIndent().insert(
                    mapOf(
                        "id" to personhendelse.id,
                        "endret" to Tidspunkt.now(clock),
                        "oppgaveId" to personhendelse.oppgaveId,
                    ),
                    session,
                )
            }
        }
    }

    override fun hentPersonhendelserUtenOppgave(): List<Personhendelse.TilknyttetSak.IkkeSendtTilOppgave> {
        return dbMetrics.timeQuery("hentPersonhendelserUtenOppgave") {
            sessionFactory.withSession { session ->
                """
                    select
                        p.*, s.saksnummer as saksnummer
                    from
                        personhendelse p
                        left join sak s on s.id = p.sakId
                    where
                        oppgaveId is null and antallFeiledeForsøk < 3
                """.trimIndent().hentListe(mapOf(), session) { row ->
                    row.toIkkeSendtTilOppgave()
                }
            }
        }
    }

    override fun inkrementerAntallFeiledeForsøk(personhendelse: Personhendelse.TilknyttetSak) {
        dbMetrics.timeQuery("inkrementerAntallFeiledeForsøkForPersonhendelseTilknyttetSak") {
            sessionFactory.withSession { session ->
                """
                    update
                        personhendelse
                    set
                        antallFeiledeForsøk = antallFeiledeForsøk + 1
                    where
                        id = :id
                """.trimIndent().oppdatering(
                    mapOf(
                        "id" to personhendelse.id,
                    ),
                    session,
                )
            }
        }
    }

    internal fun hent(id: UUID): Personhendelse.TilknyttetSak? {
        return dbMetrics.timeQuery("hentPersonhendelseTilknyttetSak") {
            sessionFactory.withSession { session ->
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
                    ) { it.toPersonhendelse() }
            }
        }
    }

    private fun Row.toIkkeSendtTilOppgave() = Personhendelse.TilknyttetSak.IkkeSendtTilOppgave(
        id = UUID.fromString(string("id")),
        sakId = uuid("sakId"),
        endringstype = PersonhendelseEndringstype.tryParse(string("endringstype")).toDomain(),
        hendelse = hentHendelse(),
        saksnummer = Saksnummer(long("saksnummer")),
        metadata = deserialize<MetadataJson>(string("metadata")).toDomain(),
        antallFeiledeForsøk = int("antallFeiledeForsøk"),
    )

    private fun Row.toSendtTilOppgave(oppgaveId: OppgaveId) = Personhendelse.TilknyttetSak.SendtTilOppgave(
        id = UUID.fromString(string("id")),
        sakId = uuid("sakId"),
        endringstype = PersonhendelseEndringstype.tryParse(string("endringstype")).toDomain(),
        hendelse = hentHendelse(),
        saksnummer = Saksnummer(long("saksnummer")),
        metadata = deserialize<MetadataJson>(string("metadata")).toDomain(),
        oppgaveId = oppgaveId,
        antallFeiledeForsøk = int("antallFeiledeForsøk"),
    )

    private fun Row.toPersonhendelse(): Personhendelse.TilknyttetSak =
        when (val oppgaveId = stringOrNull("oppgaveId")) {
            null -> this.toIkkeSendtTilOppgave()
            else -> this.toSendtTilOppgave(OppgaveId(oppgaveId))
        }

    private fun Row.hentHendelse(): Personhendelse.Hendelse = when (val type = string("type")) {
        PersonhendelseType.DØDSFALL.value -> {
            deserialize<HendelseJson.DødsfallJson>(string("hendelse")).toDomain()
        }
        PersonhendelseType.UTFLYTTING_FRA_NORGE.value -> {
            deserialize<HendelseJson.UtflyttingFraNorgeJson>(string("hendelse")).toDomain()
        }
        PersonhendelseType.SIVILSTAND.value -> {
            deserialize<HendelseJson.SivilstandJson>(string("hendelse")).toDomain()
        }
        PersonhendelseType.BOSTEDSADRESSE.value -> Personhendelse.Hendelse.Bostedsadresse
        PersonhendelseType.KONTAKTADRESSE.value -> Personhendelse.Hendelse.Kontaktadresse
        else -> throw RuntimeException("Kunne ikke deserialisere [Personhendelse]. Ukjent type: $type")
    }

    private fun Personhendelse.Hendelse.toDatabasetype(): String = when (this) {
        is Personhendelse.Hendelse.Dødsfall -> PersonhendelseType.DØDSFALL.value
        is Personhendelse.Hendelse.UtflyttingFraNorge -> PersonhendelseType.UTFLYTTING_FRA_NORGE.value
        is Personhendelse.Hendelse.Sivilstand -> PersonhendelseType.SIVILSTAND.value
        is Personhendelse.Hendelse.Bostedsadresse -> PersonhendelseType.BOSTEDSADRESSE.value
        is Personhendelse.Hendelse.Kontaktadresse -> PersonhendelseType.KONTAKTADRESSE.value
    }

    private enum class PersonhendelseType(val value: String) {
        DØDSFALL("dødsfall"),
        UTFLYTTING_FRA_NORGE("utflytting_fra_norge"),
        SIVILSTAND("sivilstand"),
        BOSTEDSADRESSE("bostedsadresse"),
        KONTAKTADRESSE("kontaktadresse"),
    }

    private fun Personhendelse.Endringstype.toDatabasetype(): String = when (this) {
        Personhendelse.Endringstype.OPPRETTET -> PersonhendelseEndringstype.OPPRETTET.value
        Personhendelse.Endringstype.KORRIGERT -> PersonhendelseEndringstype.KORRIGERT.value
        Personhendelse.Endringstype.ANNULLERT -> PersonhendelseEndringstype.ANNULLERT.value
        Personhendelse.Endringstype.OPPHØRT -> PersonhendelseEndringstype.OPPHØRT.value
    }

    private enum class PersonhendelseEndringstype(val value: String) {
        OPPRETTET("opprettet"),
        KORRIGERT("korrigert"),
        ANNULLERT("annullert"),
        OPPHØRT("opphørt"),
        ;

        fun toDomain(): Personhendelse.Endringstype = when (this) {
            OPPRETTET -> Personhendelse.Endringstype.OPPRETTET
            KORRIGERT -> Personhendelse.Endringstype.KORRIGERT
            ANNULLERT -> Personhendelse.Endringstype.ANNULLERT
            OPPHØRT -> Personhendelse.Endringstype.OPPHØRT
        }

        companion object {
            fun tryParse(value: String): PersonhendelseEndringstype {
                return values()
                    .firstOrNull { it.value == value }
                    ?: throw IllegalStateException("Ukjent PersonhendelseEndringstype: $value")
            }
        }
    }

    /**
     * Dto som persisteres som JSON i databasen. Tilbyr mapping til/fra domenetypen.
     */
    private sealed class HendelseJson {
        data class DødsfallJson(val dødsdato: LocalDate?) : HendelseJson()
        data class UtflyttingFraNorgeJson(val utflyttingsdato: LocalDate?) : HendelseJson()
        data class SivilstandJson(
            val type: String?,
            val gyldigFraOgMed: LocalDate?,
            val relatertVedSivilstand: String?,
            val bekreftelsesdato: LocalDate?,
        ) : HendelseJson() {
            enum class Typer(val value: String) {
                UOPPGITT("uoppgitt"),
                UGIFT("ugift"),
                GIFT("gift"),
                ENKE_ELLER_ENKEMANN("enke_eller_enkemann"),
                SKILT("skilt"),
                SEPARERT("separert"),
                REGISTRERT_PARTNER("registrert_partner"),
                SEPARERT_PARTNER("separert_partner"),
                SKILT_PARTNER("skilt_partner"),
                GJENLEVENDE_PARTNER("gjenlevende_partner"),
                ;

                companion object {
                    fun tryParse(value: String): Typer {
                        return values()
                            .firstOrNull { it.value == value }
                            ?: throw IllegalStateException("Ukjent sivilstandtype: $value")
                    }
                }
            }
        }

        object BostedsadresseJson : HendelseJson()

        object KontaktadresseJson : HendelseJson()

        fun toDomain(): Personhendelse.Hendelse = when (this) {
            is DødsfallJson -> Personhendelse.Hendelse.Dødsfall(dødsdato)
            is UtflyttingFraNorgeJson -> Personhendelse.Hendelse.UtflyttingFraNorge(utflyttingsdato)
            is SivilstandJson -> Personhendelse.Hendelse.Sivilstand(
                type = type?.let {
                    when (SivilstandJson.Typer.tryParse(it)) {
                        SivilstandJson.Typer.UOPPGITT -> SivilstandTyper.UOPPGITT
                        SivilstandJson.Typer.UGIFT -> SivilstandTyper.UGIFT
                        SivilstandJson.Typer.GIFT -> SivilstandTyper.GIFT
                        SivilstandJson.Typer.ENKE_ELLER_ENKEMANN -> SivilstandTyper.ENKE_ELLER_ENKEMANN
                        SivilstandJson.Typer.SKILT -> SivilstandTyper.SKILT
                        SivilstandJson.Typer.SEPARERT -> SivilstandTyper.SEPARERT
                        SivilstandJson.Typer.REGISTRERT_PARTNER -> SivilstandTyper.REGISTRERT_PARTNER
                        SivilstandJson.Typer.SEPARERT_PARTNER -> SivilstandTyper.SEPARERT_PARTNER
                        SivilstandJson.Typer.SKILT_PARTNER -> SivilstandTyper.SKILT_PARTNER
                        SivilstandJson.Typer.GJENLEVENDE_PARTNER -> SivilstandTyper.GJENLEVENDE_PARTNER
                    }
                },
                gyldigFraOgMed = gyldigFraOgMed,
                relatertVedSivilstand = relatertVedSivilstand?.let { Fnr(it) },
                bekreftelsesdato = bekreftelsesdato,
            )
            is BostedsadresseJson -> Personhendelse.Hendelse.Bostedsadresse
            is KontaktadresseJson -> Personhendelse.Hendelse.Kontaktadresse
        }

        companion object {
            fun Personhendelse.Hendelse.toJson(): HendelseJson = when (this) {
                is Personhendelse.Hendelse.Dødsfall -> DødsfallJson(dødsdato)
                is Personhendelse.Hendelse.UtflyttingFraNorge -> UtflyttingFraNorgeJson(utflyttingsdato)
                is Personhendelse.Hendelse.Sivilstand -> SivilstandJson(
                    type = type?.let {
                        when (it) {
                            SivilstandTyper.UOPPGITT -> SivilstandJson.Typer.UOPPGITT
                            SivilstandTyper.UGIFT -> SivilstandJson.Typer.UGIFT
                            SivilstandTyper.GIFT -> SivilstandJson.Typer.GIFT
                            SivilstandTyper.ENKE_ELLER_ENKEMANN -> SivilstandJson.Typer.ENKE_ELLER_ENKEMANN
                            SivilstandTyper.SKILT -> SivilstandJson.Typer.SKILT
                            SivilstandTyper.SEPARERT -> SivilstandJson.Typer.SEPARERT
                            SivilstandTyper.REGISTRERT_PARTNER -> SivilstandJson.Typer.REGISTRERT_PARTNER
                            SivilstandTyper.SEPARERT_PARTNER -> SivilstandJson.Typer.SEPARERT_PARTNER
                            SivilstandTyper.SKILT_PARTNER -> SivilstandJson.Typer.SKILT_PARTNER
                            SivilstandTyper.GJENLEVENDE_PARTNER -> SivilstandJson.Typer.GJENLEVENDE_PARTNER
                        }.value
                    },
                    gyldigFraOgMed = gyldigFraOgMed,
                    relatertVedSivilstand = relatertVedSivilstand?.toString(),
                    bekreftelsesdato = bekreftelsesdato,
                )
                is Personhendelse.Hendelse.Bostedsadresse -> BostedsadresseJson
                is Personhendelse.Hendelse.Kontaktadresse -> KontaktadresseJson
            }
        }
    }

    internal data class MetadataJson(
        val hendelseId: String,
        val tidligereHendelseId: String?,
        val offset: Long,
        val partisjon: Int,
        val master: String,
        val key: String,
        val personidenter: List<String>,
    ) {
        companion object {
            fun Personhendelse.Metadata.toJson() = MetadataJson(
                hendelseId = hendelseId,
                tidligereHendelseId = tidligereHendelseId,
                offset = offset,
                partisjon = partisjon,
                master = master,
                key = key,
                personidenter = personidenter,
            )
        }

        fun toDomain() = Personhendelse.Metadata(
            hendelseId = hendelseId,
            personidenter = personidenter.toNonEmptyList(),
            tidligereHendelseId = tidligereHendelseId,
            offset = offset,
            partisjon = partisjon,
            master = master,
            key = key,
        )
    }
}
