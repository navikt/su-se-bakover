package no.nav.su.se.bakover.database.vedtak

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.database.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.database.PostgresSessionFactory
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.beregning.PersistertBeregning
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.klage.KlagePostgresRepo
import no.nav.su.se.bakover.database.revurdering.RevurderingPostgresRepo
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingPostgresRepo
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.database.uuid30OrNull
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.IverksattAvvistKlage
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.AutomatiskEllerManuelleSak
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.domain.vedtak.BehandlingType
import no.nav.su.se.bakover.domain.vedtak.Klagevedtak
import no.nav.su.se.bakover.domain.vedtak.Stønadsvedtak
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vedtak.VedtakType.valueOf
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource

internal enum class VedtakType {
    SØKNAD, // Innvilget Søknadsbehandling                  -> EndringIYtelse
    AVSLAG, // Avslått Søknadsbehandling                    -> Avslag
    ENDRING, // Revurdering innvilget                       -> EndringIYtelse
    INGEN_ENDRING, // Revurdering mellom 2% og 10% endring  -> IngenEndringIYtelse
    OPPHØR, // Revurdering ført til opphør                  -> EndringIYtelse
    STANS_AV_YTELSE,
    GJENOPPTAK_AV_YTELSE,
    AVVIST_KLAGE,
}

