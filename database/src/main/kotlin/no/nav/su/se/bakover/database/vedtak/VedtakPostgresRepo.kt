package no.nav.su.se.bakover.database.vedtak

import beregning.domain.BeregningMedFradragBeregnetMånedsvis
import dokument.domain.Dokumenttilstand
import dokument.domain.brev.BrevbestillingId
import kotliquery.Row
import no.nav.su.se.bakover.behandling.Stønadsbehandling
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.deserializeListNullable
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withOptionalSession
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresTransactionContext.Companion.withTransaction
import no.nav.su.se.bakover.common.infrastructure.persistence.Session
import no.nav.su.se.bakover.common.infrastructure.persistence.TransactionalSession
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.infrastructure.persistence.uuid30OrNull
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.database.beregning.deserialiserBeregning
import no.nav.su.se.bakover.database.klage.KlagePostgresRepo
import no.nav.su.se.bakover.database.regulering.ReguleringPostgresRepo
import no.nav.su.se.bakover.database.revurdering.RevurderingPostgresRepo
import no.nav.su.se.bakover.database.simulering.deserializeNullableSimulering
import no.nav.su.se.bakover.database.simulering.serializeSimulering
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingPostgresRepo
import no.nav.su.se.bakover.domain.klage.IverksattAvvistKlage
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KlageId
import no.nav.su.se.bakover.domain.regulering.IverksattRegulering
import no.nav.su.se.bakover.domain.regulering.ReguleringId
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.domain.vedtak.Klagevedtak
import no.nav.su.se.bakover.domain.vedtak.Opphørsvedtak
import no.nav.su.se.bakover.domain.vedtak.Stønadsvedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakAvslagBeregning
import no.nav.su.se.bakover.domain.vedtak.VedtakAvslagVilkår
import no.nav.su.se.bakover.domain.vedtak.VedtakEndringIYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtakGjenopptakAvYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtakIngenEndringIYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRegulering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRevurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakIverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakOpphørMedUtbetaling
import no.nav.su.se.bakover.domain.vedtak.VedtakOpphørUtenUtbetaling
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakStansAvYtelse
import no.nav.su.se.bakover.domain.vedtak.Vedtaksammendrag
import no.nav.su.se.bakover.domain.vedtak.Vedtakstype
import no.nav.su.se.bakover.vedtak.domain.Vedtak
import satser.domain.supplerendestønad.SatsFactoryForSupplerendeStønad
import vilkår.common.domain.Avslagsgrunn
import java.time.LocalDate
import java.util.UUID

internal enum class VedtakType {
    // Innvilget Søknadsbehandling                  -> EndringIYtelse
    SØKNAD,

    // Avslått Søknadsbehandling                    -> Avslag
    AVSLAG,

    // Revurdering innvilget                       -> EndringIYtelse
    ENDRING,

    // Regulering innvilget                     -> EndringIYtelse
    REGULERING,

