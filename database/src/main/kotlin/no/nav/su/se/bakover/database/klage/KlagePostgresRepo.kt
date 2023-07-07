package no.nav.su.se.bakover.database.klage

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import kotliquery.Row
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.extensions.ddMMyyyyFormatter
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresTransactionContext.Companion.withTransaction
import no.nav.su.se.bakover.common.infrastructure.persistence.Session
import no.nav.su.se.bakover.common.infrastructure.persistence.booleanOrNull
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.database.attestering.toAttesteringshistorikk
import no.nav.su.se.bakover.database.attestering.toDatabaseJson
import no.nav.su.se.bakover.database.klage.AvsluttetKlageJson.Companion.toAvsluttetKlageJson
import no.nav.su.se.bakover.database.klage.KlagePostgresRepo.Svarord.Companion.tilDatabaseType
import no.nav.su.se.bakover.database.klage.KlagePostgresRepo.Tilstand.Companion.databasetype
import no.nav.su.se.bakover.database.klage.KlagePostgresRepo.VedtaksvurderingJson.Companion.toJson
import no.nav.su.se.bakover.database.klage.KlagePostgresRepo.VedtaksvurderingJson.Omgjør.Omgjøringsutfall.Companion.toDatabasetype
import no.nav.su.se.bakover.database.klage.KlagePostgresRepo.VedtaksvurderingJson.Omgjør.Omgjøringsårsak.Companion.toDatabasetype
import no.nav.su.se.bakover.database.klage.KlagePostgresRepo.VedtaksvurderingJson.Oppretthold.Hjemmel.Companion.toDatabasetype
import no.nav.su.se.bakover.database.klage.klageinstans.KlageinstanshendelsePostgresRepo
import no.nav.su.se.bakover.domain.klage.AvsluttetKlage
import no.nav.su.se.bakover.domain.klage.AvvistKlage
import no.nav.su.se.bakover.domain.klage.Hjemler
import no.nav.su.se.bakover.domain.klage.IverksattAvvistKlage
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.domain.klage.KlageTilAttestering
import no.nav.su.se.bakover.domain.klage.Klageinstanshendelser
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.klage.OversendtKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import no.nav.su.se.bakover.domain.klage.VurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import no.nav.su.se.bakover.domain.sak.Saksnummer
import java.time.LocalDate
import java.util.UUID

