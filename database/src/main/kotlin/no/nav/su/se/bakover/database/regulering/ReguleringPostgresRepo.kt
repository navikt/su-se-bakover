package no.nav.su.se.bakover.database.regulering

import behandling.revurdering.domain.GrunnlagsdataOgVilkårsvurderingerRevurdering
import beregning.domain.Beregning
import beregning.domain.BeregningMedFradragBeregnetMånedsvis
import kotliquery.Row
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.deserializeNullable
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PeriodeDbJson
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresTransactionContext.Companion.withTransaction
import no.nav.su.se.bakover.common.infrastructure.persistence.Session
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.inClauseWith
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.infrastructure.persistence.toDbJson
import no.nav.su.se.bakover.common.infrastructure.persistence.toDomain
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.database.attestering.toAttesteringshistorikk
import no.nav.su.se.bakover.database.attestering.toDatabaseJson
import no.nav.su.se.bakover.database.beregning.deserialiserBeregning
import no.nav.su.se.bakover.database.grunnlag.FradragsgrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.GrunnlagsdataOgVilkårsvurderingerPostgresRepo
import no.nav.su.se.bakover.database.simulering.deserializeNullableSimulering
import no.nav.su.se.bakover.database.simulering.serializeNullableSimulering
import no.nav.su.se.bakover.domain.regulering.AvsluttetRegulering
import no.nav.su.se.bakover.domain.regulering.EksterntRegulerteBeløp
import no.nav.su.se.bakover.domain.regulering.IverksattRegulering
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.regulering.ReguleringId
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringSomKreverManuellBehandling
import no.nav.su.se.bakover.domain.regulering.ReguleringUnderBehandling
import no.nav.su.se.bakover.domain.regulering.ReguleringUnderBehandling.BeregnetRegulering
import no.nav.su.se.bakover.domain.regulering.ReguleringUnderBehandling.OpprettetRegulering
import no.nav.su.se.bakover.domain.regulering.ReguleringUnderBehandling.TilAttestering
import no.nav.su.se.bakover.domain.regulering.Reguleringer
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import satser.domain.supplerendestønad.SatsFactoryForSupplerendeStønad
import økonomi.domain.simulering.Simulering
import java.time.Year
import java.util.UUID