internal class VedtakPostgresRepo(
    private val dataSource: DataSource,
    private val søknadsbehandlingRepo: SøknadsbehandlingPostgresRepo,
    private val revurderingRepo: RevurderingPostgresRepo,
    private val klageRepo: KlagePostgresRepo,
    private val dbMetrics: DbMetrics,
    private val sessionFactory: PostgresSessionFactory,
) : VedtakRepo {

    override fun hentForVedtakId(vedtakId: UUID): Vedtak? {
        return sessionFactory.withSession { session ->
            """
                SELECT *
                FROM vedtak
                WHERE id = :id
            """.trimIndent()
                .hent(mapOf("id" to vedtakId), session) {
                    it.toVedtak(session)
                }
        }
    }

    override fun hentForSakId(sakId: UUID): List<Vedtak> {
        return dbMetrics.timeQuery("hentVedtakForSakId") {
            dataSource.withSession { session -> hentForSakId(sakId, session) }
        }
    }

    internal fun hentForSakId(sakId: UUID, session: Session): List<Vedtak> =
        """
            SELECT v.*
            FROM vedtak v
            JOIN behandling_vedtak bv ON bv.vedtakid = v.id
            WHERE bv.sakId = :sakId
        """.trimIndent()
            .hentListe(mapOf("sakId" to sakId), session) {
                it.toVedtak(session)
            }

    override fun lagre(vedtak: Vedtak) =
        sessionFactory.withTransactionContext { tx ->
            lagre(vedtak, tx)
        }

    override fun lagre(vedtak: Vedtak, sessionContext: TransactionContext) {
        return sessionContext.withSession { tx ->
            when (vedtak) {
                is VedtakSomKanRevurderes.EndringIYtelse -> lagreInternt(vedtak, tx)
                is Avslagsvedtak -> lagreInternt(vedtak, tx)
                is VedtakSomKanRevurderes.IngenEndringIYtelse -> lagreInternt(vedtak, tx)
                is Klagevedtak.Avvist -> lagreInternt(vedtak, tx)
            }
        }
    }

    /**
     * Det er kun [VedtakSomKanRevurderes.EndringIYtelse] som inneholder en utbetalingId
     */
    override fun hentForUtbetaling(utbetalingId: UUID30): VedtakSomKanRevurderes.EndringIYtelse? {
        return dataSource.withSession { session ->
            """
                SELECT *
                FROM vedtak
                WHERE utbetalingId = :utbetalingId
            """.trimIndent()
                .hent(mapOf("utbetalingId" to utbetalingId), session) {
                    it.toVedtak(session) as VedtakSomKanRevurderes.EndringIYtelse
                }
        }
    }

    override fun hentAlle(): List<Vedtak> {
        return dataSource.withSession { session ->
            """select * from vedtak""".hentListe(emptyMap(), session) { it.toVedtak(session) }
        }
    }

    override fun hentJournalpostId(vedtakId: UUID): JournalpostId? {
        return dataSource.withSession { session ->
            """
                select journalpostid from dokument inner join dokument_distribusjon dd on dokument.id = dd.dokumentid where vedtakid = :vedtakId
            """.trimIndent().hent(mapOf("vedtakId" to vedtakId), session) {
                JournalpostId(it.string("journalpostid"))
            }
        }
    }

    internal fun hent(id: UUID, session: Session) =
        """
            select * from vedtak where id = :id
        """.trimIndent()
            .hent(mapOf("id" to id), session) {
                it.toVedtak(session)
            }

    override fun hentAktive(dato: LocalDate): List<VedtakSomKanRevurderes.EndringIYtelse> =
        dataSource.withSession { session ->
            """
            select * from vedtak 
            where fraogmed <= :dato
              and tilogmed >= :dato
            order by fraogmed, tilogmed, opprettet

            """.trimIndent()
                .hentListe(mapOf("dato" to dato), session) {
                    it.toVedtak(session)
                }.filterIsInstance<VedtakSomKanRevurderes.EndringIYtelse>()
        }

    override fun hentVedtakSomKanReguleres(dato: LocalDate): List<AutomatiskEllerManuelleSak> {
        return dataSource.withSession { session ->
            """
                with sakogid (sakid, saksnummer, bid, fraOgMed, tilOgMed, vedtaktype, opprettet ) as (
                    select bv.sakid
                         , s.saksnummer
                         , coalesce(bv.søknadsbehandlingid, bv.revurderingid)
                         , v.fraogmed
                         , v.tilogmed
                         , v.vedtaktype
                         , v.opprettet
                    from behandling_vedtak bv
                
                    inner join vedtak v
                    on bv.vedtakid = v.id
                    
                    inner join sak s
                    on s.id = bv.sakid
                
                    where v.tilogmed >= :dato
                
                )
                
                select s.sakid
                     , s.saksnummer
                     , s.bid
                     , s.fraOgMed
                     , s.tilOgMed
                     , s.vedtaktype
                     , s.opprettet
                     , case when ( EXISTS( select 1
                                             from grunnlag_fradrag g
                                            where g.behandlingid = s.bid
                                              and g.fradragstype in ('NAVytelserTilLivsopphold', 'OffentligPensjon')
                                              and ( not ( (s.tilOgMed < g.fraogmed) or (s.fraOgMed > g.tilogmed) ) )
                         ) ) then 'MANUELL'
                         else 'AUTOMATISK'
                         end behandlingtype
                       from sakogid s
            """.trimIndent()
                .hentListe(mapOf("dato" to dato), session) {
                    it.toAutomatiskEllerManuelleSak()
                }
        }
    }

    private fun Row.toAutomatiskEllerManuelleSak(): AutomatiskEllerManuelleSak {
        val sakId = uuid("sakid")
        val behandlingId = uuid("bid")
        val saksnummer = Saksnummer(long("saksnummer"))
        val opprettet = tidspunkt("opprettet")
        val fraOgMed = localDate("fraOgMed")
        val tilOgMed = localDate("tilOgMed")
        val vedtakType = valueOf(string("vedtaktype")) // todo mappe via vedtakstype i repo
        val behandlingType = BehandlingType.valueOf(string("behandlingtype"))

        return AutomatiskEllerManuelleSak(
            sakId = sakId,
            saksnummer = saksnummer,
            opprettet = opprettet,
            behandlingId = behandlingId,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            vedtakType = vedtakType,
            behandlingType = behandlingType,
        )
    }

    private fun Row.toVedtak(session: Session): Vedtak {
        val id = uuid("id")
        val opprettet = tidspunkt("opprettet")

        val fraOgMed = localDateOrNull("fraOgMed")
        val tilOgMed = localDateOrNull("tilOgMed")
        val periode = when {
            fraOgMed != null && tilOgMed != null -> Periode.create(fraOgMed, tilOgMed)
            (fraOgMed == null) xor (fraOgMed == null) -> throw IllegalStateException("fraOgMed og tilOgMed må enten begge være fylt ut, eller begge være null. fraOgMed=$fraOgMed, tilOgMed=$tilOgMed")
            else -> null
        }
        val knytning = hentBehandlingVedtakKnytning(id, session)
            ?: throw IllegalStateException("Fant ikke knytning mellom vedtak og søknadsbehandling/revurdering.")
        val behandling: Behandling? = when (knytning) {
            is BehandlingVedtakKnytning.ForSøknadsbehandling ->
                søknadsbehandlingRepo.hent(knytning.søknadsbehandlingId, session)!!
            is BehandlingVedtakKnytning.ForRevurdering ->
                revurderingRepo.hent(knytning.revurderingId, session)!!
            is BehandlingVedtakKnytning.ForKlage -> null
        }
        val klage: Klage? = (knytning as? BehandlingVedtakKnytning.ForKlage)?.let {
            klageRepo.hentKlage(knytning.klageId)
        }

        val saksbehandler = stringOrNull("saksbehandler")?.let { NavIdentBruker.Saksbehandler(it) }!!
        val attestant = stringOrNull("attestant")?.let { NavIdentBruker.Attestant(it) }!!
        val utbetalingId = uuid30OrNull("utbetalingId")
        val beregning = stringOrNull("beregning")?.let { objectMapper.readValue<PersistertBeregning>(it) }
        val simulering = stringOrNull("simulering")?.let { objectMapper.readValue<Simulering>(it) }
        val avslagsgrunner = stringOrNull("avslagsgrunner")?.let { objectMapper.readValue<List<Avslagsgrunn>>(it) }

        return when (VedtakType.valueOf(string("vedtaktype"))) {
            VedtakType.SØKNAD -> {
                VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling(
                    id = id,
                    opprettet = opprettet,
                    behandling = behandling as Søknadsbehandling.Iverksatt.Innvilget,
                    saksbehandler = saksbehandler,
                    attestant = attestant,
                    periode = periode!!,
                    beregning = beregning!!,
                    simulering = simulering!!,
                    utbetalingId = utbetalingId!!,
                )
            }
            VedtakType.ENDRING -> {
                VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering(
                    id = id,
                    opprettet = opprettet,
                    behandling = behandling as IverksattRevurdering.Innvilget,
                    saksbehandler = saksbehandler,
                    attestant = attestant,
                    periode = periode!!,
                    beregning = beregning!!,
                    simulering = simulering!!,
                    utbetalingId = utbetalingId!!,
                )
            }
            VedtakType.OPPHØR -> {
                VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering(
                    id = id,
                    opprettet = opprettet,
                    behandling = behandling as IverksattRevurdering.Opphørt,
                    saksbehandler = saksbehandler,
                    attestant = attestant,
                    periode = periode!!,
                    beregning = beregning!!,
                    simulering = simulering!!,
                    utbetalingId = utbetalingId!!,
                )
            }
            VedtakType.AVSLAG -> {
                if (beregning != null) {
                    Avslagsvedtak.AvslagBeregning(
                        id = id,
                        opprettet = opprettet,
                        // AVSLAG gjelder kun for søknadsbehandling
                        behandling = behandling as Søknadsbehandling.Iverksatt.Avslag.MedBeregning,
                        beregning = beregning,
                        saksbehandler = saksbehandler,
                        attestant = attestant,
                        periode = periode!!,
                        // TODO fjern henting fra behandling etter migrering
                        avslagsgrunner = avslagsgrunner ?: behandling.avslagsgrunner,
                    )
                } else {
                    Avslagsvedtak.AvslagVilkår(
                        id = id,
                        opprettet = opprettet,
                        // AVSLAG gjelder kun for søknadsbehandling
                        behandling = behandling as Søknadsbehandling.Iverksatt.Avslag.UtenBeregning,
                        saksbehandler = saksbehandler,
                        attestant = attestant,
                        periode = periode!!,
                        // TODO fjern henting fra behandling etter migrering
                        avslagsgrunner = avslagsgrunner ?: behandling.avslagsgrunner,
                    )
                }
            }
            VedtakType.INGEN_ENDRING -> VedtakSomKanRevurderes.IngenEndringIYtelse(
                id = id,
                opprettet = opprettet,
                behandling = behandling as IverksattRevurdering.IngenEndring,
                saksbehandler = saksbehandler,
                attestant = attestant,
                periode = periode!!,
                beregning = beregning!!,
            )
            VedtakType.STANS_AV_YTELSE -> VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse(
                id = id,
                opprettet = opprettet,
                behandling = behandling as StansAvYtelseRevurdering.IverksattStansAvYtelse,
                saksbehandler = saksbehandler,
                attestant = attestant,
                periode = periode!!,
                simulering = simulering!!,
                utbetalingId = utbetalingId!!,
            )
            VedtakType.GJENOPPTAK_AV_YTELSE -> VedtakSomKanRevurderes.EndringIYtelse.GjenopptakAvYtelse(
                id = id,
                opprettet = opprettet,
                behandling = behandling as GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse,
                saksbehandler = saksbehandler,
                attestant = attestant,
                periode = periode!!,
                simulering = simulering!!,
                utbetalingId = utbetalingId!!,
            )
            VedtakType.AVVIST_KLAGE -> Klagevedtak.Avvist(
                id = id,
                opprettet = opprettet,
                saksbehandler = saksbehandler,
                attestant = attestant,
                klage = klage as IverksattAvvistKlage,
            )
        }
    }

    private fun lagreInternt(vedtak: VedtakSomKanRevurderes.EndringIYtelse, session: Session) {
        """
                INSERT INTO vedtak(
                    id,
                    opprettet,
                    fraOgMed,
                    tilOgMed,
                    saksbehandler,
                    attestant,
                    utbetalingid,
                    simulering,
                    beregning,
                    vedtaktype
                ) VALUES (
                    :id,
                    :opprettet,
                    :fraOgMed,
                    :tilOgMed,
                    :saksbehandler,
                    :attestant,
                    :utbetalingid,
                    to_json(:simulering::json),
                    to_json(:beregning::json),
                    :vedtaktype
                )
        """.trimIndent()
            .insert(
                mapOf(
                    "id" to vedtak.id,
                    "opprettet" to vedtak.opprettet,
                    "fraOgMed" to vedtak.periode.fraOgMed,
                    "tilOgMed" to vedtak.periode.tilOgMed,
                    "saksbehandler" to vedtak.saksbehandler,
                    "attestant" to vedtak.attestant,
                    "utbetalingid" to vedtak.utbetalingId,
                    "simulering" to objectMapper.writeValueAsString(vedtak.simulering),
                    "beregning" to when (vedtak) {
                        is VedtakSomKanRevurderes.EndringIYtelse.GjenopptakAvYtelse ->
                            null
                        is VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse ->
                            null
                        is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering ->
                            vedtak.beregning
                        is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling ->
                            vedtak.beregning
                        is VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering ->
                            vedtak.beregning
                    },
                    "vedtaktype" to when (vedtak) {
                        is VedtakSomKanRevurderes.EndringIYtelse.GjenopptakAvYtelse -> VedtakType.GJENOPPTAK_AV_YTELSE
                        is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering -> VedtakType.ENDRING
                        is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling -> VedtakType.SØKNAD
                        is VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering -> VedtakType.OPPHØR
                        is VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse -> VedtakType.STANS_AV_YTELSE
                    },
                ),
                session,
            )
        lagreKlagevedtakKnytningTilBehandling(vedtak, session)
    }

    private fun lagreInternt(vedtak: Avslagsvedtak, session: Session) {
        val beregning = when (vedtak) {
            is Avslagsvedtak.AvslagBeregning -> vedtak.beregning
            is Avslagsvedtak.AvslagVilkår -> null
        }
        """
                insert into vedtak(
                    id,
                    opprettet,
                    fraOgMed,
                    tilOgMed,
                    saksbehandler,
                    attestant,
                    utbetalingid,
                    simulering,
                    beregning,
                    vedtaktype,
                    avslagsgrunner
                ) values (
                    :id,
                    :opprettet,
                    :fraOgMed,
                    :tilOgMed,
                    :saksbehandler,
                    :attestant,
                    :utbetalingid,
                    to_json(:simulering::json),
                    to_json(:beregning::json),
                    :vedtaktype,
                    to_json(:avslagsgrunner::json)
                )
        """.trimIndent()
            .insert(
                mapOf(
                    "id" to vedtak.id,
                    "opprettet" to vedtak.opprettet,
                    "fraOgMed" to vedtak.periode.fraOgMed,
                    "tilOgMed" to vedtak.periode.tilOgMed,
                    "saksbehandler" to vedtak.saksbehandler,
                    "attestant" to vedtak.attestant,
                    "beregning" to beregning,
                    "vedtaktype" to VedtakType.AVSLAG,
                    "avslagsgrunner" to vedtak.avslagsgrunner.serialize(),
                ),
                session,
            )
        lagreKlagevedtakKnytningTilSøknadsbehandling(vedtak, session)
    }

    private fun lagreInternt(vedtak: VedtakSomKanRevurderes.IngenEndringIYtelse, session: Session) {
        """
                INSERT INTO vedtak(
                    id,
                    opprettet,
                    fraOgMed,
                    tilOgMed,
                    saksbehandler,
                    attestant,
                    utbetalingid,
                    simulering,
                    beregning,
                    vedtaktype
                ) VALUES (
                    :id,
                    :opprettet,
                    :fraOgMed,
                    :tilOgMed,
                    :saksbehandler,
                    :attestant,
                    :utbetalingid,
                    to_json(:simulering::json),
                    to_json(:beregning::json),
                    :vedtaktype
                )
        """.trimIndent()
            .insert(
                mapOf(
                    "id" to vedtak.id,
                    "opprettet" to vedtak.opprettet,
                    "fraOgMed" to vedtak.periode.fraOgMed,
                    "tilOgMed" to vedtak.periode.tilOgMed,
                    "saksbehandler" to vedtak.saksbehandler,
                    "attestant" to vedtak.attestant,
                    "utbetalingid" to null,
                    "simulering" to null,
                    "beregning" to vedtak.beregning,
                    "vedtaktype" to VedtakType.INGEN_ENDRING,
                ),
                session,
            )
        lagreKlagevedtakKnytningTilRevurdering(vedtak, session)
    }

    private fun lagreInternt(vedtak: Klagevedtak.Avvist, session: Session) {
        """
                INSERT INTO vedtak(
                    id,
                    opprettet,
                    fraOgMed,
                    tilOgMed,
                    saksbehandler,
                    attestant,
                    utbetalingid,
                    simulering,
                    beregning,
                    vedtaktype
                ) VALUES (
                    :id,
                    :opprettet,
                    null,
                    null,
                    :saksbehandler,
                    :attestant,
                    null,
                    null,
                    null,
                    :vedtaktype
                )
        """.trimIndent().insert(
            mapOf(
                "id" to vedtak.id,
                "opprettet" to vedtak.opprettet,
                "saksbehandler" to vedtak.saksbehandler,
                "attestant" to vedtak.attestant,
                "vedtaktype" to VedtakType.AVVIST_KLAGE,
            ),
            session,
        )
        lagreKlagevedtakKnytningTilKlage(vedtak, session)
    }
}

