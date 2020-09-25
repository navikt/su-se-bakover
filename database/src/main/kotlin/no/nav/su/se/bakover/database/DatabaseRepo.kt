package no.nav.su.se.bakover.database

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import kotliquery.using
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.UUIDFactory
import no.nav.su.se.bakover.common.between
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.BehandlingPersistenceObserver
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SakPersistenceObserver
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Fradrag
import no.nav.su.se.bakover.domain.beregning.Fradragstype
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.hendelseslogg.Hendelseslogg
import no.nav.su.se.bakover.domain.hendelseslogg.HendelsesloggPersistenceObserver
import no.nav.su.se.bakover.domain.hendelseslogg.hendelse.HendelseListReader
import no.nav.su.se.bakover.domain.hendelseslogg.hendelse.HendelseListWriter
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag.OppdragPersistenceObserver
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingPersistenceObserver
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.sql.DataSource

internal class DatabaseRepo(
    private val dataSource: DataSource,
    private val uuidFactory: UUIDFactory = UUIDFactory(),
    private val clock: Clock = Clock.systemUTC()
) : ObjectRepo,
    SakPersistenceObserver,
    BehandlingPersistenceObserver,
    OppdragPersistenceObserver,
    UtbetalingPersistenceObserver,
    HendelsesloggPersistenceObserver {

    override fun hentSak(fnr: Fnr): Sak? = using(sessionOf(dataSource)) { hentSakInternal(fnr, it) }

    private fun hentSakInternal(fnr: Fnr, session: Session): Sak? = "select * from sak where fnr=:fnr"
        .hent(mapOf("fnr" to fnr.toString()), session) { it.toSak(session) }

    override fun nySøknad(sakId: UUID, søknad: Søknad): Søknad {
        return opprettSøknad(sakId = sakId, søknad = søknad)
    }

    override fun opprettSøknadsbehandling(
        sakId: UUID,
        behandling: Behandling
    ): Behandling {
        """
            insert into behandling
                (id, sakId, søknadId, opprettet, status, behandlingsinformasjon)
            values
                (:id, :sakId, :soknadId, :opprettet, :status, to_json(:behandlingsinformasjon::json))
        """.trimIndent().oppdatering(
            mapOf(
                "id" to behandling.id,
                "sakId" to sakId,
                "soknadId" to behandling.søknad.id,
                "opprettet" to behandling.opprettet,
                "status" to behandling.status().name,
                "behandlingsinformasjon" to objectMapper.writeValueAsString(behandling.behandlingsinformasjon())
            )
        )
        oppdaterHendelseslogg(Hendelseslogg(behandling.id.toString()))
        return hentBehandling(behandling.id)!!
    }

    internal fun hentOppdrag(oppdragId: UUID30) = using(sessionOf(dataSource)) { session ->
        "select * from oppdrag where id=:id".hent(mapOf("id" to oppdragId.toString()), session) {
            it.toOppdrag(session)
        }
    }

    override fun hentUtbetaling(utbetalingId: UUID30): Utbetaling? =
        using(sessionOf(dataSource)) { session -> hentUtbetalingInternal(utbetalingId, session) }

    private fun hentUtbetalingInternal(utbetalingId: UUID30, session: Session): Utbetaling? =
        "select * from utbetaling where id = :id".hent(
            mapOf(
                "id" to utbetalingId
            ),
            session
        ) { it.toUtbetaling(session) }

    override fun opprettUtbetaling(oppdragId: UUID30, utbetaling: Utbetaling): Utbetaling {
        """
            insert into utbetaling (id, opprettet, oppdragId, fnr)
            values (:id, :opprettet, :oppdragId, :fnr)
         """.oppdatering(
            mapOf(
                "id" to utbetaling.id,
                "opprettet" to utbetaling.opprettet,
                "oppdragId" to oppdragId,
                "fnr" to utbetaling.fnr
            )
        )
        utbetaling.utbetalingslinjer.forEach { opprettUtbetalingslinje(utbetaling.id, it) }
        utbetaling.addObserver(this)
        return utbetaling
    }

    override fun slettUtbetaling(utbetaling: Utbetaling) {
        check(utbetaling.kanSlettes()) { "Utbetaling har kommet for langt i utbetalingsløpet til å kunne slettes" }
        "delete from utbetaling where id=:id".oppdatering(
            mapOf(
                "id" to utbetaling.id
            )
        )
    }

    internal fun opprettUtbetalingslinje(utbetalingId: UUID30, utbetalingslinje: Utbetalingslinje): Utbetalingslinje {
        """
            insert into utbetalingslinje (id, opprettet, fom, tom, utbetalingId, forrigeUtbetalingslinjeId, beløp)
            values (:id, :opprettet, :fom, :tom, :utbetalingId, :forrigeUtbetalingslinjeId, :belop)
        """.oppdatering(
            mapOf(
                "id" to utbetalingslinje.id,
                "opprettet" to utbetalingslinje.opprettet,
                "fom" to utbetalingslinje.fom,
                "tom" to utbetalingslinje.tom,
                "utbetalingId" to utbetalingId,
                "forrigeUtbetalingslinjeId" to utbetalingslinje.forrigeUtbetalingslinjeId,
                "belop" to utbetalingslinje.beløp,
            )
        )
        return utbetalingslinje
    }

    private fun Row.toSak(session: Session): Sak {
        val sakId = UUID.fromString(string("id"))
        return Sak(
            id = sakId,
            fnr = Fnr(string("fnr")),
            opprettet = instant("opprettet"),
            søknader = hentSøknaderInternal(sakId, session),
            behandlinger = hentBehandlingerForSak(sakId, session),
            oppdrag = hentOppdragForSak(sakId, session)
        ).also { it.addObserver(this@DatabaseRepo) }
    }

    private fun hentOppdragForSak(sakId: UUID, session: Session) =
        "select * from oppdrag where sakId=:sakId".hent(mapOf("sakId" to sakId), session) {
            it.toOppdrag(session)
        }!!

    private fun Row.toOppdrag(session: Session): Oppdrag {
        val oppdragId = uuid30("id")
        return Oppdrag(
            id = oppdragId,
            opprettet = instant("opprettet"),
            sakId = uuid("sakId"),
            utbetalinger = hentUtbetalinger(oppdragId, session)
        ).also { it.addObserver(this@DatabaseRepo) }
    }

    internal fun hentUtbetalinger(oppdragId: UUID30, session: Session) =
        "select * from utbetaling where oppdragId=:oppdragId".hentListe(
            mapOf("oppdragId" to oppdragId.toString()),
            session
        ) {
            it.toUtbetaling(session)
        }.toMutableList()

    private fun Row.toUtbetaling(session: Session): Utbetaling {
        val utbetalingId = uuid30("id")
        return Utbetaling(
            id = utbetalingId,
            opprettet = instant("opprettet"),
            simulering = stringOrNull("simulering")?.let { objectMapper.readValue(it, Simulering::class.java) },
            kvittering = stringOrNull("kvittering")?.let { objectMapper.readValue(it, Kvittering::class.java) },
            oppdragsmelding = stringOrNull("oppdragsmelding")?.let {
                objectMapper.readValue(
                    it,
                    Oppdragsmelding::class.java
                )
            },
            utbetalingslinjer = hentUtbetalingslinjer(utbetalingId, session),
            avstemmingId = stringOrNull("avstemmingId")?.let { UUID30.fromString(it) },
            fnr = Fnr(string("fnr"))
        ).also {
            it.addObserver(this@DatabaseRepo)
        }
    }

    internal fun hentUtbetalingslinjer(utbetalingId: UUID30, session: Session): List<Utbetalingslinje> =
        "select * from utbetalingslinje where utbetalingId=:utbetalingId".hentListe(
            mapOf("utbetalingId" to utbetalingId.toString()),
            session
        ) {
            it.toUtbetalingslinje()
        }

    private fun Row.toUtbetalingslinje(): Utbetalingslinje {
        return Utbetalingslinje(
            id = uuid30("id"),
            fom = localDate("fom"),
            tom = localDate("tom"),
            opprettet = instant("opprettet"),
            forrigeUtbetalingslinjeId = stringOrNull("forrigeUtbetalingslinjeId")?.let { uuid30("forrigeUtbetalingslinjeId") },
            beløp = int("beløp")
        )
    }

    private fun hentSøknaderInternal(sakId: UUID, session: Session) = "select * from søknad where sakId=:sakId"
        .hentListe(mapOf("sakId" to sakId), session) {
            it.toSøknad()
        }.toMutableList()

    private fun hentBehandlingerForSak(sakId: UUID, session: Session) = "select * from behandling where sakId=:sakId"
        .hentListe(mapOf("sakId" to sakId), session) {
            it.toBehandling(session)
        }.toMutableList()

    override fun hentSak(sakId: UUID): Sak? = using(sessionOf(dataSource)) { hentSakInternal(sakId, it) }

    private fun hentSakInternal(sakId: UUID, session: Session): Sak? = "select * from sak where id=:sakId"
        .hent(mapOf("sakId" to sakId), session) { it.toSak(session) }

    internal fun opprettSøknad(sakId: UUID, søknad: Søknad): Søknad {
        "insert into søknad (id, sakId, søknadInnhold, opprettet) values (:id, :sakId, to_json(:soknad::json), :opprettet)".oppdatering(
            mapOf(
                "id" to søknad.id,
                "sakId" to sakId,
                "soknad" to objectMapper.writeValueAsString(søknad.søknadInnhold),
                "opprettet" to søknad.opprettet
            )
        )
        return søknad
    }

    override fun opprettSak(fnr: Fnr): Sak {
        val opprettet = now(clock)
        val sakId = UUID.randomUUID()
        val sak = Sak(
            id = sakId,
            fnr = fnr,
            opprettet = opprettet,
            oppdrag = Oppdrag(
                id = uuidFactory.newUUID30(),
                opprettet = opprettet,
                sakId = sakId
            )
        )
        """
            with inserted_sak as(insert into sak (id, fnr, opprettet) values (:sakId, :fnr, :opprettet))
            insert into oppdrag (id, opprettet, sakId) values (:oppdragId, :opprettet, :sakId)
        """.oppdatering(
            mapOf(
                "sakId" to sak.id,
                "fnr" to fnr,
                "opprettet" to sak.opprettet,
                "oppdragId" to sak.oppdrag.id
            )
        )
        sak.oppdrag.addObserver(this)
        sak.addObserver(this)
        return sak
    }

    override fun hentSøknad(søknadId: UUID): Søknad? = using(sessionOf(dataSource)) { hentSøknadInternal(søknadId, it) }

    private fun hentSøknadInternal(søknadId: UUID, session: Session): Søknad? = "select * from søknad where id=:id"
        .hent(mapOf("id" to søknadId), session) {
            it.toSøknad()
        }

    private fun Row.toSøknad(): Søknad {
        return Søknad(
            id = uuid("id"),
            søknadInnhold = objectMapper.readValue(string("søknadInnhold")),
            opprettet = instant("opprettet")
        )
    }

    override fun hentBehandling(behandlingId: UUID): Behandling? =
        using(sessionOf(dataSource)) { hentBehandling(behandlingId, it) }

    private fun hentBehandling(behandlingId: UUID, session: Session): Behandling? =
        "select * from behandling where id=:id"
            .hent(mapOf("id" to behandlingId), session) { row ->
                row.toBehandling(session)
            }

    private fun Row.toBehandling(session: Session): Behandling {
        val behandlingId = uuid("id")
        return Behandling(
            id = behandlingId,
            behandlingsinformasjon = objectMapper.readValue(string("behandlingsinformasjon")),
            opprettet = instant("opprettet"),
            søknad = hentSøknadInternal(uuid("søknadId"), session)!!,
            beregning = hentBeregningInternal(behandlingId, session),
            utbetaling = stringOrNull("utbetalingId")?.let { hentUtbetalingInternal(UUID30.fromString(it), session)!! },
            status = Behandling.BehandlingsStatus.valueOf(string("status")),
            attestant = stringOrNull("attestant")?.let { Attestant(it) },
            saksbehandler = stringOrNull("saksbehandler")?.let { Saksbehandler(it) },
            sakId = uuid("sakId"),
            hendelseslogg = hentHendelseslogg(behandlingId.toString())!!
        ).also {
            it.addObserver(this@DatabaseRepo)
        }
    }

    private fun String.oppdatering(params: Map<String, Any?>) {
        using(sessionOf(dataSource)) {
            this.oppdatering(params, it)
        }
    }

    override fun hentBeregning(behandlingId: UUID): Beregning? =
        using(sessionOf(dataSource)) { hentBeregningInternal(behandlingId, it) }

    private fun hentBeregningInternal(behandlingId: UUID, session: Session) =
        "select * from beregning where behandlingId = :id".hent(mapOf("id" to behandlingId), session) {
            it.toBeregning(session)
        }

    private fun Row.toBeregning(session: Session) = Beregning(
        id = uuid("id"),
        opprettet = instant("opprettet"),
        fom = localDate("fom"),
        tom = localDate("tom"),
        sats = Sats.valueOf(string("sats")),
        månedsberegninger = hentMånedsberegninger(uuid("id"), session),
        fradrag = hentFradrag(uuid("id"), session),
        forventetInntekt = hentBehandling(uuid("id"))?.behandlingsinformasjon()?.uførhet?.forventetInntekt ?: 0
    )

    override fun opprettBeregning(behandlingId: UUID, beregning: Beregning): Beregning {
        deleteBeregninger(behandlingId)
        "insert into beregning (id, opprettet, fom, tom, behandlingId, sats) values (:id, :opprettet, :fom, :tom, :behandlingId, :sats)".oppdatering(
            mapOf(
                "id" to beregning.id,
                "opprettet" to beregning.opprettet,
                "fom" to beregning.fom,
                "tom" to beregning.tom,
                "behandlingId" to behandlingId,
                "sats" to beregning.sats.name
            )
        )
        beregning.månedsberegninger.forEach { opprettMånedsberegning(beregning.id, it) }
        beregning.fradrag.forEach { opprettFradrag(beregning.id, it) }
        return beregning
    }

    override fun deleteBeregninger(behandlingId: UUID) {
        "delete from beregning where behandlingId=:id".oppdatering(mapOf("id" to behandlingId))
    }

    override fun oppdaterBehandlingStatus(
        behandlingId: UUID,
        status: Behandling.BehandlingsStatus
    ): Behandling.BehandlingsStatus {
        "update behandling set status = :status where id = :id".oppdatering(
            mapOf(
                "id" to behandlingId,
                "status" to status.name
            )
        )
        return status
    }

    override fun oppdaterBehandlingsinformasjon(
        behandlingId: UUID,
        behandlingsinformasjon: Behandlingsinformasjon
    ): Behandlingsinformasjon {
        "update behandling set behandlingsinformasjon = to_json(:behandlingsinformasjon::json) where id = :id".oppdatering(
            mapOf(
                "id" to behandlingId,
                "behandlingsinformasjon" to objectMapper.writeValueAsString(behandlingsinformasjon)
            )
        )
        return behandlingsinformasjon
    }

    override fun hentOppdrag(sakId: UUID): Oppdrag {
        return using(sessionOf(dataSource)) {
            hentOppdragForSak(sakId, it)
        }
    }

    override fun hentFnr(sakId: UUID): Fnr {
        return using(sessionOf(dataSource)) {
            "select fnr from sak where id=:sakId".hent(mapOf("sakId" to sakId), it) {
                Fnr(it.string("fnr"))
            }!!
        }
    }

    override fun attester(behandlingId: UUID, attestant: Attestant): Attestant {
        "update behandling set attestant = :attestant where id=:id".oppdatering(
            mapOf(
                "id" to behandlingId,
                "attestant" to attestant.id
            )
        )
        return attestant
    }

    override fun settSaksbehandler(behandlingId: UUID, saksbehandler: Saksbehandler): Saksbehandler {
        "update behandling set saksbehandler = :saksbehandler where id=:id".oppdatering(
            mapOf(
                "id" to behandlingId,
                "saksbehandler" to saksbehandler.id
            )
        )
        return saksbehandler
    }

    override fun leggTilUtbetaling(behandlingId: UUID, utbetalingId: UUID30) {
        """
            update behandling set utbetalingId=:utbetalingId where id=:id
        """.oppdatering(
            mapOf(
                "id" to behandlingId,
                "utbetalingId" to utbetalingId
            )
        )
    }

    private fun hentMånedsberegninger(beregningId: UUID, session: Session) =
        "select * from månedsberegning where beregningId = :id".hentListe(mapOf("id" to beregningId), session) {
            it.toMånedsberegning()
        }.toMutableList()

    private fun Row.toMånedsberegning() = Månedsberegning(
        id = uuid("id"),
        opprettet = instant("opprettet"),
        fom = localDate("fom"),
        tom = localDate("tom"),
        grunnbeløp = int("grunnbeløp"),
        sats = Sats.valueOf(string("sats")),
        beløp = int("beløp"),
        fradrag = int("fradrag")
    )

    private fun opprettMånedsberegning(beregningId: UUID, månedsberegning: Månedsberegning) {
        """
            insert into månedsberegning (id, opprettet, fom, tom, grunnbeløp, beregningId, sats, beløp, fradrag)
            values (:id, :opprettet, :fom, :tom, :grunnbelop, :beregningId, :sats, :belop, :fradrag)
        """.oppdatering(
            mapOf(
                "id" to månedsberegning.id,
                "opprettet" to månedsberegning.opprettet,
                "fom" to månedsberegning.fom,
                "tom" to månedsberegning.tom,
                "grunnbelop" to månedsberegning.grunnbeløp,
                "beregningId" to beregningId,
                "sats" to månedsberegning.sats.name,
                "belop" to månedsberegning.beløp,
                "fradrag" to månedsberegning.fradrag
            )
        )
    }

    private fun hentFradrag(beregningId: UUID, session: Session) =
        "select * from fradrag where beregningId = :id".hentListe(mapOf("id" to beregningId), session) {
            it.toFradrag()
        }.toMutableList()

    private fun Row.toFradrag() = Fradrag(
        id = uuid("id"),
        beløp = int("beløp"),
        beskrivelse = stringOrNull("beskrivelse"),
        type = Fradragstype.valueOf(string("fradragstype"))
    )

    private fun opprettFradrag(beregningId: UUID, fradrag: Fradrag) {
        """
            insert into fradrag (id, beregningId, fradragstype, beløp, beskrivelse)
            values (:id, :beregningId, :fradragstype, :belop, :beskrivelse)
        """
            .oppdatering(
                mapOf(
                    "id" to fradrag.id,
                    "beregningId" to beregningId,
                    "fradragstype" to fradrag.type.toString(),
                    "belop" to fradrag.beløp,
                    "beskrivelse" to fradrag.beskrivelse
                )
            )
    }

    override fun addSimulering(utbetalingId: UUID30, simulering: Simulering): Simulering {
        "update utbetaling set simulering = to_json(:simulering::json) where id = :id".oppdatering(
            mapOf(
                "id" to utbetalingId,
                "simulering" to objectMapper.writeValueAsString(simulering)
            )
        )
        return simulering
    }

    override fun addKvittering(utbetalingId: UUID30, kvittering: Kvittering): Kvittering {
        "update utbetaling set kvittering = to_json(:kvittering::json) where id = :id".oppdatering(
            mapOf(
                "id" to utbetalingId,
                "kvittering" to objectMapper.writeValueAsString(kvittering)
            )
        )
        return kvittering
    }

    override fun addOppdragsmelding(utbetalingId: UUID30, oppdragsmelding: Oppdragsmelding): Oppdragsmelding {
        "update utbetaling set oppdragsmelding = to_json(:oppdragsmelding::json) where id = :id".oppdatering(
            mapOf(
                "id" to utbetalingId,
                "oppdragsmelding" to objectMapper.writeValueAsString(oppdragsmelding)
            )
        )
        return oppdragsmelding
    }

    /**
     * Tow-part operation to avoid issues caused by lost precision when converting to/from instant/timestamp
     * 1. Get rows for extended interval.
     * 2. Filter in code to utilize precision of instant to get extact rows.
     */
    override fun hentUtbetalingerForAvstemming(fom: Instant, tom: Instant): List<Utbetaling> =
        using(sessionOf(dataSource)) { session ->
            val adjustedFom = fom.minus(1, ChronoUnit.DAYS)
            val adjustedTom = tom.plus(1, ChronoUnit.DAYS)
            """select * from utbetaling where oppdragsmelding is not null and (oppdragsmelding ->> 'tidspunkt')::timestamptz >= :fom and (oppdragsmelding ->> 'tidspunkt')::timestamptz < :tom and oppdragsmelding ->> 'status' = :status""".trimMargin()
                .hentListe(
                    mapOf(
                        "fom" to adjustedFom,
                        "tom" to adjustedTom,
                        "status" to Oppdragsmelding.Oppdragsmeldingstatus.SENDT.name
                    ),
                    session
                ) {
                    it.toUtbetaling(session)
                }.filter {
                    it.getOppdragsmelding()!!.tidspunkt.between(fom, tom)
                }
        }

    override fun opprettAvstemming(avstemming: Avstemming): Avstemming {
        """
            insert into avstemming (id, opprettet, fom, tom, utbetalinger, avstemmingXmlRequest)
            values (:id, :opprettet, :fom, :tom, to_json(:utbetalinger::json), :avstemmingXmlRequest)
        """.oppdatering(
            mapOf(
                "id" to avstemming.id,
                "opprettet" to avstemming.opprettet,
                "fom" to avstemming.fom,
                "tom" to avstemming.tom,
                "utbetalinger" to objectMapper.writeValueAsString(avstemming.utbetalinger.map { it.id.toString() }),
                "avstemmingXmlRequest" to avstemming.avstemmingXmlRequest
            )
        )
        return avstemming
    }

    override fun hentSisteAvstemming() = using(sessionOf(dataSource)) { session ->
        """
            select * from avstemming order by tom desc limit 1
        """.hent(emptyMap(), session) {
            it.toAvstemming(session)
        }
    }

    private fun Row.toAvstemming(session: Session) = Avstemming(
        id = uuid30("id"),
        opprettet = instant("opprettet"),
        fom = instant("fom"),
        tom = instant("tom"),
        utbetalinger = stringOrNull("utbetalinger")?.let { utbetalingListAsString ->
            objectMapper.readValue(utbetalingListAsString, List::class.java).map { utbetalingId ->
                hentUtbetalingInternal(UUID30(utbetalingId as String), session)!!
            }
        }!!,
        avstemmingXmlRequest = stringOrNull("avstemmingXmlRequest")
    )

    override fun addAvstemmingId(utbetalingId: UUID30, avstemmingId: UUID30): UUID30 {
        """
            update utbetaling set avstemmingId = :avstemmingId where id = :id
        """.oppdatering(
            mapOf(
                "id" to utbetalingId,
                "avstemmingId" to avstemmingId
            )
        )
        return avstemmingId
    }

    override fun oppdaterHendelseslogg(hendelseslogg: Hendelseslogg): Hendelseslogg {
        "insert into hendelseslogg (id, hendelser) values (:id, to_json(:hendelser::json)) on conflict(id) do update set hendelser=to_json(:hendelser::json)".oppdatering(
            mapOf(
                "id" to hendelseslogg.id,
                "hendelser" to HendelseListWriter.writeValueAsString(hendelseslogg.hendelser())
            )
        )
        return hendelseslogg
    }

    internal fun hentHendelseslogg(id: String) = using(sessionOf(dataSource)) { session ->
        "select * from hendelseslogg where id=:id".hent(
            mapOf("id" to id),
            session
        ) { it.toHendelseslogg() }
    }

    fun Row.toHendelseslogg(): Hendelseslogg {
        return Hendelseslogg(
            id = string(columnLabel = "id"),
            hendelser = stringOrNull("hendelser")?.let { HendelseListReader.readValue(it) } ?: mutableListOf()
        ).also {
            it.addObserver(this@DatabaseRepo)
        }
    }
}