internal class ReguleringPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val grunnlagsdataOgVilkårsvurderingerPostgresRepo: GrunnlagsdataOgVilkårsvurderingerPostgresRepo,
    private val fradragsgrunnlagPostgresRepo: FradragsgrunnlagPostgresRepo,
    private val dbMetrics: DbMetrics,
    private val satsFactory: SatsFactoryForSupplerendeStønad,
) : ReguleringRepo {
    override fun hent(id: ReguleringId): Regulering? {
        return sessionFactory.withSession { session ->
            hent(id, session)
        }
    }

    override fun hentStatusForÅpneManuelleReguleringer(): List<ReguleringSomKreverManuellBehandling> =
        dbMetrics.timeQuery("hentReguleringerSomIkkeErIverksatt") {
            sessionFactory.withSession { session ->
                // Steg 1: Hent og lag ReguleringSomKreverManuellBehandling med tomme fradrag
                val reguleringer = """
                    SELECT s.saksnummer, s.fnr, r.id, r.arsakForManuell
                    FROM regulering r
                    JOIN sak s ON r.sakid = s.id
                    WHERE r.reguleringstatus = ANY(:statuses)
                      AND r.reguleringtype = :reguleringType
                """.trimIndent()
                    .hentListe(
                        mapOf(
                            "statuses" to session.inClauseWith(openStatuses),
                            "reguleringType" to ReguleringstypeDb.MANUELL.name,
                        ),
                        session,
                    ) {
                        val reguleringId = ReguleringId(it.uuid("id"))
                        reguleringId to ReguleringSomKreverManuellBehandling(
                            saksnummer = Saksnummer(it.long("saksnummer")),
                            fnr = Fnr(it.string("fnr")),
                            reguleringId = reguleringId,
                            fradragsKategori = emptyList(),
                            årsakTilManuellRegulering = ÅrsakTilManuellReguleringJson.toDomain(it.string("arsakForManuell"))
                                .map { it.kategori },
                        )
                    }
                    .associate { it }

                if (reguleringer.isEmpty()) return@withSession emptyList()

                // Steg 2: Batch-last alle fradrag og berik
                val fradragPerRegulering = fradragsgrunnlagPostgresRepo.hentFradragsgrunnlagForBehandlingIds(
                    reguleringer.keys.toList(),
                    session,
                )

                reguleringer.map { (reguleringId, regulering) ->
                    val fradrag = fradragPerRegulering[reguleringId]
                        ?.map { it.fradrag.fradragstype.kategori }
                        ?.distinct()
                        ?: emptyList()
                    regulering.copy(fradragsKategori = fradrag)
                }
            }
        }

    override fun hentStatusForÅpneManuelleReguleringerEnkel(): List<ReguleringSomKreverManuellBehandling> =
        dbMetrics.timeQuery("hentReguleringerSomIkkeErIverksattEnkel") {
            sessionFactory.withSession { session ->
                """
                    SELECT s.saksnummer, s.fnr, r.id, r.arsakForManuell
                    FROM regulering r
                    JOIN sak s ON r.sakid = s.id
                    WHERE r.reguleringstatus = ANY(:statuses)
                      AND r.reguleringtype = :reguleringType
                """.trimIndent().hentListe(
                    mapOf(
                        "statuses" to session.inClauseWith(openStatuses),
                        "reguleringType" to ReguleringstypeDb.MANUELL.name,
                    ),
                    session,
                ) {
                    ReguleringSomKreverManuellBehandling(
                        saksnummer = Saksnummer(it.long("saksnummer")),
                        fnr = Fnr(it.string("fnr")),
                        reguleringId = ReguleringId(it.uuid("id")),
                        fradragsKategori = emptyList(),
                        årsakTilManuellRegulering = emptyList(),
                    )
                }
            }
        }

    override fun hentForSakId(sakId: UUID, sessionContext: SessionContext): Reguleringer =
        dbMetrics.timeQuery("hentReguleringerForSakId") {
            sessionContext.withSession { session ->
                """ select * from regulering r inner join sak s on s.id = r.sakid where sakid = :sakid order by r.opprettet""".trimIndent()
                    .hentListe(
                        mapOf("sakid" to sakId),
                        session,
                    ) { it.toRegulering(session) }.let {
                        Reguleringer(sakId = sakId, behandlinger = it)
                    }
            }
        }

    internal fun hent(id: ReguleringId, session: Session): Regulering? =
        dbMetrics.timeQuery("hentReguleringFraId") {
            """
            select *
            from regulering r

            inner join sak s
            on s.id = r.sakId

            where r.id = :id
            """.trimIndent()
                .hent(mapOf("id" to id.value), session) { row ->
                    row.toRegulering(session)
                }
        }

    override fun lagre(regulering: Regulering, sessionContext: TransactionContext) {
        dbMetrics.timeQuery("lagreRegulering") {
            sessionContext.withTransaction { session ->
                """
            insert into regulering (
                id,
                sakId,
                opprettet,
                periode,
                beregning,
                simulering,
                saksbehandler,
                reguleringStatus,
                reguleringType,
                arsakForManuell,
                avsluttet,
                attestering, 
                eksternt_regulerte_belop,
                oppgave_id
            ) values (
                :id,
                :sakId,
                :opprettet,
                to_json(:periode::json),
                to_json(:beregning::json),
                to_json(:simulering::json),
                :saksbehandler,
                :reguleringStatus,
                :reguleringType,
                to_jsonb(:arsakForManuell::jsonb),
                to_jsonb(:avsluttet::jsonb),
                to_jsonb(:attestering::jsonb),
                to_jsonb(:eksternt_regulerte_belop::jsonb),
                :oppgaveId
            )
                ON CONFLICT(id) do update set
                id=:id,
                sakId=:sakId,
                opprettet=:opprettet,
                periode=to_json(:periode::json),
                beregning=to_json(:beregning::json),
                simulering=to_json(:simulering::json),
                saksbehandler=:saksbehandler,
                reguleringStatus=:reguleringStatus,
                reguleringType=:reguleringType,
                arsakForManuell=to_jsonb(:arsakForManuell::jsonb),
                avsluttet=to_jsonb(:avsluttet::jsonb),
                eksternt_regulerte_belop=to_jsonb(:eksternt_regulerte_belop::jsonb),
                attestering=to_jsonb(:attestering::jsonb),
                oppgave_id=:oppgaveId
                """.trimIndent()
                    .insert(
                        mapOf(
                            "id" to regulering.id.value,
                            "sakId" to regulering.sakId,
                            "periode" to serialize(regulering.periode.toDbJson()),
                            "opprettet" to regulering.opprettet,
                            "saksbehandler" to regulering.saksbehandler.navIdent,
                            "beregning" to regulering.beregning,
                            "simulering" to regulering.simulering.serializeNullableSimulering(),
                            "reguleringType" to when (regulering.reguleringstype) {
                                is Reguleringstype.AUTOMATISK -> ReguleringstypeDb.AUTOMATISK.name
                                is Reguleringstype.MANUELL -> ReguleringstypeDb.MANUELL.name
                            },
                            "arsakForManuell" to regulering.reguleringstype.årsakerTilManuellReguleringJson(),
                            "reguleringStatus" to when (regulering) {
                                is OpprettetRegulering -> ReguleringStatus.OPPRETTET
                                is BeregnetRegulering -> ReguleringStatus.BEREGNET
                                is TilAttestering -> ReguleringStatus.ATTESTERING
                                is IverksattRegulering -> ReguleringStatus.IVERKSATT
                                is AvsluttetRegulering -> ReguleringStatus.AVSLUTTET
                            }.toString(),
                            "avsluttet" to when (regulering) {
                                is AvsluttetRegulering -> {
                                    serialize(
                                        AvsluttetReguleringJson(
                                            regulering.avsluttetTidspunkt,
                                            regulering.avsluttetAv?.navIdent,
                                        ),
                                    )
                                }

                                is IverksattRegulering -> null
                                is ReguleringUnderBehandling -> null
                            },
                            "attestering" to when (regulering) {
                                is AvsluttetRegulering -> regulering.opprettetRegulering.attesteringer.toDatabaseJson()
                                is IverksattRegulering -> regulering.opprettetRegulering.attesteringer.toDatabaseJson()
                                is ReguleringUnderBehandling -> regulering.attesteringer.toDatabaseJson()
                            },
                            "eksternt_regulerte_belop" to regulering.eksterntRegulerteBeløp?.let { serialize(it) },
                            "oppgave_id" to if (regulering is ReguleringUnderBehandling) regulering.oppgaveId else null,
                        ),
                        session,
                    )
                grunnlagsdataOgVilkårsvurderingerPostgresRepo.lagre(
                    behandlingId = regulering.id,
                    grunnlagsdataOgVilkårsvurderinger = regulering.grunnlagsdataOgVilkårsvurderinger,
                    tx = session,
                )
            }
        }
    }

    override fun markerSomIkkeSendtTilOppdrag(id: ReguleringId, sessionContext: TransactionContext?) {
        dbMetrics.timeQuery("markerReguleringIkkeSendtTilOppdrag") {
            val ctx = sessionContext ?: sessionFactory.newTransactionContext()
            ctx.withTransaction { session ->
                "UPDATE regulering SET erSendtTilOppdrag = false WHERE id = :id"
                    .oppdatering(mapOf("id" to id.value), session)
            }
        }
    }

    override fun markerSomSendtTilOppdrag(id: ReguleringId, sessionContext: TransactionContext?) {
        dbMetrics.timeQuery("markerReguleringErSendtTilOppdrag") {
            val ctx = sessionContext ?: sessionFactory.newTransactionContext()
            ctx.withTransaction { session ->
                "UPDATE regulering SET erSendtTilOppdrag = true WHERE id = :id"
                    .oppdatering(mapOf("id" to id.value), session)
            }
        }
    }

    override fun hentIverksatteReguleringerSomIkkeErSendtTilOppdrag(år: Year): List<IverksattRegulering> {
        return dbMetrics.timeQuery("hentIverksatteReguleringerSomIkkeErSendtTilOppdrag") {
            sessionFactory.withSession { session ->
                """
                    SELECT r.*, s.saksnummer, s.fnr, s.type
                    FROM regulering r
                    JOIN sak s ON s.id = r.sakid
                    WHERE r.reguleringstatus = 'IVERKSATT'
                      AND r.erSendtTilOppdrag = false
                      AND EXTRACT(YEAR FROM r.opprettet) = :aar
                """.trimIndent()
                    .hentListe(mapOf("aar" to år.value), session) { it.toRegulering(session) }
                    .filterIsInstance<IverksattRegulering>()
            }
        }
    }

    override fun defaultSessionContext(): SessionContext {
        return sessionFactory.newSessionContext()
    }

    override fun defaultTransactionContext(): TransactionContext {
        return sessionFactory.newTransactionContext()
    }

    private enum class ReguleringstypeDb {
        MANUELL,
        AUTOMATISK,
    }

    private fun Row.toRegulering(session: Session): Regulering {
        val sakId = uuid("sakid")
        val id = ReguleringId(uuid("id"))
        val opprettet = tidspunkt("opprettet")
        val saksnummer = Saksnummer(long("saksnummer"))
        val fnr = Fnr(string("fnr"))
        val status = ReguleringStatus.valueOf(string("reguleringStatus"))
        val reguleringstype = ReguleringstypeDb.valueOf(string("reguleringType"))
        val årsakForManuell = ÅrsakTilManuellReguleringJson.toDomain(string("arsakForManuell"))
        val type = when (reguleringstype) {
            ReguleringstypeDb.MANUELL -> Reguleringstype.MANUELL(årsakForManuell)
            ReguleringstypeDb.AUTOMATISK -> Reguleringstype.AUTOMATISK
        }

        val sakstype = Sakstype.from(string("type"))
        val avbrutt = deserializeNullable<AvsluttetReguleringJson>(stringOrNull("avsluttet"))
        val beregning: BeregningMedFradragBeregnetMånedsvis? = stringOrNull("beregning")?.deserialiserBeregning(
            satsFactory = satsFactory,
            sakstype = sakstype,
            saksnummer = saksnummer,
            erAvbrutt = avbrutt != null,
        )
        val simulering = stringOrNull("simulering").deserializeNullableSimulering()
        val saksbehandler = NavIdentBruker.Saksbehandler(string("saksbehandler"))
        val periode = deserialize<PeriodeDbJson>(string("periode")).toDomain()

        // Merk at denne ikke inneholder eksterneGrunnlag
        val grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderingerPostgresRepo.hentForRevurdering(
            // grunnlagsdata og vilkårsvurderinger er av 2 typer, søknadsbehandling & revurdering
            // men det er slik at regulering tar i bruk revurderingstypen, og blir da lagret på sin id.
            // vi konverterer derfor reguleringId til revurderingId for henting av informasjon
            revurderingId = RevurderingId(id.value),
            session = session,
            sakstype = Sakstype.from(string("type")),
        )
        val attesteringer = stringOrNull("attestering")?.toAttesteringshistorikk() ?: Attesteringshistorikk.empty()
        val eksterntRegulerteBeløp =
            stringOrNull("eksternt_regulerte_belop")?.let { deserialize<EksterntRegulerteBeløp>(it) }
        val erSendtTilOppdrag = boolean("erSendtTilOppdrag")

        return lagRegulering(
            status = status,
            id = id,
            opprettet = opprettet,
            sakId = sakId,
            saksnummer = saksnummer,
            saksbehandler = saksbehandler,
            fnr = fnr,
            periode = periode,
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
            beregning = beregning,
            simulering = simulering,
            reguleringstype = type,
            avsluttetReguleringJson = avbrutt,
            sakstype = sakstype,
            attesteringer = attesteringer,
            eksterntRegulerteBeløp = eksterntRegulerteBeløp,
            erSendtTilOppdrag = erSendtTilOppdrag,
        )
    }

    private enum class ReguleringStatus {
        OPPRETTET,
        BEREGNET,
        ATTESTERING,
        IVERKSATT,
        AVSLUTTET,
    }

    private fun lagRegulering(
        status: ReguleringStatus,
        id: ReguleringId,
        opprettet: Tidspunkt,
        sakId: UUID,
        saksnummer: Saksnummer,
        saksbehandler: NavIdentBruker.Saksbehandler,
        fnr: Fnr,
        periode: Periode,
        grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerRevurdering,
        beregning: Beregning?,
        simulering: Simulering?,
        reguleringstype: Reguleringstype,
        avsluttetReguleringJson: AvsluttetReguleringJson?,
        sakstype: Sakstype,
        attesteringer: Attesteringshistorikk,
        eksterntRegulerteBeløp: EksterntRegulerteBeløp?,
        erSendtTilOppdrag: Boolean,
        oppgaveId: OppgaveId? = null,
    ): Regulering {
        val eksterntRegulerteBeløp =
            if (eksterntRegulerteBeløp == null && (status == ReguleringStatus.IVERKSATT || status == ReguleringStatus.AVSLUTTET)) {
                EksterntRegulerteBeløp(
                    brukerFnr = fnr,
                    beløpBruker = emptyList(),
                    beløpEps = emptyList(),
                    inntektEtterUføre = null,
                )
            } else {
                eksterntRegulerteBeløp
                    ?: throw IllegalStateException("Regulering under behandling mangler eksternt regulerte beløp")
            }
        return OpprettetRegulering(
            id = id,
            opprettet = opprettet,
            sakId = sakId,
            saksnummer = saksnummer,
            saksbehandler = saksbehandler,
            fnr = fnr,
            periode = periode,
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
            reguleringstype = reguleringstype,
            sakstype = sakstype,
            attesteringer = attesteringer,
            eksterntRegulerteBeløp = eksterntRegulerteBeløp,
            oppgaveId = oppgaveId,
        ).let { regulering ->
            when (status) {
                ReguleringStatus.OPPRETTET -> regulering
                ReguleringStatus.BEREGNET -> regulering.tilBeregnet(
                    beregning ?: throw ReguleringPostgresManglerBeregningEllersimulering(),
                    simulering ?: throw ReguleringPostgresManglerBeregningEllersimulering(),
                )

                ReguleringStatus.ATTESTERING -> regulering.tilBeregnet(
                    beregning ?: throw ReguleringPostgresManglerBeregningEllersimulering(),
                    simulering ?: throw ReguleringPostgresManglerBeregningEllersimulering(),
                ).tilAttestering(saksbehandler, oppgaveId)

                ReguleringStatus.IVERKSATT -> IverksattRegulering(
                    opprettetRegulering = regulering.tilBeregnet(
                        beregning ?: throw ReguleringPostgresManglerBeregningEllersimulering(),
                        simulering ?: throw ReguleringPostgresManglerBeregningEllersimulering(),
                    ).tilAttestering(saksbehandler, oppgaveId),
                    beregning = beregning,
                    simulering = simulering,
                    erSendtTilOppdrag = erSendtTilOppdrag,
                )

                ReguleringStatus.AVSLUTTET -> AvsluttetRegulering(
                    opprettetRegulering = regulering,
                    avsluttetTidspunkt = avsluttetReguleringJson?.tidspunkt
                        ?: throw IllegalStateException("Avsluttet regulering mangler avsluttetReguleringJson"),
                    avsluttetAv = avsluttetReguleringJson.avsluttetAv?.let { NavIdentBruker.Saksbehandler(it) },
                )
            }
        }
    }

    companion object {
        private val openStatuses = listOf(
            ReguleringStatus.OPPRETTET.name,
            ReguleringStatus.BEREGNET.name,
            ReguleringStatus.ATTESTERING.name,
        )
    }
}

class ReguleringPostgresManglerBeregningEllersimulering : IllegalStateException()
