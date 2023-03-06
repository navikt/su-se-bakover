package no.nav.su.se.bakover.database.vedtak

import kotliquery.Row
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.common.deserializeListNullable
import no.nav.su.se.bakover.common.deserializeNullable
import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.DbMetrics
import no.nav.su.se.bakover.common.persistence.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.common.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.persistence.Session
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.persistence.hent
import no.nav.su.se.bakover.common.persistence.hentListe
import no.nav.su.se.bakover.common.persistence.insert
import no.nav.su.se.bakover.common.persistence.tidspunkt
import no.nav.su.se.bakover.common.persistence.uuid30OrNull
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.database.beregning.deserialiserBeregning
import no.nav.su.se.bakover.database.klage.KlagePostgresRepo
import no.nav.su.se.bakover.database.regulering.ReguleringPostgresRepo
import no.nav.su.se.bakover.database.revurdering.RevurderingPostgresRepo
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingPostgresRepo
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.beregning.BeregningMedFradragBeregnetMånedsvis
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.dokument.Dokumenttilstand
import no.nav.su.se.bakover.domain.klage.IverksattAvvistKlage
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.regulering.IverksattRegulering
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.satser.SatsFactoryForSupplerendeStønad
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.domain.vedtak.ForenkletVedtak
import no.nav.su.se.bakover.domain.vedtak.Klagevedtak
import no.nav.su.se.bakover.domain.vedtak.Stønadsvedtak
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vedtak.Vedtakstype
import java.util.UUID

internal enum class VedtakType {
    SØKNAD, // Innvilget Søknadsbehandling                  -> EndringIYtelse
    AVSLAG, // Avslått Søknadsbehandling                    -> Avslag
    ENDRING, // Revurdering innvilget                       -> EndringIYtelse
    REGULERING, // Regulering innvilget                     -> EndringIYtelse
    OPPHØR, // Revurdering ført til opphør                  -> EndringIYtelse
    STANS_AV_YTELSE,
    GJENOPPTAK_AV_YTELSE,
    AVVIST_KLAGE,
}