/** Kan være en Søknadsbehandling eller Revurdering */
private fun lagreKlagevedtakKnytningTilBehandling(vedtak: Stønadsvedtak, session: Session) {
    when (vedtak.behandling) {
        is AbstraktRevurdering ->
            lagreKlagevedtakKnytningTilRevurdering(vedtak, session)
        is Søknadsbehandling ->
            lagreKlagevedtakKnytningTilSøknadsbehandling(vedtak, session)
        else ->
            throw IllegalArgumentException("vedtak.behandling er av ukjent type. Den må være en revurdering eller en søknadsbehandling.")
    }
}

private fun lagreKlagevedtakKnytningTilRevurdering(vedtak: Stønadsvedtak, session: Session) {
    lagreVedtaksknytning(
        behandlingVedtakKnytning = BehandlingVedtakKnytning.ForRevurdering(
            vedtakId = vedtak.id,
            sakId = vedtak.behandling.sakId,
            revurderingId = vedtak.behandling.id,
        ),
        session = session,
    )
}

private fun lagreKlagevedtakKnytningTilSøknadsbehandling(vedtak: Stønadsvedtak, session: Session) {
    lagreVedtaksknytning(
        behandlingVedtakKnytning = BehandlingVedtakKnytning.ForSøknadsbehandling(
            vedtakId = vedtak.id,
            sakId = vedtak.behandling.sakId,
            søknadsbehandlingId = vedtak.behandling.id,
        ),
        session = session,
    )
}

