package no.nav.su.se.bakover.database

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import kotliquery.using
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.UUIDFactory
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.beregning.BeregningRepoInternal.hentBeregningForBehandling
import no.nav.su.se.bakover.database.utbetaling.UtbetalingInternalRepo.hentUtbetalingInternal
import no.nav.su.se.bakover.database.utbetaling.UtbetalingInternalRepo.hentUtbetalinger
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
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.hendelseslogg.Hendelseslogg
import no.nav.su.se.bakover.domain.hendelseslogg.HendelsesloggPersistenceObserver
import no.nav.su.se.bakover.domain.hendelseslogg.hendelse.HendelseListReader
import no.nav.su.se.bakover.domain.hendelseslogg.hendelse.HendelseListWriter
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag.OppdragPersistenceObserver
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingPersistenceObserver
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import java.time.Clock
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

    override fun opprettUtbetaling(oppdragId: UUID30, utbetaling: Utbetaling) {
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
            opprettet = tidspunkt("opprettet"),
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
            opprettet = tidspunkt("opprettet"),
            sakId = uuid("sakId"),
            utbetalinger = hentUtbetalinger(
                oppdragId,
                session
            ).also { utbetalinger -> utbetalinger.forEach { it.addObserver(this@DatabaseRepo) } }
        ).also { it.addObserver(this@DatabaseRepo) }
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
            opprettet = tidspunkt("opprettet")
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
            opprettet = tidspunkt("opprettet"),
            søknad = hentSøknadInternal(uuid("søknadId"), session)!!,
            beregning = hentBeregningForBehandling(behandlingId, session),
            utbetaling = stringOrNull("utbetalingId")?.let {
                hentUtbetalingInternal(
                    UUID30.fromString(it),
                    session
                )!!.apply { addObserver(this@DatabaseRepo) }
            },
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

    override fun opprettBeregning(behandlingId: UUID, beregning: Beregning): Beregning {
        deleteBeregninger(behandlingId)
        "insert into beregning (id, opprettet, fom, tom, behandlingId, sats, forventetInntekt) values (:id, :opprettet, :fom, :tom, :behandlingId, :sats, :forventetInntekt)".oppdatering(
            mapOf(
                "id" to beregning.id,
                "opprettet" to beregning.opprettet,
                "fom" to beregning.fom,
                "tom" to beregning.tom,
                "behandlingId" to behandlingId,
                "sats" to beregning.sats.name,
                "forventetInntekt" to beregning.forventetInntekt
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

    override fun addSimulering(utbetalingId: UUID30, simulering: Simulering) {
        "update utbetaling set simulering = to_json(:simulering::json) where id = :id".oppdatering(
            mapOf(
                "id" to utbetalingId,
                "simulering" to objectMapper.writeValueAsString(simulering)
            )
        )
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
