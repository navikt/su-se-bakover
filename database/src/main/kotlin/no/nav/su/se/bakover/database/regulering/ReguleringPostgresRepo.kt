package no.nav.su.se.bakover.database.regulering

import behandling.revurdering.domain.GrunnlagsdataOgVilkårsvurderingerRevurdering
import beregning.domain.Beregning
import beregning.domain.BeregningMedFradragBeregnetMånedsvis
import kotliquery.Row
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.deserializeList
import no.nav.su.se.bakover.common.deserializeNullable
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresTransactionContext.Companion.withTransaction
import no.nav.su.se.bakover.common.infrastructure.persistence.Session
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.database.beregning.deserialiserBeregning
import no.nav.su.se.bakover.database.grunnlag.GrunnlagsdataOgVilkårsvurderingerPostgresRepo
import no.nav.su.se.bakover.database.revurdering.RevurderingsType
import no.nav.su.se.bakover.database.simulering.deserializeNullableSimulering
import no.nav.su.se.bakover.database.simulering.serializeNullableSimulering
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingStatusDB
import no.nav.su.se.bakover.domain.regulering.AvsluttetRegulering
import no.nav.su.se.bakover.domain.regulering.IverksattRegulering
import no.nav.su.se.bakover.domain.regulering.OpprettetRegulering
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.regulering.ReguleringId
import no.nav.su.se.bakover.domain.regulering.ReguleringMerknad
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringSomKreverManuellBehandling
import no.nav.su.se.bakover.domain.regulering.Reguleringer
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.regulering.ÅrsakTilManuellRegulering
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import satser.domain.supplerendestønad.SatsFactoryForSupplerendeStønad
import økonomi.domain.simulering.Simulering
import java.util.UUID