private fun lagreKlagevedtakKnytningTilKlage(vedtak: Klagevedtak, session: Session) {
    lagreVedtaksknytning(
        behandlingVedtakKnytning = BehandlingVedtakKnytning.ForKlage(
            vedtakId = vedtak.id,
            sakId = vedtak.klage.sakId,
            klageId = vedtak.klage.id,
        ),
        session = session,
    )
}

private fun lagreVedtaksknytning(
    behandlingVedtakKnytning: BehandlingVedtakKnytning,
    session: Session,
) {
    """
        INSERT INTO behandling_vedtak
        (
            id,
            vedtakId,
            sakId,
            søknadsbehandlingId,
            revurderingId,
            klageId
        ) VALUES (
            :id,
            :vedtakId,
            :sakId,
            :soknadsbehandlingId,
            :revurderingId,
            :klageId
        ) ON CONFLICT ON CONSTRAINT unique_vedtakid DO NOTHING
    """.trimIndent().insert(
        mapOf(
            "id" to behandlingVedtakKnytning.id,
            "vedtakId" to behandlingVedtakKnytning.vedtakId,
            "sakId" to behandlingVedtakKnytning.sakId,
            "soknadsbehandlingId" to behandlingVedtakKnytning.søknadsbehandlingId,
            "revurderingId" to behandlingVedtakKnytning.revurderingId,
            "klageId" to behandlingVedtakKnytning.klageId,
        ),
        session,
    )
}

