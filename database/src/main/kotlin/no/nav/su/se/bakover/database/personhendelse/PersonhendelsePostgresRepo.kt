package no.nav.su.se.bakover.database.personhendelse

import com.fasterxml.jackson.annotation.JsonInclude
import kotliquery.Row
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunktOrNull
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.database.personhendelse.PersonhendelsePostgresRepo.HendelseJson.Companion.toJson
import no.nav.su.se.bakover.database.personhendelse.PersonhendelsePostgresRepo.MetadataJson.Companion.toJson
import no.nav.su.se.bakover.domain.personhendelse.Personhendelse
import no.nav.su.se.bakover.domain.personhendelse.PersonhendelseRepo
import person.domain.SivilstandTyper
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
                insert into personhendelse (id, sakId, opprettet, endret, endringstype, hendelse, oppgaveId, type, metadata, gjelderEps)
                values(
                    :id,
                    :sakId,
                    :opprettet,
                    :endret,
                    :endringstype,
                    to_jsonb(:hendelse::jsonb),
                    :oppgaveId,
                    :type,
                    to_jsonb(:metadata::jsonb),
                    :gjelderEps
                )
                on conflict do nothing
                """.trimIndent().insert(
                    mapOf(
                        "id" to personhendelse.id,
                        "sakId" to personhendelse.sakId,
                        "opprettet" to personhendelse.opprettet,
                        "endret" to tidspunkt,
                        "endringstype" to personhendelse.endringstype.toDatabasetype(),
                        "hendelse" to serialize(personhendelse.hendelse.toJson()),
                        "oppgaveId" to null,
                        "type" to personhendelse.hendelse.toDatabasetype(),
                        "metadata" to serialize(personhendelse.metadata.toJson()),
                        "gjelderEps" to personhendelse.gjelderEps,
                    ),
                    session,
                )
            }
        }
    }

    override fun lagre(personhendelse: List<Personhendelse.TilknyttetSak.SendtTilOppgave>) {
        val multiLineQuery: String = personhendelse.joinToString("\n") {
            "update personhendelse set oppgaveId = '${it.oppgaveId}', endret = :endret where id = '${it.id}';"
        }
        sessionFactory.withSession { session ->
            multiLineQuery.insert(
                mapOf(
                    "endret" to Tidspunkt.now(clock),
                ),
                session,
            )
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
                    limit 50
                """.trimIndent().hentListe(mapOf(), session) { row ->
                    row.toIkkeSendtTilOppgave()
                }
            }
        }
    }

    override fun hentPersonhendelserUtenPdlVurdering(): List<Personhendelse.TilknyttetSak.IkkeSendtTilOppgave> {
        return dbMetrics.timeQuery("hentPersonhendelserUtenPdlVurdering") {
            sessionFactory.withSession { session ->
                """
                    select
                        p.*, s.saksnummer as saksnummer
                    from
                        personhendelse p
                        left join sak s on s.id = p.sakId
                    where
                        oppgaveId is null
                        and pdl_vurdert = false
                        and antallFeiledeForsøk < 3
                    limit 50
                """.trimIndent().hentListe(mapOf(), session) { row ->
                    row.toIkkeSendtTilOppgave()
                }
            }
        }
    }

    override fun hentPersonhendelserKlareForOppgave(): List<Personhendelse.TilknyttetSak.IkkeSendtTilOppgave> {
        return dbMetrics.timeQuery("hentPersonhendelserKlareForOppgave") {
            sessionFactory.withSession { session ->
                """
                    select
                        p.*, s.saksnummer as saksnummer
                    from
                        personhendelse p
                        left join sak s on s.id = p.sakId
                    where
                        oppgaveId is null
                        and pdl_vurdert = true
                        and pdl_relevant = true
                        and antallFeiledeForsøk < 3
                    limit 50
                """.trimIndent().hentListe(mapOf(), session) { row ->
                    row.toIkkeSendtTilOppgave()
                }
            }
        }
    }

    override fun oppdaterPdlVurdering(vurderinger: List<PersonhendelseRepo.PdlVurdering>) {
        val vurderingstidspunkt = Tidspunkt.now(clock)
        sessionFactory.withSession { session ->
            vurderinger.forEach { vurdering ->
                """
                    update personhendelse
                    set
                        pdl_vurdert = true,
                        pdl_relevant = :pdlRelevant,
                        pdl_vurdert_tidspunkt = :pdlVurdertTidspunkt,
                        pdl_snapshot = :pdlSnapshot::jsonb,
                        pdl_diff = :pdlDiff::jsonb,
                        endret = :endret
                    where
                        id = :id
                """.trimIndent().insert(
                    mapOf(
                        "id" to vurdering.id,
                        "pdlRelevant" to vurdering.relevant,
                        "pdlVurdertTidspunkt" to vurderingstidspunkt,
                        "pdlSnapshot" to vurdering.pdlSnapshot,
                        "pdlDiff" to vurdering.pdlDiff,
                        "endret" to vurderingstidspunkt,
                    ),
                    session,
                )
            }
        }
    }

    override fun inkrementerAntallFeiledeForsøk(personhendelse: List<Personhendelse.TilknyttetSak>) {
        val multiLineQuery: String = personhendelse.joinToString("\n") {
            "update personhendelse set antallFeiledeForsøk = antallFeiledeForsøk + 1 where id = '${it.id}';"
        }
        sessionFactory.withSession { session ->
            multiLineQuery.insert(emptyMap(), session)
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
        opprettet = tidspunkt("opprettet"),
        gjelderEps = boolean("gjelderEps"),
        pdlOppsummering = hentPdlOppsummering(),
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
        opprettet = tidspunkt("opprettet"),
        gjelderEps = boolean("gjelderEps"),
    )

    private fun Row.toPersonhendelse(): Personhendelse.TilknyttetSak =
        when (val oppgaveId = stringOrNull("oppgaveId")) {
            null -> this.toIkkeSendtTilOppgave()
            else -> this.toSendtTilOppgave(OppgaveId(oppgaveId))
        }

    private fun Row.hentPdlOppsummering(): Personhendelse.PdlOppsummering? {
        val vurdertTidspunkt = tidspunktOrNull("pdl_vurdert_tidspunkt")
        val snapshot = stringOrNull("pdl_snapshot")?.let { deserialize<PdlSnapshotForOppgaveJson>(it) }
        val diff = stringOrNull("pdl_diff")?.let { deserialize<PdlDiffForOppgaveJson>(it) }

        if (vurdertTidspunkt == null && snapshot == null && diff == null) return null

        return Personhendelse.PdlOppsummering(
            vurdertTidspunkt = vurdertTidspunkt,
            harBostedsadresseNå = snapshot?.harBostedsadresse ?: diff?.harBostedsadresseNå,
            harKontaktadresseNå = snapshot?.harKontaktadresse ?: diff?.harKontaktadresseNå,
            begrunnelse = diff?.begrunnelse,
        )
    }

    private data class PdlSnapshotForOppgaveJson(
        val harBostedsadresse: Boolean? = null,
        val harKontaktadresse: Boolean? = null,
    )

    private data class PdlDiffForOppgaveJson(
        val begrunnelse: String? = null,
        val harBostedsadresseNå: Boolean? = null,
        val harKontaktadresseNå: Boolean? = null,
    )

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

        PersonhendelseType.BOSTEDSADRESSE.value -> {
            // Legacy-rader kan ha {} her (fra tiden før vi persisterte adressefeltene).
            // BostedsadresseJson har nullable defaults, så {} blir lest kompatibelt.
            deserialize<HendelseJson.BostedsadresseJson>(string("hendelse")).toDomain()
        }

        PersonhendelseType.KONTAKTADRESSE.value -> {
            // Legacy-rader kan ha {} her (fra tiden før vi persisterte adressefeltene).
            // KontaktadresseJson har nullable defaults, så {} blir lest kompatibelt.
            deserialize<HendelseJson.KontaktadresseJson>(string("hendelse")).toDomain()
        }
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
                return entries
                    .firstOrNull { it.value == value }
                    ?: throw IllegalStateException("Ukjent PersonhendelseEndringstype: $value")
            }
        }
    }

    /**
     * Dto som persisteres som JSON i databasen. Tilbyr mapping til/fra domenetypen.
     */
    private sealed interface HendelseJson {
        data class DødsfallJson(val dødsdato: LocalDate?) : HendelseJson
        data class UtflyttingFraNorgeJson(val utflyttingsdato: LocalDate?) : HendelseJson
        data class SivilstandJson(
            val type: String?,
            val gyldigFraOgMed: LocalDate?,
            val relatertVedSivilstand: String?,
            val bekreftelsesdato: LocalDate?,
        ) : HendelseJson {
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
                        return entries
                            .firstOrNull { it.value == value }
                            ?: throw IllegalStateException("Ukjent sivilstandtype: $value")
                    }
                }
            }
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        data class BostedsadresseJson(
            val angittFlyttedato: LocalDate? = null,
            val gyldigFraOgMed: LocalDate? = null,
            val gyldigTilOgMed: LocalDate? = null,
            val coAdressenavn: String? = null,
            val adressetype: String? = null,
        ) : HendelseJson {
            enum class Adressetype(val value: String) {
                VEGADRESSE("vegadresse"),
                MATRIKKELADRESSE("matrikkeladresse"),
                UTENLANDSK_ADRESSE("utenlandsk_adresse"),
                UKJENT_BOSTED("ukjent_bosted"),
                ;

                companion object
            }
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        data class KontaktadresseJson(
            val gyldigFraOgMed: LocalDate? = null,
            val gyldigTilOgMed: LocalDate? = null,
            val type: String? = null,
            val coAdressenavn: String? = null,
            val adressetype: String? = null,
        ) : HendelseJson {
            enum class Adressetype(val value: String) {
                POSTBOKSADRESSE("postboksadresse"),
                VEGADRESSE("vegadresse"),
                POSTADRESSE_I_FRITT_FORMAT("postadresse_i_fritt_format"),
                UTENLANDSK_ADRESSE("utenlandsk_adresse"),
                UTENLANDSK_ADRESSE_I_FRITT_FORMAT("utenlandsk_adresse_i_fritt_format"),
                ;

                companion object
            }
        }

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

            is BostedsadresseJson -> Personhendelse.Hendelse.Bostedsadresse(
                angittFlyttedato = angittFlyttedato,
                gyldigFraOgMed = gyldigFraOgMed,
                gyldigTilOgMed = gyldigTilOgMed,
                coAdressenavn = coAdressenavn,
                adressetype = adressetype?.let { raw ->
                    when (raw) {
                        BostedsadresseJson.Adressetype.VEGADRESSE.value -> Personhendelse.Hendelse.Bostedsadresse.Adressetype.VEGADRESSE
                        BostedsadresseJson.Adressetype.MATRIKKELADRESSE.value -> Personhendelse.Hendelse.Bostedsadresse.Adressetype.MATRIKKELADRESSE
                        BostedsadresseJson.Adressetype.UTENLANDSK_ADRESSE.value -> Personhendelse.Hendelse.Bostedsadresse.Adressetype.UTENLANDSK_ADRESSE
                        BostedsadresseJson.Adressetype.UKJENT_BOSTED.value -> Personhendelse.Hendelse.Bostedsadresse.Adressetype.UKJENT_BOSTED
                        else -> null
                    }
                },
            )

            is KontaktadresseJson -> Personhendelse.Hendelse.Kontaktadresse(
                gyldigFraOgMed = gyldigFraOgMed,
                gyldigTilOgMed = gyldigTilOgMed,
                type = type,
                coAdressenavn = coAdressenavn,
                adressetype = adressetype?.let { raw ->
                    when (raw) {
                        KontaktadresseJson.Adressetype.POSTBOKSADRESSE.value -> Personhendelse.Hendelse.Kontaktadresse.Adressetype.POSTBOKSADRESSE
                        KontaktadresseJson.Adressetype.VEGADRESSE.value -> Personhendelse.Hendelse.Kontaktadresse.Adressetype.VEGADRESSE
                        KontaktadresseJson.Adressetype.POSTADRESSE_I_FRITT_FORMAT.value -> Personhendelse.Hendelse.Kontaktadresse.Adressetype.POSTADRESSE_I_FRITT_FORMAT
                        KontaktadresseJson.Adressetype.UTENLANDSK_ADRESSE.value -> Personhendelse.Hendelse.Kontaktadresse.Adressetype.UTENLANDSK_ADRESSE
                        KontaktadresseJson.Adressetype.UTENLANDSK_ADRESSE_I_FRITT_FORMAT.value -> Personhendelse.Hendelse.Kontaktadresse.Adressetype.UTENLANDSK_ADRESSE_I_FRITT_FORMAT
                        else -> null
                    }
                },
            )
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

                is Personhendelse.Hendelse.Bostedsadresse -> BostedsadresseJson(
                    angittFlyttedato = angittFlyttedato,
                    gyldigFraOgMed = gyldigFraOgMed,
                    gyldigTilOgMed = gyldigTilOgMed,
                    coAdressenavn = coAdressenavn,
                    adressetype = adressetype?.let {
                        when (it) {
                            Personhendelse.Hendelse.Bostedsadresse.Adressetype.VEGADRESSE -> BostedsadresseJson.Adressetype.VEGADRESSE
                            Personhendelse.Hendelse.Bostedsadresse.Adressetype.MATRIKKELADRESSE -> BostedsadresseJson.Adressetype.MATRIKKELADRESSE
                            Personhendelse.Hendelse.Bostedsadresse.Adressetype.UTENLANDSK_ADRESSE -> BostedsadresseJson.Adressetype.UTENLANDSK_ADRESSE
                            Personhendelse.Hendelse.Bostedsadresse.Adressetype.UKJENT_BOSTED -> BostedsadresseJson.Adressetype.UKJENT_BOSTED
                        }.value
                    },
                )

                is Personhendelse.Hendelse.Kontaktadresse -> KontaktadresseJson(
                    gyldigFraOgMed = gyldigFraOgMed,
                    gyldigTilOgMed = gyldigTilOgMed,
                    type = type,
                    coAdressenavn = coAdressenavn,
                    adressetype = adressetype?.let {
                        when (it) {
                            Personhendelse.Hendelse.Kontaktadresse.Adressetype.POSTBOKSADRESSE -> KontaktadresseJson.Adressetype.POSTBOKSADRESSE
                            Personhendelse.Hendelse.Kontaktadresse.Adressetype.VEGADRESSE -> KontaktadresseJson.Adressetype.VEGADRESSE
                            Personhendelse.Hendelse.Kontaktadresse.Adressetype.POSTADRESSE_I_FRITT_FORMAT -> KontaktadresseJson.Adressetype.POSTADRESSE_I_FRITT_FORMAT
                            Personhendelse.Hendelse.Kontaktadresse.Adressetype.UTENLANDSK_ADRESSE -> KontaktadresseJson.Adressetype.UTENLANDSK_ADRESSE
                            Personhendelse.Hendelse.Kontaktadresse.Adressetype.UTENLANDSK_ADRESSE_I_FRITT_FORMAT -> KontaktadresseJson.Adressetype.UTENLANDSK_ADRESSE_I_FRITT_FORMAT
                        }.value
                    },
                )
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
        val eksternOpprettet: Tidspunkt?,
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
                eksternOpprettet = eksternOpprettet,
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
            eksternOpprettet = eksternOpprettet,
        )
    }
}