    // Revurdering ført til opphør                  -> EndringIYtelse
    OPPHØR,

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
                and d.duplikatAv is null
                order by v.opprettet
        """.trimIndent()
            .hent(mapOf("vedtakId" to vedtakId), session) {
                it.toVedtak(session)
            }
    }

    override fun hentForRevurderingId(revurderingId: RevurderingId): Vedtak? {
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
                and d.duplikatAv is null
                order by v.opprettet
            """.trimIndent()
                .hent(mapOf("revurderingId" to revurderingId.value), session) {
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
            and d.duplikatAv is null
            order by v.opprettet
        """.trimIndent()
            .hentListe(mapOf("sakId" to sakId), session) {
                it.toVedtak(session)
            }.also {
                it.map { it.id }.let {
                    check(it.distinct().size == it.size) { "Fant duplikate vedtak/dokument/dokument_distribusjon for sakId=$sakId" }
                }
            }

    override fun lagre(vedtak: Vedtak) {
        sessionFactory.withTransactionContext { tx ->
            lagreITransaksjon(vedtak, tx)
        }
    }

    override fun lagreITransaksjon(vedtak: Vedtak, tx: TransactionContext) {
        return dbMetrics.timeQuery("lagreVedtak") {
            tx.withTransaction { tx ->
                when (vedtak) {
                    // TODO jah: Erstatt med én felles insert-function
                    is VedtakEndringIYtelse -> lagreInternt(vedtak, tx)
                    is VedtakIngenEndringIYtelse -> throw IllegalStateException("")
                    is Avslagsvedtak -> lagreInternt(vedtak, tx)
                    is Klagevedtak.Avvist -> lagreInternt(vedtak, tx)
                }
            }
        }
    }

    override fun oppdaterUtbetalingId(
        vedtakId: UUID,
        utbetalingId: UUID30,
        sessionContext: SessionContext?,
    ) {
        sessionContext.withOptionalSession(sessionFactory) { session ->
            """
                update vedtak set utbetalingId = :utbetalingId where id = :vedtakId
            """.trimIndent()
                .oppdatering(mapOf("utbetalingId" to utbetalingId, "vedtakId" to vedtakId), session)
        }
    }

    /**
     * Det er kun [VedtakEndringIYtelse] som inneholder en utbetalingId
     */
    override fun hentForUtbetaling(utbetalingId: UUID30): VedtakEndringIYtelse? {
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
                and d.duplikatAv is null
                order by v.opprettet
                """.trimIndent()
                    .hent(mapOf("utbetalingId" to utbetalingId), session) {
                        // Siden vi krever vedtak med utbetalingId bør dette være en trygg cast.
                        it.toVedtak(session) as VedtakEndringIYtelse
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
                  dd.journalpostid
                from dokument d
                inner join dokument_distribusjon dd
                  on d.id = dd.dokumentid
                where d.vedtakid = :vedtakId and d.duplikatAv is null
                """.trimIndent().hent(mapOf("vedtakId" to vedtakId), session) {
                    JournalpostId(it.string("journalpostid"))
                }
            }
        }
    }

    override fun hentForMåned(måned: Måned): List<Vedtaksammendrag> {
        return dbMetrics.timeQuery("hentForMåned") {
            sessionFactory.withSession { session ->
                """
                  select
                    v.opprettet,
                    v.fraogmed,
                    v.tilogmed,
                    v.vedtaktype,
                    s.fnr,
                    s.id as sakid,
                    s.saksnummer
                  from vedtak v
                    left join sak s on s.id = v.sakid
                  where
                    v.vedtaktype IN ('SØKNAD','ENDRING','OPPHØR') and
                    :dato between fraogmed and tilogmed
                """.trimIndent()
                    .hentListe(mapOf("dato" to måned.fraOgMed), session) {
                        Vedtaksammendrag(
                            opprettet = it.tidspunkt("opprettet"),
                            periode = Periode.create(it.localDate("fraogmed"), it.localDate("tilogmed")),
                            fødselsnummer = Fnr(it.string("fnr")),
                            vedtakstype = when (val v = it.string("vedtaktype")) {
                                "SØKNAD" -> Vedtakstype.SØKNADSBEHANDLING_INNVILGELSE
                                "ENDRING" -> Vedtakstype.REVURDERING_INNVILGELSE
                                "OPPHØR" -> Vedtakstype.REVURDERING_OPPHØR
                                else -> throw IllegalStateException("Hentet ukjent vedtakstype fra databasen: $v")
                            },
                            sakId = it.uuid("sakid"),
                            saksnummer = Saksnummer(it.long("saksnummer")),
                        )
                    }
            }
        }
    }

    override fun hentForFødselsnumreOgFraOgMedMåned(
        fødselsnumre: List<Fnr>,
        fraOgMed: Måned,
    ): List<Vedtaksammendrag> {
        return dbMetrics.timeQuery("hentForFødselsnumreOgFraOgMedMåned") {
            sessionFactory.withSession { session ->
                """
                  select
                    v.opprettet,
                    v.fraogmed,
                    v.tilogmed,
                    v.vedtaktype,
                    s.fnr,
                    s.id as sakid,
                    s.saksnummer
                  from vedtak v
                    left join sak s on s.id = v.sakid
                  where
                    v.vedtaktype IN ('SØKNAD','ENDRING','OPPHØR') and
                    s.fnr in (:fnr) and
                    :dato >= fraogmed
                """.trimIndent()
                    .hentListe(
                        mapOf(
                            "dato" to fraOgMed.fraOgMed,
                            "fnr" to fødselsnumre.joinToString { "," },
                        ),
                        session,
                    ) {
                        Vedtaksammendrag(
                            opprettet = it.tidspunkt("opprettet"),
                            periode = Periode.create(it.localDate("fraogmed"), it.localDate("tilogmed")),
                            fødselsnummer = Fnr(it.string("fnr")),
                            vedtakstype = when (val v = it.string("vedtaktype")) {
                                "SØKNAD" -> Vedtakstype.SØKNADSBEHANDLING_INNVILGELSE
                                "ENDRING" -> Vedtakstype.REVURDERING_INNVILGELSE
                                "OPPHØR" -> Vedtakstype.REVURDERING_OPPHØR
                                else -> throw IllegalStateException("Hentet ukjent vedtakstype fra databasen: $v")
                            },
                            sakId = it.uuid("sakid"),
                            saksnummer = Saksnummer(it.long("saksnummer")),
                        )
                    }
            }
        }
    }

    override fun hentSøknadsbehandlingsvedtakFraOgMed(
        fraOgMed: LocalDate,
    ): List<UUID> {
        return sessionFactory.withSession { session ->
            """
                select
                  v.id
                from
                  vedtak v
                where
                  v.vedtaktype IN ('SØKNAD','AVSLAG')
                  and v.opprettet >= :fraOgMed::date
                order by
                  v.opprettet
            """.trimIndent()
                .hentListe(mapOf("fraOgMed" to fraOgMed.toString()), session) {
                    it.uuid("id")
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
        val behandling: Stønadsbehandling? = when (knytning) {
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
                satsFactory = satsFactory,
                sakstype = behandling!!.sakstype,
                saksnummer = behandling.saksnummer,
            )
        val simulering = stringOrNull("simulering").deserializeNullableSimulering()
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
                VedtakInnvilgetSøknadsbehandling.createFromPersistence(
                    id = id,
                    opprettet = opprettet,
                    behandling = behandling as IverksattSøknadsbehandling.Innvilget,
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
                VedtakInnvilgetRegulering.createFromPersistence(
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
                VedtakInnvilgetRevurdering.createFromPersistence(
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
                if (utbetalingId != null) {
                    VedtakOpphørMedUtbetaling.createFromPersistence(
                        id = id,
                        opprettet = opprettet,
                        behandling = behandling as IverksattRevurdering.Opphørt,
                        saksbehandler = saksbehandler,
                        attestant = attestant,
                        periode = periode!!,
                        beregning = beregning!!,
                        simulering = simulering!!,
                        utbetalingId = utbetalingId,
                        dokumenttilstand = dokumenttilstand,
                    )
                } else {
                    VedtakOpphørUtenUtbetaling.createFromPersistence(
                        id = id,
                        opprettet = opprettet,
                        behandling = behandling as IverksattRevurdering.Opphørt,
                        saksbehandler = saksbehandler,
                        attestant = attestant,
                        periode = periode!!,
                        beregning = beregning!!,
                        simulering = simulering!!,
                        dokumenttilstand = dokumenttilstand,
                    )
                }
            }

            VedtakType.AVSLAG -> {
                if (beregning != null) {
                    VedtakAvslagBeregning.createFromPersistence(
                        id = id,
                        opprettet = opprettet,
                        behandling = behandling as IverksattSøknadsbehandling.Avslag.MedBeregning,
                        beregning = beregning,
                        saksbehandler = saksbehandler,
                        attestant = attestant,
                        periode = periode!!,
                        // TODO fjern henting fra behandling etter migrering
                        avslagsgrunner = avslagsgrunner ?: behandling.avslagsgrunner,
                        dokumenttilstand = dokumenttilstand,
                    )
                } else {
                    VedtakAvslagVilkår.createFromPersistence(
                        id = id,
                        opprettet = opprettet,
                        behandling = behandling as IverksattSøknadsbehandling.Avslag.UtenBeregning,
                        saksbehandler = saksbehandler,
                        attestant = attestant,
                        periode = periode!!,
                        // TODO fjern henting fra behandling etter migrering
                        avslagsgrunner = avslagsgrunner ?: behandling.avslagsgrunner,
                        dokumenttilstand = dokumenttilstand,
                    )
                }
            }

            VedtakType.STANS_AV_YTELSE -> VedtakStansAvYtelse.createFromPersistence(
                id = id,
                opprettet = opprettet,
                behandling = behandling as StansAvYtelseRevurdering.IverksattStansAvYtelse,
                saksbehandler = saksbehandler,
                attestant = attestant,
                periode = periode!!,
                simulering = simulering!!,
                utbetalingId = utbetalingId!!,
            )

            VedtakType.GJENOPPTAK_AV_YTELSE -> VedtakGjenopptakAvYtelse.createFromPersistence(
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

    /** @param tx Persisterer både 'vedtak' og 'behandling_vedtak', så vi krever en transaksjon her. */
    private fun lagreInternt(vedtak: VedtakEndringIYtelse, tx: TransactionalSession) {
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
                    "simulering" to vedtak.simulering.serializeSimulering(),
                    "beregning" to vedtak.beregning,
                    "vedtaktype" to when (vedtak) {
                        is VedtakGjenopptakAvYtelse -> VedtakType.GJENOPPTAK_AV_YTELSE
                        is VedtakInnvilgetRevurdering -> VedtakType.ENDRING
                        is VedtakInnvilgetSøknadsbehandling -> VedtakType.SØKNAD
                        is Opphørsvedtak -> VedtakType.OPPHØR

                        is VedtakStansAvYtelse -> VedtakType.STANS_AV_YTELSE
                        is VedtakInnvilgetRegulering -> VedtakType.REGULERING
                    },
                ),
                tx,
            )
        lagreKlagevedtaksknytningTilBehandling(vedtak, tx)
    }

    /** @param tx Persisterer både 'vedtak' og 'behandling_vedtak', så vi krever en transaksjon her. */
    private fun lagreInternt(vedtak: Avslagsvedtak, tx: TransactionalSession) {
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
                    "beregning" to vedtak.beregning,
                    "vedtaktype" to VedtakType.AVSLAG,
                    "avslagsgrunner" to vedtak.avslagsgrunner.serialize(),
                ),
                tx,
            )
        lagreKlagevedtaksknytningTilSøknadsbehandling(vedtak, tx)
    }

    /** @param tx Persisterer både 'vedtak' og 'behandling_vedtak', så vi krever en transaksjon her. */
    private fun lagreInternt(vedtak: Klagevedtak.Avvist, tx: TransactionalSession) {
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
                "sakId" to vedtak.behandling.sakId,
                "opprettet" to vedtak.opprettet,
                "saksbehandler" to vedtak.saksbehandler,
                "attestant" to vedtak.attestant,
                "vedtaktype" to VedtakType.AVVIST_KLAGE,
            ),
            tx,
        )
        lagreKlagevedtaksknytningTilKlage(vedtak, tx)
    }
}

/** Kan være en Søknadsbehandling eller Revurdering */
private fun lagreKlagevedtaksknytningTilBehandling(vedtak: VedtakEndringIYtelse, session: Session) {
    when (vedtak) {
        is VedtakInnvilgetSøknadsbehandling -> lagreKlagevedtaksknytningTilSøknadsbehandling(vedtak, session)
        // TODO jah: Kan også vurdere lage en felles type for innvilget rev, opphør, stans og gjenopptak, da tregner vi ikke sende med vedtak.behandling.id
        is VedtakOpphørMedUtbetaling -> lagreKlagevedtaksknytningTilRevurdering(vedtak, vedtak.behandling.id, session)
        is VedtakInnvilgetRevurdering -> lagreKlagevedtaksknytningTilRevurdering(vedtak, vedtak.behandling.id, session)
        is VedtakGjenopptakAvYtelse -> lagreKlagevedtaksknytningTilRevurdering(vedtak, vedtak.behandling.id, session)
        is VedtakInnvilgetRegulering -> lagreKlagevedtaksknytningTilRegulering(vedtak, session)
        is VedtakStansAvYtelse -> lagreKlagevedtaksknytningTilRevurdering(vedtak, vedtak.behandling.id, session)
    }
}

private fun lagreKlagevedtaksknytningTilRevurdering(
    vedtak: Stønadsvedtak,
    revurderingId: RevurderingId,
    session: Session,
) {
    lagreVedtaksknytning(
        behandlingVedtaksknytning = BehandlingVedtaksknytning.ForRevurdering(
            vedtakId = vedtak.id,
            sakId = vedtak.behandling.sakId,
            revurderingId = revurderingId,
        ),
        session = session,
    )
}

private fun lagreKlagevedtaksknytningTilRegulering(vedtak: VedtakInnvilgetRegulering, session: Session) {
    lagreVedtaksknytning(
        behandlingVedtaksknytning = BehandlingVedtaksknytning.ForRegulering(
            vedtakId = vedtak.id,
            sakId = vedtak.behandling.sakId,
            reguleringId = vedtak.behandling.id,
        ),
        session = session,
    )
}

private fun lagreKlagevedtaksknytningTilSøknadsbehandling(vedtak: VedtakIverksattSøknadsbehandling, session: Session) {
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
            sakId = vedtak.behandling.sakId,
            klageId = vedtak.behandling.id,
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
            "soknadsbehandlingId" to behandlingVedtaksknytning.søknadsbehandlingId?.value,
            "revurderingId" to behandlingVedtaksknytning.revurderingId?.value,
            "klageId" to behandlingVedtaksknytning.klageId?.value,
            "reguleringId" to behandlingVedtaksknytning.reguleringId?.value,
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
    check(it.uuid("vedtakId") == vedtakId)
    val sakId = it.uuid("sakId")
    val søknadsbehandlingId = it.stringOrNull("søknadsbehandlingId")?.let { SøknadsbehandlingId.fraString(it) }
    val revurderingId = it.stringOrNull("revurderingId")?.let { RevurderingId.fraString(it) }
    val klageId = it.stringOrNull("klageId")?.let { KlageId.fraString(it) }
    val reguleringId = it.stringOrNull("reguleringId")?.let { ReguleringId.fraString(it) }

    when {
        revurderingId == null && søknadsbehandlingId != null && klageId == null && reguleringId == null -> {
            BehandlingVedtaksknytning.ForSøknadsbehandling(
                id = id,
                vedtakId = vedtakId,
                sakId = sakId,
                søknadsbehandlingId = søknadsbehandlingId,
            )
        }

        revurderingId != null && søknadsbehandlingId == null && klageId == null && reguleringId == null -> {
            BehandlingVedtaksknytning.ForRevurdering(
                id = id,
                vedtakId = vedtakId,
                sakId = sakId,
                revurderingId = revurderingId,
            )
        }

        revurderingId == null && søknadsbehandlingId == null && klageId != null && reguleringId == null -> {
            BehandlingVedtaksknytning.ForKlage(
                id = id,
                vedtakId = vedtakId,
                sakId = sakId,
                klageId = klageId,
            )
        }

        revurderingId == null && søknadsbehandlingId == null && klageId == null && reguleringId != null -> {
            BehandlingVedtaksknytning.ForRegulering(
                id = id,
                vedtakId = vedtakId,
                sakId = sakId,
                reguleringId = reguleringId,
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

    val søknadsbehandlingId: SøknadsbehandlingId?
    val revurderingId: RevurderingId?
    val klageId: KlageId?
    val reguleringId: ReguleringId?

    data class ForSøknadsbehandling(
        override val id: UUID = UUID.randomUUID(),
        override val vedtakId: UUID,
        override val sakId: UUID,
        override val søknadsbehandlingId: SøknadsbehandlingId,
    ) : BehandlingVedtaksknytning {
        override val revurderingId: RevurderingId? = null
        override val klageId: KlageId? = null
        override val reguleringId: ReguleringId? = null
    }

    data class ForRevurdering(
        override val id: UUID = UUID.randomUUID(),
        override val vedtakId: UUID,
        override val sakId: UUID,
        override val revurderingId: RevurderingId,
    ) : BehandlingVedtaksknytning {
        override val søknadsbehandlingId: SøknadsbehandlingId? = null
        override val klageId: KlageId? = null
        override val reguleringId: ReguleringId? = null
    }

    data class ForKlage(
        override val id: UUID = UUID.randomUUID(),
        override val vedtakId: UUID,
        override val sakId: UUID,
        override val klageId: KlageId,
    ) : BehandlingVedtaksknytning {
        override val søknadsbehandlingId: SøknadsbehandlingId? = null
        override val revurderingId: RevurderingId? = null
        override val reguleringId: ReguleringId? = null
    }

    data class ForRegulering(
        override val id: UUID = UUID.randomUUID(),
        override val vedtakId: UUID,
        override val sakId: UUID,
        override val reguleringId: ReguleringId,
    ) : BehandlingVedtaksknytning {
        override val søknadsbehandlingId: SøknadsbehandlingId? = null
        override val klageId: KlageId? = null
        override val revurderingId: RevurderingId? = null
    }
}