private fun hentBehandlingVedtakKnytning(vedtakId: UUID, session: Session): BehandlingVedtakKnytning? = """
            SELECT *
            FROM behandling_vedtak
            WHERE vedtakId = :vedtakId
""".trimIndent().hent(
    mapOf("vedtakId" to vedtakId),
    session,
) {
    val id = it.uuid("id")
    assert(it.uuid("vedtakId") == vedtakId)
    val sakId = it.uuid("sakId")
    val søknadsbehandlingId = it.stringOrNull("søknadsbehandlingId")
    val revurderingId = it.stringOrNull("revurderingId")
    val klageId = it.stringOrNull("klageId")

    when {
        revurderingId == null && søknadsbehandlingId != null && klageId == null -> {
            BehandlingVedtakKnytning.ForSøknadsbehandling(
                id = id,
                vedtakId = vedtakId,
                sakId = sakId,
                søknadsbehandlingId = UUID.fromString(søknadsbehandlingId),
            )
        }
        revurderingId != null && søknadsbehandlingId == null && klageId == null -> {
            BehandlingVedtakKnytning.ForRevurdering(
                id = id,
                vedtakId = vedtakId,
                sakId = sakId,
                revurderingId = UUID.fromString(revurderingId),
            )
        }
        revurderingId == null && søknadsbehandlingId == null && klageId != null -> {
            BehandlingVedtakKnytning.ForKlage(
                id = id,
                vedtakId = vedtakId,
                sakId = sakId,
                klageId = UUID.fromString(klageId),
            )
        }
        else -> {
            throw IllegalStateException(
                "Fant ugyldig behandling-vedtak-knytning. søknadsbehandlingId=$søknadsbehandlingId, revurderingId=$revurderingId, klageId=$klageId. Én og nøyaktig én av dem må være satt.",
            )
        }
    }
}

private sealed interface BehandlingVedtakKnytning {
    val id: UUID
    val vedtakId: UUID
    val sakId: UUID

    val søknadsbehandlingId: UUID?
    val revurderingId: UUID?
    val klageId: UUID?

    data class ForSøknadsbehandling(
        override val id: UUID = UUID.randomUUID(),
        override val vedtakId: UUID,
        override val sakId: UUID,
        override val søknadsbehandlingId: UUID,
    ) : BehandlingVedtakKnytning {
        override val revurderingId: UUID? = null
        override val klageId: UUID? = null
    }

    data class ForRevurdering(
        override val id: UUID = UUID.randomUUID(),
        override val vedtakId: UUID,
        override val sakId: UUID,
        override val revurderingId: UUID,
    ) : BehandlingVedtakKnytning {
        override val søknadsbehandlingId: UUID? = null
        override val klageId: UUID? = null
    }

    data class ForKlage(
        override val id: UUID = UUID.randomUUID(),
        override val vedtakId: UUID,
        override val sakId: UUID,
        override val klageId: UUID,
    ) : BehandlingVedtakKnytning {
        override val søknadsbehandlingId: UUID? = null
        override val revurderingId: UUID? = null
    }
}