internal class KlagePostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
    private val klageinstanshendelsePostgresRepo: KlageinstanshendelsePostgresRepo,
) : KlageRepo {

    override fun lagre(klage: Klage, transactionContext: TransactionContext) {
        dbMetrics.timeQuery("lagreKlage") {
            transactionContext.withTransaction { transaction ->
                when (klage) {
                    is OpprettetKlage -> lagreOpprettetKlage(klage, transaction)

                    is VilkårsvurdertKlage.Påbegynt -> lagreVilkårsvurdertKlage(klage, transaction)
                    is VilkårsvurdertKlage.Utfylt -> lagreVilkårsvurdertKlage(klage, transaction)
                    is VilkårsvurdertKlage.Bekreftet -> lagreVilkårsvurdertKlage(klage, transaction)

                    is VurdertKlage -> lagreVurdertKlage(klage, transaction)

                    is AvvistKlage -> lagreAvvistKlage(klage, transaction)

                    is KlageTilAttestering -> lagreTilAttestering(klage, transaction)
                    is OversendtKlage -> lagreOversendtKlage(klage, transaction)
                    is IverksattAvvistKlage -> lagreIverksattAvvistKlage(klage, transaction)
                    is AvsluttetKlage -> lagreAvsluttetKlage(klage, transaction)
                }
            }
        }
    }

    private fun lagreOpprettetKlage(klage: OpprettetKlage, session: Session) {
        """
            insert into klage(id,  sakid,  opprettet,  journalpostid,  oppgaveid,  saksbehandler,  datoKlageMottatt,  type)
                      values(:id, :sakid, :opprettet, :journalpostid, :oppgaveid, :saksbehandler, :datoKlageMottatt, :type)
        """.trimIndent()
            .insert(
                params = mapOf(
                    "id" to klage.id,
                    "sakid" to klage.sakId,
                    "opprettet" to klage.opprettet,
                    "journalpostid" to klage.journalpostId,
                    "oppgaveid" to klage.oppgaveId,
                    "saksbehandler" to klage.saksbehandler,
                    "datoKlageMottatt" to klage.datoKlageMottatt,
                    "type" to klage.databasetype(),
                ),
                session = session,
            )
    }

    private fun lagreVilkårsvurdertKlage(klage: VilkårsvurdertKlage, session: Session) {
        """
            update klage set
                oppgaveid=:oppgaveid,
                saksbehandler=:saksbehandler,
                type=:type,
                attestering = to_jsonb(:attestering::jsonb),
                vedtakId=:vedtakId,
                innenforFristen=:innenforFristen,
                klagesDetPåKonkreteElementerIVedtaket=:klagesDetPaaKonkreteElementerIVedtaket,
                erUnderskrevet=:erUnderskrevet,
                begrunnelse=:begrunnelse
            where id=:id
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "id" to klage.id,
                    "oppgaveid" to klage.oppgaveId,
                    "saksbehandler" to klage.saksbehandler,
                    "type" to klage.databasetype(),
                    "attestering" to klage.attesteringer.toDatabaseJson(),
                    "vedtakId" to klage.vilkårsvurderinger.vedtakId,
                    "innenforFristen" to klage.vilkårsvurderinger.innenforFristen?.tilDatabaseType(),
                    "klagesDetPaaKonkreteElementerIVedtaket" to klage.vilkårsvurderinger.klagesDetPåKonkreteElementerIVedtaket,
                    "erUnderskrevet" to klage.vilkårsvurderinger.erUnderskrevet?.tilDatabaseType(),
                    "begrunnelse" to klage.vilkårsvurderinger.begrunnelse,
                ),
                session,
            )
    }

    private fun lagreVurdertKlage(klage: VurdertKlage, session: Session) {
        """
            update klage set
                oppgaveid=:oppgaveid,
                saksbehandler=:saksbehandler,
                type=:type,
                attestering=to_jsonb(:attestering::jsonb),
                vedtakId=:vedtakId,
                innenforFristen=:innenforFristen,
                klagesDetPåKonkreteElementerIVedtaket=:klagesDetPaaKonkreteElementerIVedtaket,
                erUnderskrevet=:erUnderskrevet,
                begrunnelse=:begrunnelse,
                fritekstTilBrev=:fritekstTilBrev,
                vedtaksvurdering=to_jsonb(:vedtaksvurdering::jsonb)
            where id=:id
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "id" to klage.id,
                    "oppgaveid" to klage.oppgaveId,
                    "saksbehandler" to klage.saksbehandler,
                    "type" to klage.databasetype(),
                    "vedtakId" to klage.vilkårsvurderinger.vedtakId,
                    "innenforFristen" to klage.vilkårsvurderinger.innenforFristen.tilDatabaseType(),
                    "klagesDetPaaKonkreteElementerIVedtaket" to klage.vilkårsvurderinger.klagesDetPåKonkreteElementerIVedtaket,
                    "erUnderskrevet" to klage.vilkårsvurderinger.erUnderskrevet.tilDatabaseType(),
                    "begrunnelse" to klage.vilkårsvurderinger.begrunnelse,
                    "fritekstTilBrev" to klage.vurderinger.fritekstTilOversendelsesbrev,
                    "vedtaksvurdering" to klage.vurderinger.vedtaksvurdering?.toJson(),
                    "attestering" to klage.attesteringer.toDatabaseJson(),
                ),
                session,
            )
    }

    private fun lagreAvvistKlage(klage: AvvistKlage, session: Session) {
        """
            UPDATE
                klage
            SET
                type=:type,
                attestering=to_jsonb(:attestering::jsonb),
                fritekstTilBrev=:fritekst,
                saksbehandler=:saksbehandler
            WHERE
                id=:id
        """.trimIndent().oppdatering(
            mapOf(
                "id" to klage.id,
                "type" to klage.databasetype(),
                "attestering" to klage.attesteringer.toDatabaseJson(),
                "fritekst" to klage.fritekstTilVedtaksbrev,
                "saksbehandler" to klage.saksbehandler,
            ),
            session,
        )
    }

    private fun lagreTilAttestering(klage: KlageTilAttestering, session: Session) {
        """
            update
                klage
            set
                oppgaveid=:oppgaveid,
                type=:type,
                saksbehandler=:saksbehandler,
                attestering=to_jsonb(:attestering::jsonb)
            where id=:id
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "id" to klage.id,
                    "oppgaveid" to klage.oppgaveId,
                    "type" to klage.databasetype(),
                    "saksbehandler" to klage.saksbehandler,
                    "attestering" to klage.attesteringer.toDatabaseJson(),
                ),
                session,
            )
    }

    private fun lagreOversendtKlage(klage: OversendtKlage, session: Session) {
        """
            update
                klage
            set
                oppgaveid=:oppgaveid,
                type=:type,
                attestering=to_jsonb(:attestering::jsonb)
            where id=:id
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "id" to klage.id,
                    "oppgaveid" to klage.oppgaveId,
                    "type" to klage.databasetype(),
                    "attestering" to klage.attesteringer.toDatabaseJson(),
                ),
                session,
            )
    }

    private fun lagreIverksattAvvistKlage(klage: IverksattAvvistKlage, session: Session) {
        """
            UPDATE
                klage
            SET
                type=:type,
                attestering=to_jsonb(:attestering::jsonb)
            WHERE
                id=:id
        """.trimIndent().oppdatering(
            mapOf(
                "id" to klage.id,
                "type" to klage.databasetype(),
                "attestering" to klage.attesteringer.toDatabaseJson(),
            ),
            session,
        )
    }

    private fun lagreAvsluttetKlage(klage: AvsluttetKlage, session: Session) {
        // Dette vil overskrive saksbehandler for klagens forrigeSteg, siden vi kun har en enkelt `text` i databasetabellen som representerer en saksbehandler.
        """
            UPDATE
                klage
            SET
                saksbehandler=:saksbehandler,
                avsluttet=to_jsonb(:avsluttet::jsonb)
            WHERE
                id=:id
        """.trimIndent().oppdatering(
            mapOf(
                "id" to klage.id,
                "saksbehandler" to klage.saksbehandler,
                "avsluttet" to klage.toAvsluttetKlageJson(),
            ),
            session,
        )
    }

    override fun hentKlage(klageId: UUID): Klage? {
        return dbMetrics.timeQuery("hentKlageForId") {
            sessionFactory.withSession { session ->
                hentKlage(klageId, session)
            }
        }
    }

    internal fun hentKlage(klageId: UUID, session: Session): Klage? {
        return "select k.*, s.fnr, s.saksnummer  from klage k inner join sak s on s.id = k.sakId where k.id=:id".trimIndent()
            .hent(
                params = mapOf("id" to klageId),
                session = session,
            ) { rowToKlage(it, session) }
    }

    override fun hentKlager(sakid: UUID, sessionContext: SessionContext): List<Klage> {
        return dbMetrics.timeQuery("hentKlager") {
            sessionContext.withSession { session ->
                """
                    select k.*, s.fnr, s.saksnummer  from klage k inner join sak s on s.id = k.sakId where k.sakid=:sakid order by k.opprettet
                """.trimIndent().hentListe(
                    mapOf(
                        "sakid" to sakid,
                    ),
                    session,
                ) { rowToKlage(it, session) }
            }
        }
    }

    override fun hentVedtaksbrevDatoSomDetKlagesPå(klageId: UUID): LocalDate? {
        return dbMetrics.timeQuery("hentVedtaksbrevDatoSomDetKlagesPå") {
            sessionFactory.withSession {
                """
                select d.generertdokumentjson->'personalia'->>'dato' as vedtaksbrevdato
                  from klage k
                  join vedtak v on k.vedtakid = v.id
                  join dokument d on d.vedtakid = v.id
                  where k.id = :id
                  and d.duplikatAv is null
                """.trimIndent()
                    .hent(mapOf("id" to klageId), it) { row ->
                        row.string("vedtaksbrevdato").let {
                            LocalDate.parse(it, ddMMyyyyFormatter) // Eksempel: 17.10.2022
                        }
                    }
            }
        }
    }

    override fun defaultSessionContext(): SessionContext {
        return sessionFactory.newSessionContext()
    }

    override fun defaultTransactionContext(): TransactionContext {
        return sessionFactory.newTransactionContext()
    }

    private fun rowToKlage(row: Row, session: Session): Klage {
        val id: UUID = row.uuid("id")
        val opprettet: Tidspunkt = row.tidspunkt("opprettet")
        val sakId: UUID = row.uuid("sakid")
        val saksnummer = Saksnummer(row.long("saksnummer"))
        val fnr = Fnr(row.string("fnr"))
        val journalpostId = JournalpostId(row.string("journalpostid"))
        val oppgaveId = OppgaveId(row.string("oppgaveId"))
        val datoKlageMottatt = row.localDate("datoKlageMottatt")
        val saksbehandler: NavIdentBruker.Saksbehandler = NavIdentBruker.Saksbehandler(row.string("saksbehandler"))

        val attesteringer = row.string("attestering").toAttesteringshistorikk()
        val klageinstanshendelser =
            Klageinstanshendelser.create(
                klageinstanshendelsePostgresRepo.hentProsesserteKlageinstanshendelser(
                    id,
                    session,
                ),
            )

        val vilkårsvurderingerTilKlage = VilkårsvurderingerTilKlage.create(
            vedtakId = row.uuidOrNull("vedtakId"),
            innenforFristen = row.stringOrNull("innenforFristen")?.let {
                VilkårsvurderingerTilKlage.Svarord.valueOf(it)
            },
            klagesDetPåKonkreteElementerIVedtaket = row.booleanOrNull("klagesDetPåKonkreteElementerIVedtaket"),
            erUnderskrevet = row.stringOrNull("erUnderskrevet")?.let {
                VilkårsvurderingerTilKlage.Svarord.valueOf(it)
            },
            begrunnelse = row.stringOrNull("begrunnelse"),
        )

        val fritekstTilBrev = row.stringOrNull("fritekstTilBrev")
        val vedtaksvurdering = row.stringOrNull("vedtaksvurdering")?.let {
            deserialize<VedtaksvurderingJson>(it).toDomain()
        }
        val vurderinger = if (fritekstTilBrev == null && vedtaksvurdering == null) {
            null
        } else {
            VurderingerTilKlage.create(
                fritekstTilOversendelsesbrev = fritekstTilBrev,
                vedtaksvurdering = vedtaksvurdering,
            )
        }

        fun bekreftetVilkårsvurdertKlageTilVurdering() = VilkårsvurdertKlage.Bekreftet.TilVurdering(
            id = id,
            opprettet = opprettet,
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            journalpostId = journalpostId,
            oppgaveId = oppgaveId,
            saksbehandler = saksbehandler,
            vilkårsvurderinger = vilkårsvurderingerTilKlage as VilkårsvurderingerTilKlage.Utfylt,
            vurderinger = vurderinger,
            attesteringer = attesteringer,
            datoKlageMottatt = datoKlageMottatt,
            klageinstanshendelser = klageinstanshendelser,
        )

        fun bekreftetAvvistVilkårsvurdertKlage() = VilkårsvurdertKlage.Bekreftet.Avvist(
            id = id,
            opprettet = opprettet,
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            journalpostId = journalpostId,
            oppgaveId = oppgaveId,
            saksbehandler = saksbehandler,
            vilkårsvurderinger = vilkårsvurderingerTilKlage as VilkårsvurderingerTilKlage.Utfylt,
            attesteringer = attesteringer,
            datoKlageMottatt = datoKlageMottatt,
            fritekstTilAvvistVedtaksbrev = fritekstTilBrev,
        )

        fun avvistKlage() = AvvistKlage(
            forrigeSteg = bekreftetAvvistVilkårsvurdertKlage(),
            saksbehandler = saksbehandler,
            fritekstTilVedtaksbrev = fritekstTilBrev
                ?: throw IllegalStateException("Fritekst må være utfylt for en avvist klage som er til attestering. id: $id"),
        )

        fun påbegyntVurdertKlage() = VurdertKlage.Påbegynt(
            forrigeSteg = bekreftetVilkårsvurdertKlageTilVurdering(),
            saksbehandler = saksbehandler,
            // En påbegynt klage kan ikke ha utfylte vurderinger (det vil være de tilfellene da Påbegynt er representert som forrigeSteg
            vurderinger = if (vurderinger == null || vurderinger is VurderingerTilKlage.Utfylt) VurderingerTilKlage.empty() else vurderinger as VurderingerTilKlage.Påbegynt,
        )

        fun utfyltVurdertKlage() = VurdertKlage.Utfylt(
            forrigeSteg = påbegyntVurdertKlage(),
            saksbehandler = saksbehandler,
            vurderinger = vurderinger as VurderingerTilKlage.Utfylt,
        )

        fun bekreftetVurdertKlage() = VurdertKlage.Bekreftet(
            forrigeSteg = utfyltVurdertKlage(),
            saksbehandler = saksbehandler,
        )

        fun avvistKlageTilAttestering() = KlageTilAttestering.Avvist(
            forrigeSteg = avvistKlage(),
            oppgaveId = oppgaveId,
            saksbehandler = saksbehandler,
        )

        fun vurdertKlageTilAttestering() = KlageTilAttestering.Vurdert(
            forrigeSteg = bekreftetVurdertKlage(),
            oppgaveId = oppgaveId,
            saksbehandler = saksbehandler,
        )

        val klage = when (Tilstand.fromString(row.string("type"))) {
            Tilstand.OPPRETTET -> OpprettetKlage(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                journalpostId = journalpostId,
                oppgaveId = oppgaveId,
                saksbehandler = saksbehandler,
                datoKlageMottatt = datoKlageMottatt,
            )
            Tilstand.VILKÅRSVURDERT_PÅBEGYNT -> {
                VilkårsvurdertKlage.Påbegynt(
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    fnr = fnr,
                    journalpostId = journalpostId,
                    oppgaveId = oppgaveId,
                    saksbehandler = saksbehandler,
                    vilkårsvurderinger = vilkårsvurderingerTilKlage as VilkårsvurderingerTilKlage.Påbegynt,
                    attesteringer = attesteringer,
                    datoKlageMottatt = datoKlageMottatt,
                )
            }
            Tilstand.VILKÅRSVURDERT_UTFYLT_TIL_VURDERING -> VilkårsvurdertKlage.Utfylt.TilVurdering(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                journalpostId = journalpostId,
                oppgaveId = oppgaveId,
                saksbehandler = saksbehandler,
                vilkårsvurderinger = vilkårsvurderingerTilKlage as VilkårsvurderingerTilKlage.Utfylt,
                vurderinger = vurderinger,
                attesteringer = attesteringer,
                datoKlageMottatt = datoKlageMottatt,
                klageinstanshendelser = klageinstanshendelser,
            )
            Tilstand.VILKÅRSVURDERT_UTFYLT_AVVIST -> VilkårsvurdertKlage.Utfylt.Avvist(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                journalpostId = journalpostId,
                oppgaveId = oppgaveId,
                saksbehandler = saksbehandler,
                vilkårsvurderinger = vilkårsvurderingerTilKlage as VilkårsvurderingerTilKlage.Utfylt,
                attesteringer = attesteringer,
                datoKlageMottatt = datoKlageMottatt,
                fritekstTilVedtaksbrev = fritekstTilBrev,
            )
            Tilstand.VILKÅRSVURDERT_BEKREFTET_TIL_VURDERING -> bekreftetVilkårsvurdertKlageTilVurdering()
            Tilstand.VILKÅRSVURDERT_BEKREFTET_AVVIST -> bekreftetAvvistVilkårsvurdertKlage()
            Tilstand.VURDERT_PÅBEGYNT -> påbegyntVurdertKlage()
            Tilstand.VURDERT_UTFYLT -> utfyltVurdertKlage()
            Tilstand.VURDERT_BEKREFTET -> bekreftetVurdertKlage()
            Tilstand.AVVIST -> avvistKlage()
            Tilstand.TIL_ATTESTERING_TIL_VURDERING -> vurdertKlageTilAttestering()
            Tilstand.TIL_ATTESTERING_AVVIST -> avvistKlageTilAttestering()
            Tilstand.OVERSENDT -> OversendtKlage(
                forrigeSteg = vurdertKlageTilAttestering(),
                attesteringer = attesteringer,
                klageinstanshendelser = klageinstanshendelser,
            )
            Tilstand.IVERKSATT_AVVIST -> IverksattAvvistKlage(
                forrigeSteg = avvistKlageTilAttestering(),
                attesteringer = attesteringer,
            )
        }
        val avsluttet = row.stringOrNull("avsluttet")?.let {
            AvsluttetKlageJson.fromJsonString(it)
        }
        return if (avsluttet != null) {
            AvsluttetKlage(
                underliggendeKlage = klage,
                saksbehandler = saksbehandler,
                begrunnelse = avsluttet.begrunnelse,
                avsluttetTidspunkt = avsluttet.tidspunktAvsluttet,
            )
        } else {
            klage
        }
    }

    private enum class Svarord {
        JA,
        NEI_MEN_SKAL_VURDERES,
        NEI,
        ;

        companion object {
            fun VilkårsvurderingerTilKlage.Svarord.tilDatabaseType(): String {
                return when (this) {
                    VilkårsvurderingerTilKlage.Svarord.JA -> JA
                    VilkårsvurderingerTilKlage.Svarord.NEI_MEN_SKAL_VURDERES -> NEI_MEN_SKAL_VURDERES
                    VilkårsvurderingerTilKlage.Svarord.NEI -> NEI
                }.toString()
            }
        }
    }

    internal enum class Tilstand(val verdi: String) {
        OPPRETTET("opprettet"),

        VILKÅRSVURDERT_PÅBEGYNT("vilkårsvurdert_påbegynt"),
        VILKÅRSVURDERT_UTFYLT_TIL_VURDERING("vilkårsvurdert_utfylt_til_vurdering"),
        VILKÅRSVURDERT_UTFYLT_AVVIST("vilkårsvurdert_utfylt_avvist"),
        VILKÅRSVURDERT_BEKREFTET_TIL_VURDERING("vilkårsvurdert_bekreftet_til_vurdering"),
        VILKÅRSVURDERT_BEKREFTET_AVVIST("vilkårsvurdert_bekreftet_avvist"),

        VURDERT_PÅBEGYNT("vurdert_påbegynt"),
        VURDERT_UTFYLT("vurdert_utfylt"),
        VURDERT_BEKREFTET("vurdert_bekreftet"),

        AVVIST("avvist"),

        TIL_ATTESTERING_TIL_VURDERING("til_attestering_til_vurdering"),
        TIL_ATTESTERING_AVVIST("til_attestering_avvist"),

        OVERSENDT("oversendt"),
        IVERKSATT_AVVIST("iverksatt_avvist"),
        ;

        companion object {
            fun Klage.databasetype(): String {
                return when (this) {
                    is OpprettetKlage -> OPPRETTET

                    is VilkårsvurdertKlage.Påbegynt -> VILKÅRSVURDERT_PÅBEGYNT
                    is VilkårsvurdertKlage.Utfylt.TilVurdering -> VILKÅRSVURDERT_UTFYLT_TIL_VURDERING
                    is VilkårsvurdertKlage.Utfylt.Avvist -> VILKÅRSVURDERT_UTFYLT_AVVIST
                    is VilkårsvurdertKlage.Bekreftet.TilVurdering -> VILKÅRSVURDERT_BEKREFTET_TIL_VURDERING
                    is VilkårsvurdertKlage.Bekreftet.Avvist -> VILKÅRSVURDERT_BEKREFTET_AVVIST

                    is VurdertKlage.Påbegynt -> VURDERT_PÅBEGYNT
                    is VurdertKlage.Utfylt -> VURDERT_UTFYLT
                    is VurdertKlage.Bekreftet -> VURDERT_BEKREFTET

                    is AvvistKlage -> AVVIST

                    is KlageTilAttestering.Vurdert -> TIL_ATTESTERING_TIL_VURDERING
                    is KlageTilAttestering.Avvist -> TIL_ATTESTERING_AVVIST

                    is OversendtKlage -> OVERSENDT
                    is IverksattAvvistKlage -> IVERKSATT_AVVIST
                    is AvsluttetKlage -> this.hentUnderliggendeKlage().databasetype()
                }.toString()
            }

            fun fromString(value: String): Tilstand {
                return values().find { it.verdi == value }
                    ?: throw IllegalStateException("Ukjent tilstand i klage-tabellen: $value")
            }
        }

        override fun toString() = verdi
    }

    /**
     * Databasenversjonen av [VurderingerTilKlage]
     *
     * Merk at man kan ha valgt utfall, uten å ha fylt inn innholdet. Selve kravene til utfylling styres av domenet vha. feltet 'type' (tilstand) i tabellen klage.
     */
    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type",
    )
    @JsonSubTypes(
        JsonSubTypes.Type(
            value = VedtaksvurderingJson.Omgjør::class,
            name = "Omgjør",
        ),
        JsonSubTypes.Type(
            value = VedtaksvurderingJson.Oppretthold::class,
            name = "Oppretthold",
        ),
    )
    private sealed class VedtaksvurderingJson {
        abstract fun toDomain(): VurderingerTilKlage.Vedtaksvurdering
        data class Omgjør(val årsak: String?, val utfall: String?) : VedtaksvurderingJson() {

            override fun toDomain(): VurderingerTilKlage.Vedtaksvurdering {
                return VurderingerTilKlage.Vedtaksvurdering.createOmgjør(
                    årsak = årsak?.let { Omgjøringsårsak.fromString(it).toDomain() },
                    utfall = utfall?.let { Omgjøringsutfall.fromString(it).toDomain() },
                )
            }

            enum class Omgjøringsårsak(val verdi: String) {
                FEIL_LOVANVENDELSE("feil_lovanvendelse"),
                ULIK_SKJØNNSVURDERING("ulik_skjønnsvurdering"),
                SAKSBEHANDLINGSFEIL("saksbehandlingsfeil"),
                NYTT_FAKTUM("nytt_faktum"),
                ;

                fun toDomain(): VurderingerTilKlage.Vedtaksvurdering.Årsak {
                    return when (this) {
                        FEIL_LOVANVENDELSE -> VurderingerTilKlage.Vedtaksvurdering.Årsak.FEIL_LOVANVENDELSE
                        ULIK_SKJØNNSVURDERING -> VurderingerTilKlage.Vedtaksvurdering.Årsak.ULIK_SKJØNNSVURDERING
                        SAKSBEHANDLINGSFEIL -> VurderingerTilKlage.Vedtaksvurdering.Årsak.SAKSBEHANDLINGSFEIL
                        NYTT_FAKTUM -> VurderingerTilKlage.Vedtaksvurdering.Årsak.NYTT_FAKTUM
                    }
                }

                companion object {
                    fun VurderingerTilKlage.Vedtaksvurdering.Årsak.toDatabasetype(): String {
                        return when (this) {
                            VurderingerTilKlage.Vedtaksvurdering.Årsak.FEIL_LOVANVENDELSE -> FEIL_LOVANVENDELSE
                            VurderingerTilKlage.Vedtaksvurdering.Årsak.ULIK_SKJØNNSVURDERING -> ULIK_SKJØNNSVURDERING
                            VurderingerTilKlage.Vedtaksvurdering.Årsak.SAKSBEHANDLINGSFEIL -> SAKSBEHANDLINGSFEIL
                            VurderingerTilKlage.Vedtaksvurdering.Årsak.NYTT_FAKTUM -> NYTT_FAKTUM
                        }.toString()
                    }

                    fun fromString(value: String): Omgjøringsårsak {
                        return values().find { it.verdi == value }
                            ?: throw IllegalStateException("Ukjent omgjøringsårsak i klage-tabellen: $value")
                    }
                }

                override fun toString() = verdi
            }

            enum class Omgjøringsutfall(val verdi: String) {
                TIL_GUNST("til_gunst"),
                TIL_UGUNST("til_ugunst"),
                ;

                fun toDomain(): VurderingerTilKlage.Vedtaksvurdering.Utfall {
                    return when (this) {
                        TIL_GUNST -> VurderingerTilKlage.Vedtaksvurdering.Utfall.TIL_GUNST
                        TIL_UGUNST -> VurderingerTilKlage.Vedtaksvurdering.Utfall.TIL_UGUNST
                    }
                }

                companion object {
                    fun VurderingerTilKlage.Vedtaksvurdering.Utfall.toDatabasetype(): String {
                        return when (this) {
                            VurderingerTilKlage.Vedtaksvurdering.Utfall.TIL_GUNST -> TIL_GUNST
                            VurderingerTilKlage.Vedtaksvurdering.Utfall.TIL_UGUNST -> TIL_UGUNST
                        }.toString()
                    }

                    fun fromString(value: String): Omgjøringsutfall {
                        return values().find { it.verdi == value }
                            ?: throw IllegalStateException("Ukjent omgjøringsutfall i klage-tabellen: $value")
                    }
                }

                override fun toString() = verdi
            }
        }

        data class Oppretthold(val hjemler: List<String>) : VedtaksvurderingJson() {

            enum class Hjemmel(val verdi: String) {
                SU_PARAGRAF_3("su_paragraf_3"),
                SU_PARAGRAF_4("su_paragraf_4"),
                SU_PARAGRAF_5("su_paragraf_5"),
                SU_PARAGRAF_6("su_paragraf_6"),
                SU_PARAGRAF_7("su_paragraf_7"),
                SU_PARAGRAF_8("su_paragraf_8"),
                SU_PARAGRAF_9("su_paragraf_9"),
                SU_PARAGRAF_10("su_paragraf_10"),
                SU_PARAGRAF_11("su_paragraf_11"),
                SU_PARAGRAF_12("su_paragraf_12"),
                SU_PARAGRAF_13("su_paragraf_13"),
                SU_PARAGRAF_17("su_paragraf_17"),
                SU_PARAGRAF_18("su_paragraf_18"),
                SU_PARAGRAF_21("su_paragraf_21"),
                ;

                fun toDomain(): no.nav.su.se.bakover.domain.klage.Hjemmel {
                    return when (this) {
                        SU_PARAGRAF_3 -> no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_3
                        SU_PARAGRAF_4 -> no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_4
                        SU_PARAGRAF_5 -> no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_5
                        SU_PARAGRAF_6 -> no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_6
                        SU_PARAGRAF_7 -> no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_7
                        SU_PARAGRAF_8 -> no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_8
                        SU_PARAGRAF_9 -> no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_9
                        SU_PARAGRAF_10 -> no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_10
                        SU_PARAGRAF_11 -> no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_11
                        SU_PARAGRAF_12 -> no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_12
                        SU_PARAGRAF_13 -> no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_13
                        SU_PARAGRAF_17 -> no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_17
                        SU_PARAGRAF_18 -> no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_18
                        SU_PARAGRAF_21 -> no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_21
                    }
                }

                companion object {
                    fun Hjemler.toDatabasetype(): List<String> {
                        return this.map { hjemmel ->
                            when (hjemmel) {
                                no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_3 -> SU_PARAGRAF_3
                                no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_4 -> SU_PARAGRAF_4
                                no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_5 -> SU_PARAGRAF_5
                                no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_6 -> SU_PARAGRAF_6
                                no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_7 -> SU_PARAGRAF_7
                                no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_8 -> SU_PARAGRAF_8
                                no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_9 -> SU_PARAGRAF_9
                                no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_10 -> SU_PARAGRAF_10
                                no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_11 -> SU_PARAGRAF_11
                                no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_12 -> SU_PARAGRAF_12
                                no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_13 -> SU_PARAGRAF_13
                                no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_17 -> SU_PARAGRAF_17
                                no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_18 -> SU_PARAGRAF_18
                                no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_21 -> SU_PARAGRAF_21
                            }.toString()
                        }
                    }

                    fun fromString(value: String): Hjemmel {
                        return values().find { it.verdi == value }
                            ?: throw IllegalStateException("Ukjent omgjøringsutfall i klage-tabellen: $value")
                    }
                }

                override fun toString() = verdi
            }

            override fun toDomain(): VurderingerTilKlage.Vedtaksvurdering {
                return VurderingerTilKlage.Vedtaksvurdering.createOppretthold(
                    hjemler = hjemler.map { Hjemmel.fromString(it).toDomain() },
                ).getOrNull()!!
            }
        }

        companion object {
            fun VurderingerTilKlage.Vedtaksvurdering.toJson(): String {
                return when (this) {
                    is VurderingerTilKlage.Vedtaksvurdering.Påbegynt.Omgjør -> Omgjør(
                        årsak = årsak?.toDatabasetype(),
                        utfall = utfall?.toDatabasetype(),
                    )
                    is VurderingerTilKlage.Vedtaksvurdering.Påbegynt.Oppretthold -> Oppretthold(
                        hjemler = hjemler.toDatabasetype(),
                    )
                    is VurderingerTilKlage.Vedtaksvurdering.Utfylt.Omgjør -> Omgjør(
                        årsak = årsak.toDatabasetype(),
                        utfall = utfall.toDatabasetype(),
                    )
                    is VurderingerTilKlage.Vedtaksvurdering.Utfylt.Oppretthold -> Oppretthold(
                        hjemler = hjemler.toDatabasetype(),
                    )
                }.let {
                    serialize(it)
                }
            }
        }
    }
}