internal class VedtakPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
    private val søknadsbehandlingRepo: SøknadsbehandlingPostgresRepo,
    private val revurderingRepo: RevurderingPostgresRepo,
    private val reguleringRepo: ReguleringPostgresRepo,
    private val klageRepo: KlagePostgresRepo,
    private val satsFactory: SatsFactoryForSupplerendeStønad,
) : VedtakRepo {

    override fun hentVedtakForId(vedtakId: UUID): Vedtak? {
        return sessionFactory.withSession { session ->
            hentVedtakForIdOgSession(
                vedtakId = vedtakId,
                session = session,
            )
        }
    }

    internal fun hentVedtakForIdOgSession(
        vedtakId: UUID,
        session: Session,
    ): Vedtak? {
        return """
                select
                  v.*,
                  d.id as dokumentid,
                  dd.brevbestillingid,
                  dd.journalpostid
                from vedtak v
                left join dokument d on v.id = d.vedtakid
                left join dokument_distribusjon dd on d.id = dd.dokumentid
                where v.id = :vedtakId
                order by v.opprettet
        """.trimIndent()
            .hent(mapOf("vedtakId" to vedtakId), session) {
                it.toVedtak(session)
            }
    }

    override fun hentForRevurderingId(revurderingId: UUID): Vedtak? {
        return sessionFactory.withSession { session ->
            """
                select
                  v.*,
                  d.id as dokumentid,
                  dd.brevbestillingid,
                  dd.journalpostid
                from vedtak v
                left join dokument d on v.id = d.vedtakid
                left join dokument_distribusjon dd on d.id = dd.dokumentid
                join behandling_vedtak bv on bv.vedtakid = v.id
                join revurdering r on r.id = bv.revurderingId
                where r.id = :revurderingId
                order by v.opprettet
            """.trimIndent()
                .hent(mapOf("revurderingId" to revurderingId), session) {
                    it.toVedtak(session)
                }
        }
    }

    internal fun hentForSakId(sakId: UUID, session: Session): List<Vedtak> =
        """
            select
              v.*,
              d.id as dokumentid,
              dd.brevbestillingid,
              dd.journalpostid
            from vedtak v
            left join dokument d on v.id = d.vedtakid
            left join dokument_distribusjon dd on d.id = dd.dokumentid
            where v.sakId = :sakId
            order by v.opprettet
        """.trimIndent()
            .hentListe(mapOf("sakId" to sakId), session) {
                it.toVedtak(session)
            }

    override fun lagre(vedtak: Vedtak) {
        sessionFactory.withTransactionContext { tx ->
            lagreITransaksjon(vedtak, tx)
        }
    }

    override fun lagreITransaksjon(vedtak: Vedtak, sessionContext: TransactionContext) {
        return dbMetrics.timeQuery("lagreVedtak") {
            sessionContext.withSession { tx ->
                when (vedtak) {
                    is VedtakSomKanRevurderes.EndringIYtelse -> lagreInternt(vedtak, tx)
                    is Avslagsvedtak -> lagreInternt(vedtak, tx)
                    is Klagevedtak.Avvist -> lagreInternt(vedtak, tx)
                }
            }
        }
    }

    /**
     * Det er kun [VedtakSomKanRevurderes.EndringIYtelse] som inneholder en utbetalingId
     */
    override fun hentForUtbetaling(utbetalingId: UUID30): VedtakSomKanRevurderes.EndringIYtelse? {
        return dbMetrics.timeQuery("hentVedtakForUtbetalingId") {
            sessionFactory.withSession { session ->
                """
                select
                  v.*,
                  d.id as dokumentid,
                  dd.brevbestillingid,
                  dd.journalpostid
                from vedtak v
                left join dokument d on v.id = d.vedtakid
                left join dokument_distribusjon dd on d.id = dd.dokumentid
                where v.utbetalingId = :utbetalingId
                order by v.opprettet
                """.trimIndent()
                    .hent(mapOf("utbetalingId" to utbetalingId), session) {
                        it.toVedtak(session) as VedtakSomKanRevurderes.EndringIYtelse
                    }
            }
        }
    }

    // TODO jah: Flytt til DokDistRepo?
    override fun hentJournalpostId(vedtakId: UUID): JournalpostId? {
        return dbMetrics.timeQuery("hentJournalpostIdForVedtakId") {
            sessionFactory.withSession { session ->
                """
                select
                  journalpostid
                from dokument
                inner join dokument_distribusjon dd
                  on dokument.id = dd.dokumentid
                where vedtakid = :vedtakId
                """.trimIndent().hent(mapOf("vedtakId" to vedtakId), session) {
                    JournalpostId(it.string("journalpostid"))
                }
            }
        }
    }

    override fun hentForMåned(måned: Måned): List<ForenkletVedtak> {
        return dbMetrics.timeQuery("hentAktiveVedtak") {
            sessionFactory.withSession { session ->
                """
                  select
                    v.opprettet,
                    v.fraogmed,
                    v.tilogmed,
                    v.vedtaktype,
                    s.fnr
                  from vedtak v
                    left join sak s on s.id = v.sakid
                  where
                    v.vedtaktype IN ('SØKNAD','ENDRING','OPPHØR') and
                    :dato between fraogmed and tilogmed
                """.trimIndent()
                    .hentListe(mapOf("dato" to måned.fraOgMed), session) {
                        ForenkletVedtak(
                            opprettet = it.tidspunkt("opprettet"),
                            periode = Periode.create(it.localDate("fraogmed"), it.localDate("tilogmed")),
                            fødselsnummer = Fnr(it.string("fnr")),
                            vedtakstype = when (val v = it.string("vedtaktype")) {
                                "SØKNAD" -> Vedtakstype.SØKNADSBEHANDLING
                                "ENDRING" -> Vedtakstype.REVURDERING_INNVILGELSE
                                "OPPHØR" -> Vedtakstype.REVURDERING_OPPHØR
                                else -> throw IllegalStateException("Hentet ukjent vedtakstype fra databasen: $v")
                            },
                        )
                    }
            }
        }
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
        val knytning = hentBehandlingVedtaksknytning(id, session)
            ?: throw IllegalStateException("Fant ikke knytning mellom vedtak og søknadsbehandling/revurdering.")
        val behandling: Behandling? = when (knytning) {
            is BehandlingVedtaksknytning.ForSøknadsbehandling ->
                søknadsbehandlingRepo.hent(knytning.søknadsbehandlingId, session)!!

            is BehandlingVedtaksknytning.ForRevurdering ->
                revurderingRepo.hent(knytning.revurderingId, session)!!

            is BehandlingVedtaksknytning.ForKlage -> null
            is BehandlingVedtaksknytning.ForRegulering ->
                reguleringRepo.hent(knytning.reguleringId, session)
        }
        val klage: Klage? = (knytning as? BehandlingVedtaksknytning.ForKlage)?.let {
            klageRepo.hentKlage(knytning.klageId, session)
        }

        val saksbehandler = stringOrNull("saksbehandler")?.let { NavIdentBruker.Saksbehandler(it) }!!
        val attestant = stringOrNull("attestant")?.let { NavIdentBruker.Attestant(it) }!!
        val utbetalingId = uuid30OrNull("utbetalingId")
        val beregning: BeregningMedFradragBeregnetMånedsvis? =
            stringOrNull("beregning")?.deserialiserBeregning(
                satsFactory = satsFactory.gjeldende(opprettet),
                sakstype = behandling!!.sakstype,
            )
        val simulering = deserializeNullable<Simulering>(stringOrNull("simulering"))
        val avslagsgrunner = deserializeListNullable<Avslagsgrunn>(stringOrNull("avslagsgrunner"))

        val journalpostId: JournalpostId? = stringOrNull("journalpostid")?.let { JournalpostId(it) }
        val brevbestillingId: BrevbestillingId? = stringOrNull("brevbestillingid")?.let { BrevbestillingId(it) }
        val dokumentId: UUID? = uuidOrNull("dokumentid")
        val dokumenttilstand: Dokumenttilstand? = when {
            brevbestillingId != null -> Dokumenttilstand.SENDT
            journalpostId != null -> Dokumenttilstand.JOURNALFØRT
            dokumentId != null -> Dokumenttilstand.GENERERT
            else -> null
        }

        return when (VedtakType.valueOf(string("vedtaktype"))) {
            VedtakType.SØKNAD -> {
                VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling.createFromPersistence(
                    id = id,
                    opprettet = opprettet,
                    behandling = behandling as Søknadsbehandling.Iverksatt.Innvilget,
                    saksbehandler = saksbehandler,
                    attestant = attestant,
                    periode = periode!!,
                    beregning = beregning!!,
                    simulering = simulering!!,
                    utbetalingId = utbetalingId!!,
                    dokumenttilstand = dokumenttilstand,
                )
            }

            VedtakType.REGULERING -> {
                VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRegulering(
                    id = id,
                    opprettet = opprettet,
                    behandling = behandling as IverksattRegulering,
                    saksbehandler = saksbehandler,
                    attestant = attestant,
                    periode = periode!!,
                    beregning = beregning!!,
                    simulering = simulering!!,
                    utbetalingId = utbetalingId!!,
                )
            }

            VedtakType.ENDRING -> {
                VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering.createFromPersistence(
                    id = id,
                    opprettet = opprettet,
                    behandling = behandling as IverksattRevurdering.Innvilget,
                    saksbehandler = saksbehandler,
                    attestant = attestant,
                    periode = periode!!,
                    beregning = beregning!!,
                    simulering = simulering!!,
                    utbetalingId = utbetalingId!!,
                    dokumenttilstand = dokumenttilstand,
                )
            }

            VedtakType.OPPHØR -> {
                VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering.createFromPersistence(
                    id = id,
                    opprettet = opprettet,
                    behandling = behandling as IverksattRevurdering.Opphørt,
                    saksbehandler = saksbehandler,
                    attestant = attestant,
                    periode = periode!!,
                    beregning = beregning!!,
                    simulering = simulering!!,
                    utbetalingId = utbetalingId!!,
                    dokumenttilstand = dokumenttilstand,
                )
            }

            VedtakType.AVSLAG -> {
                if (beregning != null) {
                    Avslagsvedtak.AvslagBeregning.createFromPersistence(
                        id = id,
                        opprettet = opprettet,
                        behandling = behandling as Søknadsbehandling.Iverksatt.Avslag.MedBeregning,
                        beregning = beregning,
                        saksbehandler = saksbehandler,
                        attestant = attestant,
                        periode = periode!!,
                        // TODO fjern henting fra behandling etter migrering
                        avslagsgrunner = avslagsgrunner ?: behandling.avslagsgrunner,
                        dokumenttilstand = dokumenttilstand,
                    )
                } else {
                    Avslagsvedtak.AvslagVilkår.createFromPersistence(
                        id = id,
                        opprettet = opprettet,
                        behandling = behandling as Søknadsbehandling.Iverksatt.Avslag.UtenBeregning,
                        saksbehandler = saksbehandler,
                        attestant = attestant,
                        periode = periode!!,
                        // TODO fjern henting fra behandling etter migrering
                        avslagsgrunner = avslagsgrunner ?: behandling.avslagsgrunner,
                        dokumenttilstand = dokumenttilstand,
                    )
                }
            }

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

            VedtakType.AVVIST_KLAGE -> Klagevedtak.Avvist.createFromPersistence(
                id = id,
                opprettet = opprettet,
                saksbehandler = saksbehandler,
                attestant = attestant,
                klage = klage as IverksattAvvistKlage,
                dokumenttilstand = dokumenttilstand,
            )
        }
    }

    private fun lagreInternt(vedtak: VedtakSomKanRevurderes.EndringIYtelse, session: Session) {
        """
                INSERT INTO vedtak(
                    id,
                    sakId,
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
                    :sakId,
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
                    "sakId" to vedtak.sakinfo().sakId,
                    "opprettet" to vedtak.opprettet,
                    "fraOgMed" to vedtak.periode.fraOgMed,
                    "tilOgMed" to vedtak.periode.tilOgMed,
                    "saksbehandler" to vedtak.saksbehandler,
                    "attestant" to vedtak.attestant,
                    "utbetalingid" to vedtak.utbetalingId,
                    "simulering" to serialize(vedtak.simulering),
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

                        is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRegulering ->
                            vedtak.beregning
                    },
                    "vedtaktype" to when (vedtak) {
                        is VedtakSomKanRevurderes.EndringIYtelse.GjenopptakAvYtelse -> VedtakType.GJENOPPTAK_AV_YTELSE
                        is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering -> VedtakType.ENDRING
                        is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling -> VedtakType.SØKNAD
                        is VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering -> VedtakType.OPPHØR
                        is VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse -> VedtakType.STANS_AV_YTELSE
                        is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRegulering -> VedtakType.REGULERING
                    },
                ),
                session,
            )
        lagreKlagevedtaksknytningTilBehandling(vedtak, session)
    }

    private fun lagreInternt(vedtak: Avslagsvedtak, session: Session) {
        val beregning = when (vedtak) {
            is Avslagsvedtak.AvslagBeregning -> vedtak.beregning
            is Avslagsvedtak.AvslagVilkår -> null
        }
        """
                insert into vedtak(
                    id,
                    sakId,
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
                    :sakId,
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
                    "sakId" to vedtak.behandling.sakId,
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
        lagreKlagevedtaksknytningTilSøknadsbehandling(vedtak, session)
    }

    private fun lagreInternt(vedtak: Klagevedtak.Avvist, session: Session) {
        """
                INSERT INTO vedtak(
                    id,
                    sakId,
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
                    :sakId,
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
                "sakId" to vedtak.klage.sakId,
                "opprettet" to vedtak.opprettet,
                "saksbehandler" to vedtak.saksbehandler,
                "attestant" to vedtak.attestant,
                "vedtaktype" to VedtakType.AVVIST_KLAGE,
            ),
            session,
        )
        lagreKlagevedtaksknytningTilKlage(vedtak, session)
    }
}

/** Kan være en Søknadsbehandling eller Revurdering */
private fun lagreKlagevedtaksknytningTilBehandling(vedtak: Stønadsvedtak, session: Session) {
    when (vedtak.behandling) {
        is AbstraktRevurdering ->
            lagreKlagevedtaksknytningTilRevurdering(vedtak, session)

        is Søknadsbehandling ->
            lagreKlagevedtaksknytningTilSøknadsbehandling(vedtak, session)

        is Regulering ->
            lagreKlagevedtaksknytningTilRegulering(vedtak, session)

        else ->
            throw IllegalArgumentException("vedtak.behandling er av ukjent type. Den må være en revurdering eller en søknadsbehandling.")
    }
}

private fun lagreKlagevedtaksknytningTilRevurdering(vedtak: Stønadsvedtak, session: Session) {
    lagreVedtaksknytning(
        behandlingVedtaksknytning = BehandlingVedtaksknytning.ForRevurdering(
            vedtakId = vedtak.id,
            sakId = vedtak.behandling.sakId,
            revurderingId = vedtak.behandling.id,
        ),
        session = session,
    )
}

private fun lagreKlagevedtaksknytningTilRegulering(vedtak: Stønadsvedtak, session: Session) {
    lagreVedtaksknytning(
        behandlingVedtaksknytning = BehandlingVedtaksknytning.ForRegulering(
            vedtakId = vedtak.id,
            sakId = vedtak.behandling.sakId,
            reguleringId = vedtak.behandling.id,
        ),
        session = session,
    )
}

private fun lagreKlagevedtaksknytningTilSøknadsbehandling(vedtak: Stønadsvedtak, session: Session) {
    lagreVedtaksknytning(
        behandlingVedtaksknytning = BehandlingVedtaksknytning.ForSøknadsbehandling(
            vedtakId = vedtak.id,
            sakId = vedtak.behandling.sakId,
            søknadsbehandlingId = vedtak.behandling.id,
        ),
        session = session,
    )
}

private fun lagreKlagevedtaksknytningTilKlage(vedtak: Klagevedtak, session: Session) {
    lagreVedtaksknytning(
        behandlingVedtaksknytning = BehandlingVedtaksknytning.ForKlage(
            vedtakId = vedtak.id,
            sakId = vedtak.klage.sakId,
            klageId = vedtak.klage.id,
        ),
        session = session,
    )
}

private fun lagreVedtaksknytning(
    behandlingVedtaksknytning: BehandlingVedtaksknytning,
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
            klageId,
            reguleringId
        ) VALUES (
            :id,
            :vedtakId,
            :sakId,
            :soknadsbehandlingId,
            :revurderingId,
            :klageId,
            :reguleringId
        ) ON CONFLICT ON CONSTRAINT unique_vedtakid DO NOTHING
    """.trimIndent().insert(
        mapOf(
            "id" to behandlingVedtaksknytning.id,
            "vedtakId" to behandlingVedtaksknytning.vedtakId,
            "sakId" to behandlingVedtaksknytning.sakId,
            "soknadsbehandlingId" to behandlingVedtaksknytning.søknadsbehandlingId,
            "revurderingId" to behandlingVedtaksknytning.revurderingId,
            "klageId" to behandlingVedtaksknytning.klageId,
            "reguleringId" to behandlingVedtaksknytning.reguleringId,
        ),
        session,
    )
}

private fun hentBehandlingVedtaksknytning(vedtakId: UUID, session: Session): BehandlingVedtaksknytning? = """
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
    val reguleringId = it.stringOrNull("reguleringId")

    when {
        revurderingId == null && søknadsbehandlingId != null && klageId == null && reguleringId == null -> {
            BehandlingVedtaksknytning.ForSøknadsbehandling(
                id = id,
                vedtakId = vedtakId,
                sakId = sakId,
                søknadsbehandlingId = UUID.fromString(søknadsbehandlingId),
            )
        }

        revurderingId != null && søknadsbehandlingId == null && klageId == null && reguleringId == null -> {
            BehandlingVedtaksknytning.ForRevurdering(
                id = id,
                vedtakId = vedtakId,
                sakId = sakId,
                revurderingId = UUID.fromString(revurderingId),
            )
        }

        revurderingId == null && søknadsbehandlingId == null && klageId != null && reguleringId == null -> {
            BehandlingVedtaksknytning.ForKlage(
                id = id,
                vedtakId = vedtakId,
                sakId = sakId,
                klageId = UUID.fromString(klageId),
            )
        }

        revurderingId == null && søknadsbehandlingId == null && klageId == null && reguleringId != null -> {
            BehandlingVedtaksknytning.ForRegulering(
                id = id,
                vedtakId = vedtakId,
                sakId = sakId,
                reguleringId = UUID.fromString(reguleringId),
            )
        }

        else -> {
            throw IllegalStateException(
                "Fant ugyldig behandling-vedtak-knytning. søknadsbehandlingId=$søknadsbehandlingId, revurderingId=$revurderingId, klageId=$klageId, reguleringId=$reguleringId. Én og nøyaktig én av dem må være satt.",
            )
        }
    }
}

private sealed interface BehandlingVedtaksknytning {
    val id: UUID
    val vedtakId: UUID
    val sakId: UUID

    val søknadsbehandlingId: UUID?
    val revurderingId: UUID?
    val klageId: UUID?
    val reguleringId: UUID?

    data class ForSøknadsbehandling(
        override val id: UUID = UUID.randomUUID(),
        override val vedtakId: UUID,
        override val sakId: UUID,
        override val søknadsbehandlingId: UUID,
    ) : BehandlingVedtaksknytning {
        override val revurderingId: UUID? = null
        override val klageId: UUID? = null
        override val reguleringId: UUID? = null
    }

    data class ForRevurdering(
        override val id: UUID = UUID.randomUUID(),
        override val vedtakId: UUID,
        override val sakId: UUID,
        override val revurderingId: UUID,
    ) : BehandlingVedtaksknytning {
        override val søknadsbehandlingId: UUID? = null
        override val klageId: UUID? = null
        override val reguleringId: UUID? = null
    }

    data class ForKlage(
        override val id: UUID = UUID.randomUUID(),
        override val vedtakId: UUID,
        override val sakId: UUID,
        override val klageId: UUID,
    ) : BehandlingVedtaksknytning {
        override val søknadsbehandlingId: UUID? = null
        override val revurderingId: UUID? = null
        override val reguleringId: UUID? = null
    }

    data class ForRegulering(
        override val id: UUID = UUID.randomUUID(),
        override val vedtakId: UUID,
        override val sakId: UUID,
        override val reguleringId: UUID,
    ) : BehandlingVedtaksknytning {
        override val søknadsbehandlingId: UUID? = null
        override val klageId: UUID? = null
        override val revurderingId: UUID? = null
    }
}
