package no.nav.su.se.bakover.database

import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.BehandlingPersistenceObserver
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.PersistenceObserver
import no.nav.su.se.bakover.domain.PersistenceObserverException
import no.nav.su.se.bakover.domain.PersistentDomainObject
import no.nav.su.se.bakover.domain.SakPersistenceObserver
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.VoidObserver
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Fradrag
import no.nav.su.se.bakover.domain.beregning.Fradragstype
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.hendelseslogg.Hendelseslogg
import no.nav.su.se.bakover.domain.hendelseslogg.hendelse.behandling.UnderkjentAttestering
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag.OppdragPersistenceObserver
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingPersistenceObserver
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseType
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertDetaljer
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertUtbetaling
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.postgresql.util.PSQLException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class DatabaseRepoTest {

    private val repo = DatabaseRepo(EmbeddedDatabase.instance())
    private val FNR = FnrGenerator.random()

    @Test
    fun `unknown entities`() {
        withMigratedDb {
            assertNull(repo.hentSak(FnrGenerator.random()))
            assertNull(repo.hentSak(UUID.randomUUID()))
            assertNull(repo.hentBehandling(UUID.randomUUID()))
            assertNull(repo.hentSøknad(UUID.randomUUID()))
        }
    }

    @Test
    fun `opprett og hent sak`() {
        withMigratedDb {
            val opprettet = repo.opprettSak(FNR)
            val hentetId = repo.hentSak(opprettet.id)!!
            val hentetFnr = repo.hentSak(FNR)

            opprettet shouldBe hentetId
            hentetId shouldBe hentetFnr

            opprettet.fnr shouldBe FNR

            listOf(opprettet, hentetId, hentetFnr).forEach {
                assertPersistenceObserverAssigned(it!!, sakPersistenceObserver())
            }
        }
    }

    @Test
    fun `opprett og hent søknad`() {
        withMigratedDb {
            using(sessionOf(EmbeddedDatabase.instance())) {
                val sak = insertSak(FNR)
                val søknad = insertSøknad(sak.id)
                val hentetId = repo.hentSøknad(søknad.id)

                søknad shouldBe hentetId
                assertNoPersistenceObserverAssigned(søknad, voidObserver())
            }
        }
    }

    @Test
    fun `opprett og hent behandling`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            val søknad = insertSøknad(sak.id)
            val behandling = insertBehandling(sak.id, søknad)

            val hentet = repo.hentBehandling(behandling.id)

            behandling shouldBe hentet
            listOf(behandling, hentet).forEach {
                assertPersistenceObserverAssigned(it!!, behandlingPersistenceObserver())
            }
        }
    }

    @Test
    fun `oppdater behandlingstatus`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            val søknad = insertSøknad(sak.id)
            val behandling = insertBehandling(sak.id, søknad)

            behandling.status() shouldBe Behandling.BehandlingsStatus.VILKÅRSVURDERT_INNVILGET

            val oppdatertStatus = repo.oppdaterBehandlingStatus(behandling.id, Behandling.BehandlingsStatus.BEREGNET)
            val hentet = repo.hentBehandling(behandling.id)

            hentet!!.status() shouldBe oppdatertStatus
        }
    }

    @Test
    fun `attesterer behandling`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            val søknad = insertSøknad(sak.id)
            val behandling = insertBehandling(sak.id, søknad)

            val attestant = repo.attester(behandling.id, Attestant("kjella"))
            val hentet = repo.hentBehandling(behandling.id)!!

            hentet.attestant() shouldBe attestant
        }
    }

    @Test
    fun `oppdater behandlingsinformasjon`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            val søknad = insertSøknad(sak.id)
            val behandling = insertBehandling(sak.id, søknad)

            val oppdatert = repo.oppdaterBehandlingsinformasjon(
                behandling.id,
                Behandlingsinformasjon(
                    uførhet = Behandlingsinformasjon.Uførhet(
                        status = Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
                        uføregrad = 40,
                        forventetInntekt = 200
                    )
                )
            )

            val hentet = repo.hentBehandling(behandling.id)

            oppdatert shouldBe hentet!!.behandlingsinformasjon()
        }
    }

    @Test
    fun `opprett og hent beregning`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            val søknad = insertSøknad(sak.id)
            val behandling = insertBehandling(sak.id, søknad)
            val beregning = insertBeregning(behandling.id)

            val hentet = repo.hentBeregning(behandling.id)

            beregning shouldBe hentet
            listOf(hentet, beregning).forEach {
                assertNoPersistenceObserverAssigned(it!!, voidObserver())
            }
        }
    }

    @Test
    fun `sletter eksisterende beregninger når nye opprettes`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            val søknad = insertSøknad(sak.id)
            val behandling = insertBehandling(sak.id, søknad)
            val gammelBeregning = repo.opprettBeregning(
                behandling.id,
                Beregning(
                    fom = 1.januar(2020),
                    tom = 31.desember(2020),
                    sats = Sats.HØY,
                    fradrag = listOf(
                        Fradrag(
                            type = Fradragstype.AndreYtelser,
                            beløp = 10000
                        )
                    )
                )
            )

            selectCount(from = "beregning", where = "behandlingId", id = behandling.id.toString()) shouldBe 1
            selectCount(from = "beregning", where = "id", id = gammelBeregning.id.toString()) shouldBe 1
            selectCount(from = "månedsberegning", where = "beregningId", id = gammelBeregning.id.toString()) shouldBe 12
            selectCount(from = "fradrag", where = "beregningId", id = gammelBeregning.id.toString()) shouldBe 1

            val nyBeregning = Beregning(
                fom = 1.januar(2020),
                tom = 31.desember(2020),
                sats = Sats.HØY,
                fradrag = emptyList()
            )
            repo.opprettBeregning(behandling.id, nyBeregning)

            selectCount(from = "beregning", where = "behandlingId", id = behandling.id.toString()) shouldBe 1

            selectCount(from = "beregning", where = "id", id = nyBeregning.id.toString()) shouldBe 1
            selectCount(from = "månedsberegning", where = "beregningId", id = nyBeregning.id.toString()) shouldBe 12
            selectCount(from = "fradrag", where = "beregningId", id = nyBeregning.id.toString()) shouldBe 0

            selectCount(from = "beregning", where = "id", id = gammelBeregning.id.toString()) shouldBe 0
            selectCount(from = "månedsberegning", where = "beregningId", id = gammelBeregning.id.toString()) shouldBe 0
            selectCount(from = "fradrag", where = "beregningId", id = gammelBeregning.id.toString()) shouldBe 0
        }
    }

    private fun selectCount(from: String, where: String, id: String) =
        using(sessionOf(EmbeddedDatabase.instance())) { session ->
            session.run(
                queryOf(
                    "select count(*) from $from where $where='$id'",
                    emptyMap()
                ).map { it.int("count") }.asSingle
            )
        }

    @Test
    fun `opprett og hent oppdrag`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            val hentetOppdrag = repo.hentOppdrag(sak.oppdrag.id)

            sak.oppdrag shouldBe hentetOppdrag
            sak.oppdrag.sakId shouldBe sak.id

            listOf(sak.oppdrag, hentetOppdrag).forEach {
                assertPersistenceObserverAssigned(it!!, oppdragPersistenceObserver())
            }
        }
    }

    @Test
    fun `opprett og hent oppdrag for sak`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            val hentetSak = repo.hentSak(sak.id)

            listOf(hentetSak).forEach {
                assertPersistenceObserverAssigned(it!!, sakPersistenceObserver())
            }
        }
    }

    @Test
    fun `opprett og hent utbetaling`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            val søknad = insertSøknad(sak.id)
            val behandling = insertBehandling(sak.id, søknad)

            val utbetaling = insertUtbetaling(sak.oppdrag.id, behandling.id)
            using(sessionOf(EmbeddedDatabase.instance())) {
                val hentetUtbetalinger = repo.hentUtbetalinger(sak.oppdrag.id, it)
                listOf(utbetaling) shouldBe hentetUtbetalinger
                (hentetUtbetalinger + utbetaling).forEach {
                    assertPersistenceObserverAssigned(it, utbetalingPersistenceObserver())
                }
            }
        }
    }

    @Test
    fun `opprett og hent oppdragslinjer`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            val søknad = insertSøknad(sak.id)
            val behandling = insertBehandling(sak.id, søknad)
            val utbetaling = insertUtbetaling(sak.oppdrag.id, behandling.id)
            val utbetalingslinje1 = insertUtbetalingslinje(utbetaling.id, null)
            val utbetalingslinje2 = insertUtbetalingslinje(utbetaling.id, utbetalingslinje1.forrigeUtbetalingslinjeId)
            using(sessionOf(EmbeddedDatabase.instance())) {
                val hentet = repo.hentUtbetalingslinjer(utbetaling.id, it)
                listOf(utbetalingslinje1, utbetalingslinje2) shouldBe hentet
            }
        }
    }

    @Test
    fun `sletter eksisterende utbetalinger og utbetalingslinjer dersom det lages nye for samme behandling`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            val søknad = insertSøknad(sak.id)
            val behandling = insertBehandling(sak.id, søknad)
            val utbetaling = insertUtbetaling(sak.oppdrag.id, behandling.id)
            val utbetalingslinje1 = insertUtbetalingslinje(utbetaling.id, null)
            val utbetalingslinje2 = insertUtbetalingslinje(utbetaling.id, utbetalingslinje1.forrigeUtbetalingslinjeId)

            val hentet = repo.hentUtbetalingForBehandling(behandling.id)
            hentet!!.utbetalingslinjer shouldBe listOf(utbetalingslinje1, utbetalingslinje2)

            val nyeLinjer = listOf(
                Utbetalingslinje(
                    fom = 1.mai(2020),
                    tom = 30.april(2020),
                    beløp = 5000,
                    forrigeUtbetalingslinjeId = null
                )
            )

            val nyUtbetaling = Utbetaling(
                behandlingId = behandling.id,
                utbetalingslinjer = nyeLinjer
            )

            repo.opprettUtbetaling(
                oppdragId = sak.oppdrag.id,
                utbetaling = nyUtbetaling
            )
            val nyHenting = repo.hentUtbetalingForBehandling(behandling.id)
            nyHenting!!.utbetalingslinjer shouldBe nyeLinjer
        }
    }

    @Test
    fun `beskytter mot sletting av utbetalinger som ikke skal slettes`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            val søknad = insertSøknad(sak.id)
            val behandling = insertBehandling(sak.id, søknad)
            val utbetaling = insertUtbetaling(sak.oppdrag.id, behandling.id)
            val utbetalingslinje1 = insertUtbetalingslinje(utbetaling.id, null)
            val utbetalingslinje2 = insertUtbetalingslinje(utbetaling.id, utbetalingslinje1.forrigeUtbetalingslinjeId)
            utbetaling.addOppdragsmelding(Oppdragsmelding(Oppdragsmelding.Oppdragsmeldingstatus.SENDT, ""))
            utbetaling.addKvittering(Kvittering(Kvittering.Utbetalingsstatus.OK, ""))

            assertThrows<IllegalStateException> {
                repo.opprettUtbetaling(
                    sak.oppdrag.id,
                    Utbetaling(
                        behandlingId = behandling.id,
                        utbetalingslinjer = emptyList()
                    )
                )
            }

            val skulleIkkeSlettes = repo.hentUtbetalingForBehandling(behandling.id)
            skulleIkkeSlettes!!.id shouldBe utbetaling.id
            skulleIkkeSlettes.utbetalingslinjer shouldBe listOf(utbetalingslinje1, utbetalingslinje2)
        }
    }

    @Test
    fun `legg til og hent simulering`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            val søknad = insertSøknad(sak.id)
            val behandling = insertBehandling(sak.id, søknad)
            val utbetaling = insertUtbetaling(sak.oppdrag.id, behandling.id)
            val simulering = repo.addSimulering(
                utbetaling.id,
                Simulering(
                    gjelderId = Fnr("12345678910"),
                    gjelderNavn = "gjelderNavn",
                    datoBeregnet = LocalDate.now(),
                    nettoBeløp = 1,
                    periodeList = listOf(
                        SimulertPeriode(
                            fom = LocalDate.now(),
                            tom = LocalDate.now(),
                            utbetaling = listOf(
                                SimulertUtbetaling(
                                    fagSystemId = "fagSystemId",
                                    utbetalesTilId = Fnr("12345678910"),
                                    utbetalesTilNavn = "utbetalesTilNavn",
                                    forfall = LocalDate.now(),
                                    feilkonto = false,
                                    detaljer = listOf(
                                        SimulertDetaljer(
                                            faktiskFom = LocalDate.now(),
                                            faktiskTom = LocalDate.now(),
                                            konto = "konto",
                                            belop = 1,
                                            tilbakeforing = true,
                                            sats = 1,
                                            typeSats = "",
                                            antallSats = 1,
                                            uforegrad = 2,
                                            klassekode = "klassekode",
                                            klassekodeBeskrivelse = "klassekodeBeskrivelse",
                                            klasseType = KlasseType.YTEL
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
            val hentet = repo.hentUtbetaling(utbetaling.id)!!.getSimulering()!!
            simulering shouldBe hentet
        }
    }

    @Test
    fun `legg til og hent kvittering`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            val søknad = insertSøknad(sak.id)
            val behandling = insertBehandling(sak.id, søknad)
            val utbetaling = insertUtbetaling(sak.oppdrag.id, behandling.id)
            val kvittering = Kvittering(
                utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
                originalKvittering = "someXmlHere",
                mottattTidspunkt = Instant.EPOCH
            )
            repo.addKvittering(utbetaling.id, kvittering)
            val hentet = repo.hentUtbetaling(utbetaling.id)!!.getKvittering()!!
            kvittering shouldBe hentet
        }
    }

    @Test
    fun `legg til og hent oppdragsmelding`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            val søknad = insertSøknad(sak.id)
            val behandling = insertBehandling(sak.id, søknad)
            val utbetaling = insertUtbetaling(sak.oppdrag.id, behandling.id)
            val oppdragsmelding = Oppdragsmelding(Oppdragsmelding.Oppdragsmeldingstatus.SENDT, "some xml")
            repo.addOppdragsmelding(utbetaling.id, oppdragsmelding)

            val hentet = repo.hentUtbetaling(utbetaling.id)!!.getOppdragsmelding()!!
            hentet shouldBe oppdragsmelding
        }
    }

    @Test
    fun `combination of oppdragId and SakId should be unique`() {
        withMigratedDb {
            val sak = repo.opprettSak(FNR)
            shouldThrowExactly<PSQLException> {
                using(sessionOf(EmbeddedDatabase.instance())) {
                    val oppdragId = UUID30.randomUUID()
                    it.run(queryOf("insert into oppdrag (id, opprettet, sakId) values ('$oppdragId', now(), '${sak.id}')").asUpdate)
                }
            }.also {
                it.message shouldContain "duplicate key value violates unique constraint"
            }
        }
    }

    @Test
    fun `henter siste avstemming`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            val søknad = insertSøknad(sak.id)
            val behandling = insertBehandling(sak.id, søknad)
            val utbetaling = insertUtbetaling(sak.oppdrag.id, behandling.id)

            val zero = repo.hentSisteAvstemming()
            zero shouldBe null

            repo.opprettAvstemming(
                Avstemming(
                    fom = 1.januar(2020).atStartOfDay().toInstant(ZoneOffset.UTC),
                    tom = 2.januar(2020).atStartOfDay().toInstant(ZoneOffset.UTC),
                    utbetalinger = listOf(utbetaling)
                )
            )

            val second = repo.opprettAvstemming(
                Avstemming(
                    fom = 3.januar(2020).atStartOfDay().toInstant(ZoneOffset.UTC),
                    tom = 4.januar(2020).atStartOfDay().toInstant(ZoneOffset.UTC),
                    utbetalinger = listOf(utbetaling),
                    avstemmingXmlRequest = "<Root></Root>"
                )
            )

            val hentet = repo.hentSisteAvstemming()!!
            hentet shouldBe second
        }
    }

    @Test
    fun `hent utbetalinger for avstemming`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            val søknad = insertSøknad(sak.id)
            val behandling = insertBehandling(sak.id, søknad)
            val utbetaling = repo.opprettUtbetaling(
                oppdragId = sak.oppdrag.id,
                utbetaling = Utbetaling(
                    opprettet = 1.juli(2020).atStartOfDay().toInstant(ZoneOffset.UTC),
                    behandlingId = behandling.id,
                    utbetalingslinjer = emptyList()
                )
            )
            repo.addOppdragsmelding(utbetaling.id, Oppdragsmelding(Oppdragsmelding.Oppdragsmeldingstatus.SENDT, ""))

            repo.hentUtbetalingerTilAvstemming(
                fom = utbetaling.opprettet.minus(1, ChronoUnit.DAYS),
                tom = utbetaling.opprettet
            ) shouldBe emptyList()

            repo.hentUtbetalingerTilAvstemming(
                fom = utbetaling.opprettet,
                tom = utbetaling.opprettet.plus(1, ChronoUnit.DAYS)
            ) shouldHaveSize 1

            repo.hentUtbetalingerTilAvstemming(
                fom = utbetaling.opprettet.plus(1, ChronoUnit.DAYS),
                tom = utbetaling.opprettet.plus(3, ChronoUnit.DAYS)
            ) shouldBe emptyList()
        }
    }

    @Test
    fun `opprett og hent hendelseslogg`() {
        withMigratedDb {
            val tidspunkt = now()
            val underkjentAttestering = UnderkjentAttestering(
                attestant = "attestant",
                begrunnelse = "Dette er feil begrunnelse",
                tidspunkt = tidspunkt
            )

            val opprettet = repo.oppdaterHendelseslogg(Hendelseslogg("id"))
            val hentet = repo.hentHendelseslogg("id")!!
            hentet shouldBe opprettet

            repo.oppdaterHendelseslogg(Hendelseslogg("id", mutableListOf(underkjentAttestering)))
            repo.oppdaterHendelseslogg(Hendelseslogg("id", mutableListOf(underkjentAttestering)))

            val medHendelse = repo.hentHendelseslogg("id")!!
            medHendelse.id shouldBe "id"
            medHendelse.hendelser() shouldContainAll listOf(underkjentAttestering)
        }
    }

    private fun sakPersistenceObserver() = object : SakPersistenceObserver {
        override fun nySøknad(sakId: UUID, søknad: Søknad): Søknad {
            throw NotImplementedError()
        }

        override fun opprettSøknadsbehandling(sakId: UUID, behandling: Behandling): Behandling {
            throw NotImplementedError()
        }
    }

    private fun voidObserver() = object : VoidObserver {}

    private fun behandlingPersistenceObserver() = object : BehandlingPersistenceObserver {
        override fun opprettBeregning(behandlingId: UUID, beregning: Beregning): Beregning {
            throw NotImplementedError()
        }

        override fun oppdaterBehandlingStatus(
            behandlingId: UUID,
            status: Behandling.BehandlingsStatus
        ): Behandling.BehandlingsStatus {
            throw NotImplementedError()
        }

        override fun oppdaterBehandlingsinformasjon(
            behandlingId: UUID,
            behandlingsinformasjon: Behandlingsinformasjon
        ): Behandlingsinformasjon {
            throw NotImplementedError()
        }

        override fun hentOppdrag(sakId: UUID): Oppdrag {
            return Oppdrag(sakId = sakId)
        }

        override fun hentFnr(sakId: UUID): Fnr {
            return Fnr("sakId")
        }

        override fun attester(behandlingId: UUID, attestant: Attestant): Attestant {
            return attestant
        }
    }

    private fun oppdragPersistenceObserver() = object : OppdragPersistenceObserver {
        override fun opprettUtbetaling(oppdragId: UUID30, utbetaling: Utbetaling): Utbetaling {
            throw NotImplementedError()
        }
    }

    private fun utbetalingPersistenceObserver() = object : UtbetalingPersistenceObserver {
        override fun addSimulering(utbetalingId: UUID30, simulering: Simulering): Simulering {
            throw NotImplementedError()
        }

        override fun addKvittering(utbetalingId: UUID30, kvittering: Kvittering): Kvittering {
            throw NotImplementedError()
        }

        override fun addOppdragsmelding(utbetalingId: UUID30, oppdragsmelding: Oppdragsmelding): Oppdragsmelding {
            throw NotImplementedError()
        }

        override fun addAvstemmingId(utbetalingId: UUID30, avstemmingId: UUID30): UUID30 {
            throw NotImplementedError()
        }
    }

    private fun <T : PersistenceObserver> assertPersistenceObserverAssigned(
        target: PersistentDomainObject<T>,
        observer: T
    ) {
        assertThrows<PersistenceObserverException> {
            target.addObserver(observer)
        }.also {
            it.message shouldBe "There should only be one instance of type class no.nav.su.se.bakover.domain.PersistenceObserver assigned to an object!"
        }
    }

    private fun <T : PersistenceObserver> assertNoPersistenceObserverAssigned(
        target: PersistentDomainObject<T>,
        observer: T
    ) {
        assertDoesNotThrow {
            target.addObserver(observer)
        }
    }

    private fun insertSak(fnr: Fnr = FNR) = repo.opprettSak(fnr)
    private fun insertSøknad(sakId: UUID) = repo.opprettSøknad(
        sakId,
        Søknad(
            id = UUID.randomUUID(),
            søknadInnhold = SøknadInnholdTestdataBuilder.build()
        )
    )

    private fun insertBehandling(sakId: UUID, søknad: Søknad) = repo.opprettSøknadsbehandling(
        sakId = sakId,
        behandling = Behandling(
            søknad = søknad,
            status = Behandling.BehandlingsStatus.VILKÅRSVURDERT_INNVILGET,
            sakId = sakId
        )
    )

    private fun insertUtbetaling(oppdragId: UUID30, behandlingId: UUID) = repo.opprettUtbetaling(
        oppdragId = oppdragId,
        utbetaling = Utbetaling(
            behandlingId = behandlingId,
            utbetalingslinjer = emptyList()
        )
    )

    private fun insertUtbetalingslinje(utbetalingId: UUID30, forrigeUtbetalingslinjeId: UUID30?) =
        repo.opprettUtbetalingslinje(
            utbetalingId = utbetalingId,
            utbetalingslinje = Utbetalingslinje(
                fom = 1.januar(2020),
                tom = 31.desember(2020),
                forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId,
                beløp = 25000
            )
        )

    private fun insertBeregning(behandlingId: UUID) = repo.opprettBeregning(
        behandlingId = behandlingId,
        beregning = Beregning(
            fom = 1.januar(2020),
            tom = 31.desember(2020),
            sats = Sats.HØY,
            fradrag = emptyList()
        )
    )
}