internal class ReguleringPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val grunnlagsdataOgVilkårsvurderingerPostgresRepo: GrunnlagsdataOgVilkårsvurderingerPostgresRepo,
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
                """
                select
                  s.saksnummer,
                  s.fnr,
                  r.id,
                  case when exists (
                    select 1
                    from grunnlag_fradrag g
                    where g.behandlingid = r.id
                      and g.fradragstype = 'Fosterhjemsgodtgjørelse'
                  ) then true else false end as har_fosterhjemsgodtgjørelse
                from regulering r
                join sak s on r.sakid = s.id
                where r.reguleringstatus = 'OPPRETTET' and r.reguleringtype = 'MANUELL'
                """.trimIndent().hentListe(
                    emptyMap(),
                    session,
                ) {
                    val harFosterhjemsgodtgjørelse = it.boolean("har_fosterhjemsgodtgjørelse")
                    ReguleringSomKreverManuellBehandling(
                        saksnummer = Saksnummer(it.long("saksnummer")),
                        fnr = Fnr(it.string("fnr")),
                        reguleringId = ReguleringId(it.uuid("id")),
                        merknader = if (harFosterhjemsgodtgjørelse) listOf(ReguleringMerknad.Fosterhjemsgodtgjørelse) else emptyList(),
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

    // TODO jah: Flytte til [SakPostgresRepo.kt]
    override fun hentSakerMedÅpenBehandlingEllerStans(): List<Saksnummer> {
        return dbMetrics.timeQuery("hentSakerMedÅpenBehandlingEllerStans") {
            sessionFactory.withSession { session ->
                """
                select saksnummer
                from behandling
                         left join sak s on sakid = s.id
                where status in (${SøknadsbehandlingStatusDB.åpneBeregnetSøknadsbehandlingerKommaseparert()}) and lukket is false

                union

                select saksnummer
                from revurdering
                         left join sak s on s.id = sakid
                where revurderingstype in (${RevurderingsType.åpneRevurderingstyperKommaseparert()}) and avsluttet is null

                union

                select s.saksnummer
                from vedtak v
                         inner join behandling_vedtak bv on v.id = bv.vedtakid
                         inner join sak s on bv.sakid = s.id
                         inner join (select s.saksnummer, max(v.opprettet) as vedtaksdato
                                     from vedtak v
                                              inner join behandling_vedtak bv on v.id = bv.vedtakid
                                              inner join sak s on bv.sakid = s.id group by s.id) sisteVedtak on
                            sisteVedtak.saksnummer = s.saksnummer and sisteVedtak.vedtaksdato = v.opprettet
                where v.vedtaktype = 'STANS_AV_YTELSE';
                """.trimIndent().hentListe(mapOf(), session) {
                    Saksnummer(it.long("saksnummer"))
                }
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
                avsluttet
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
                to_jsonb(:avsluttet::jsonb)
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
                avsluttet=to_jsonb(:avsluttet::jsonb)
                """.trimIndent()
                    .insert(
                        mapOf(
                            "id" to regulering.id.value,
                            "sakId" to regulering.sakId,
                            "periode" to serialize(regulering.periode),
                            "opprettet" to regulering.opprettet,
                            "saksbehandler" to regulering.saksbehandler.navIdent,
                            "beregning" to regulering.beregning,
                            "simulering" to regulering.simulering.serializeNullableSimulering(),
                            "reguleringType" to when (regulering.reguleringstype) {
                                is Reguleringstype.AUTOMATISK -> ReguleringstypeDb.AUTOMATISK.name
                                is Reguleringstype.MANUELL -> ReguleringstypeDb.MANUELL.name
                            },
                            "arsakForManuell" to when (val type = regulering.reguleringstype) {
                                is Reguleringstype.AUTOMATISK -> null
                                is Reguleringstype.MANUELL -> type.problemer.toList().serialize()
                            },
                            "reguleringStatus" to when (regulering) {
                                is IverksattRegulering -> ReguleringStatus.IVERKSATT
                                is OpprettetRegulering -> ReguleringStatus.OPPRETTET
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
                                is OpprettetRegulering -> null
                            },
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
        val årsakForManuell = stringOrNull("arsakForManuell")?.deserializeList<ÅrsakTilManuellRegulering>()?.toSet()

        val type = when (reguleringstype) {
            ReguleringstypeDb.MANUELL -> Reguleringstype.MANUELL(årsakForManuell ?: emptySet())
            ReguleringstypeDb.AUTOMATISK -> Reguleringstype.AUTOMATISK
        }

        val sakstype = Sakstype.from(string("type"))
        val beregning: BeregningMedFradragBeregnetMånedsvis? = stringOrNull("beregning")?.deserialiserBeregning(
            satsFactory = satsFactory,
            sakstype = sakstype,
            saksnummer = saksnummer,
        )
        val simulering = stringOrNull("simulering").deserializeNullableSimulering()
        val saksbehandler = NavIdentBruker.Saksbehandler(string("saksbehandler"))
        val periode = deserialize<Periode>(string("periode"))

        // Merk at denne ikke inneholder eksterneGrunnlag
        val grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderingerPostgresRepo.hentForRevurdering(
            // grunnlagsdata og vilkårsvurderinger er av 2 typer, søknadsbehandling & revurdering
            // men det er slik at regulering tar i bruk revurderingstypen, og blir da lagret på sin in.
            // vi konverterer derfor reguleringId til revurderingId for henting av informasjon
            revurderingId = RevurderingId(id.value),
            session = session,
            sakstype = Sakstype.from(string("type")),
        )

        val avsluttet = deserializeNullable<AvsluttetReguleringJson>(stringOrNull("avsluttet"))
        // TODO - må migrere inn en ny kolonne som kan ha supplementet
        // vi vil også ha en ny tabell som skal ha hele CSV'en.
        // supplementet i hver regulering vil peke til CSV'en den ble hentet fra
        // TODO - fiks når vi må lagre dem inn i basen
        val eksternSupplementRegulering = EksternSupplementReguleringJson(bruker = null, eps = emptyList())

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
            avsluttetReguleringJson = avsluttet,
            sakstype = sakstype,
            eksternSupplementRegulering = eksternSupplementRegulering,
        )
    }

    private enum class ReguleringStatus {
        OPPRETTET,
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
        eksternSupplementRegulering: EksternSupplementReguleringJson,
    ): Regulering {
        val opprettetRegulering = OpprettetRegulering(
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
            reguleringstype = reguleringstype,
            sakstype = sakstype,
            eksternSupplementRegulering = eksternSupplementRegulering.toDomain(),
        )

        return when (status) {
            ReguleringStatus.OPPRETTET -> opprettetRegulering
            ReguleringStatus.IVERKSATT -> IverksattRegulering(
                opprettetRegulering = opprettetRegulering,
                beregning = beregning!!,
                simulering = simulering!!,
            )

            ReguleringStatus.AVSLUTTET -> AvsluttetRegulering(
                opprettetRegulering = opprettetRegulering,
                avsluttetTidspunkt = avsluttetReguleringJson!!.tidspunkt,
                avsluttetAv = avsluttetReguleringJson.avsluttetAv?.let { NavIdentBruker.Saksbehandler(it) },
            )
        }
    }
}
