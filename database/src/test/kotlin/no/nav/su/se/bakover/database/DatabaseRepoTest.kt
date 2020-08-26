package no.nav.su.se.bakover.database

import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotliquery.sessionOf
import kotliquery.using
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.BehandlingPersistenceObserver
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.PersistenceObserver
import no.nav.su.se.bakover.domain.PersistenceObserverException
import no.nav.su.se.bakover.domain.PersistentDomainObject
import no.nav.su.se.bakover.domain.SakPersistenceObserver
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.Vilkår.FLYKTNING
import no.nav.su.se.bakover.domain.Vilkår.UFØRHET
import no.nav.su.se.bakover.domain.Vilkårsvurdering
import no.nav.su.se.bakover.domain.VilkårsvurderingPersistenceObserver
import no.nav.su.se.bakover.domain.VoidObserver
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag.OppdragPersistenceObserver
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingPersistenceObserver
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertDetaljer
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertUtbetaling
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.postgresql.util.PSQLException
import java.time.LocalDate
import java.util.UUID

internal class DatabaseRepoTest {

    private val repo = DatabaseRepo(EmbeddedDatabase.instance())
    private val FNR = FnrGenerator.random()

    @Test
    fun `unknown entities`() {
        withMigratedDb {
            assertNull(repo.hentSak(FnrGenerator.random()))
            assertNull(repo.hentSak(UUID.randomUUID()))
            assertTrue(repo.hentVilkårsvurderinger(UUID.randomUUID()).isEmpty())
            assertNull(repo.hentBehandling(UUID.randomUUID()))
            assertNull(repo.hentSøknad(UUID.randomUUID()))
        }
    }

    @Test
    fun `opprett og hent sak`() {
        withMigratedDb {
            val opprettet = repo.opprettSak(FNR)
            val hentetId = repo.hentSak(opprettet.id)
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

            behandling.status() shouldBe Behandling.BehandlingsStatus.VILKÅRSVURDERT

            val oppdatertStatus = repo.oppdaterBehandlingStatus(behandling.id, Behandling.BehandlingsStatus.BEREGNET)
            val hentet = repo.hentBehandling(behandling.id)

            hentet!!.status() shouldBe oppdatertStatus
        }
    }

    @Test
    fun `opprett og hent vilkårsvurderinger`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            val søknad = insertSøknad(sak.id)
            val behandling = insertBehandling(sak.id, søknad)
            val vilkårsvurderinger = insertVilkårsvurderinger(behandling.id)

            val hentet = repo.hentVilkårsvurderinger(behandling.id)

            vilkårsvurderinger shouldBe hentet
            vilkårsvurderinger shouldHaveSize 2

            (vilkårsvurderinger + hentet).forEach {
                assertPersistenceObserverAssigned(it, vilkårsvurderingPersistenceObserver())
            }
        }
    }

    @Test
    fun `oppdater vilkårsvurderinger`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            val søknad = insertSøknad(sak.id)
            val behandling = insertBehandling(sak.id, søknad)
            val vilkårsvurderinger = insertVilkårsvurderinger(behandling.id)
            val uførhet = vilkårsvurderinger.first { it.vilkår == UFØRHET }

            val oppdatert = repo.oppdaterVilkårsvurdering(
                Vilkårsvurdering(
                    id = uførhet.id,
                    vilkår = UFØRHET,
                    begrunnelse = "OK",
                    status = Vilkårsvurdering.Status.OK
                )
            )

            val hentet = repo.hentVilkårsvurderinger(behandling.id)
                .first { it.vilkår == UFØRHET }

            oppdatert shouldBe hentet
        }
    }

    @Test
    fun `opprett og hent beregning`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            val søknad = insertSøknad(sak.id)
            val behandling = insertBehandling(sak.id, søknad)
            val beregning = insertBeregning(behandling.id)

            val hentetBeregninger = repo.hentBeregninger(behandling.id)

            listOf(beregning) shouldBe hentetBeregninger
            (hentetBeregninger + beregning).forEach {
                assertNoPersistenceObserverAssigned(it, voidObserver())
            }
        }
    }

    @Test
    fun `opprett og hent oppdrag`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            val oppdragId = UUID30.randomUUID()
            val oppdrag = insertOppdrag(oppdragId, sak.id)
            val hentetOppdrag = repo.hentOppdrag(oppdragId)

            oppdrag shouldBe hentetOppdrag
            oppdrag.sakId shouldBe sak.id
            oppdrag.id shouldBe oppdragId

            listOf(oppdrag, hentetOppdrag).forEach {
                assertPersistenceObserverAssigned(it!!, oppdragPersistenceObserver())
            }
        }
    }

    @Test
    fun `opprett og hent utbetaling`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            val søknad = insertSøknad(sak.id)
            val behandling = insertBehandling(sak.id, søknad)
            val oppdragId = UUID30.randomUUID()

            insertOppdrag(oppdragId, sak.id)
            val utbetaling = insertUtbetaling(oppdragId, behandling.id)
            using(sessionOf(EmbeddedDatabase.instance())) {
                val hentetUtbetalinger = repo.hentUtbetalinger(oppdragId, it)
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
            val oppdragId = UUID30.randomUUID()
            val behandling = insertBehandling(sak.id, søknad)
            insertOppdrag(oppdragId, sak.id)
            val utbetaling = insertUtbetaling(oppdragId, behandling.id)
            val utbetalingslinje1 = insertUtbetalingslinje(utbetaling.id, null)
            val utbetalingslinje2 = insertUtbetalingslinje(utbetaling.id, utbetalingslinje1.forrigeUtbetalingslinjeId)
            using(sessionOf(EmbeddedDatabase.instance())) {
                val hentet = repo.hentUtbetalingslinjer(utbetaling.id, it)
                listOf(utbetalingslinje1, utbetalingslinje2) shouldBe hentet
            }
        }
    }

    @Test
    fun `legg til og hent simulering`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            val søknad = insertSøknad(sak.id)
            val oppdragId = UUID30.randomUUID()
            val behandling = insertBehandling(sak.id, søknad)
            insertOppdrag(oppdragId, sak.id)
            val utbetaling = insertUtbetaling(oppdragId, behandling.id)
            val simulering = repo.addSimulering(
                utbetaling.id,
                Simulering(
                    gjelderId = "gjelderId",
                    gjelderNavn = "gjelderNavn",
                    datoBeregnet = LocalDate.now(),
                    totalBelop = 1,
                    periodeList = listOf(
                        SimulertPeriode(
                            fom = LocalDate.now(),
                            tom = LocalDate.now(),
                            utbetaling = listOf(
                                SimulertUtbetaling(
                                    fagSystemId = "fagSystemId",
                                    utbetalesTilId = "utbetalesTilId",
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
                                            utbetalingsType = "utbetalingsType",
                                            refunderesOrgNr = "refunderesOrgNr"
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
            using(sessionOf(EmbeddedDatabase.instance())) {
                val hentet = repo.hentUtbetalinger(oppdragId, it).first().getSimulering()!!
                simulering shouldBe hentet
            }
        }
    }

    @Test
    fun `combination of oppdragId and SakId should be unique`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            insertOppdrag(UUID30.randomUUID(), sak.id)
            shouldThrowExactly<PSQLException> { insertOppdrag(UUID30.randomUUID(), sak.id) }.also {
                it.message shouldContain "duplicate key value violates unique constraint"
            }
        }
    }

    private fun sakPersistenceObserver() = object : SakPersistenceObserver {
        override fun nySøknad(sakId: UUID, søknad: Søknad): Søknad {
            throw NotImplementedError()
        }

        override fun opprettSøknadsbehandling(sakId: UUID, behandling: Behandling): Behandling {
            throw NotImplementedError()
        }

        override fun opprettOppdrag(oppdrag: Oppdrag): Oppdrag {
            throw NotImplementedError()
        }
    }

    private fun voidObserver() = object : VoidObserver {}

    private fun behandlingPersistenceObserver() = object : BehandlingPersistenceObserver {
        override fun opprettVilkårsvurderinger(
            behandlingId: UUID,
            vilkårsvurderinger: List<Vilkårsvurdering>
        ): List<Vilkårsvurdering> {
            throw NotImplementedError()
        }

        override fun opprettBeregning(behandlingId: UUID, beregning: Beregning): Beregning {
            throw NotImplementedError()
        }

        override fun oppdaterBehandlingStatus(
            behandlingId: UUID,
            status: Behandling.BehandlingsStatus
        ): Behandling.BehandlingsStatus {
            throw NotImplementedError()
        }
    }

    private fun vilkårsvurderingPersistenceObserver() = object : VilkårsvurderingPersistenceObserver {
        override fun oppdaterVilkårsvurdering(vilkårsvurdering: Vilkårsvurdering): Vilkårsvurdering {
            throw NotImplementedError()
        }
    }

    private fun oppdragPersistenceObserver() = object : OppdragPersistenceObserver {
        override fun opprettUbetaling(oppdragId: UUID30, utbetaling: Utbetaling): Utbetaling {
            throw NotImplementedError()
        }
    }

    private fun utbetalingPersistenceObserver() = object : UtbetalingPersistenceObserver {
        override fun addSimulering(utbetalingId: UUID30, simulering: Simulering): Simulering {
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
            status = Behandling.BehandlingsStatus.VILKÅRSVURDERT
        )
    )

    private fun insertOppdrag(oppdragId: UUID30, sakId: UUID) = repo.opprettOppdrag(
        oppdrag = Oppdrag(
            id = oppdragId,
            sakId = sakId
        )
    )

    private fun insertUtbetaling(oppdragId: UUID30, behandlingId: UUID) = repo.opprettUbetaling(
        oppdragId = oppdragId,
        utbetaling = Utbetaling(
            oppdragId = oppdragId,
            behandlingId = behandlingId,
            utbetalingslinjer = emptyList()
        )
    )

    private fun insertUtbetalingslinje(utbetalingId: UUID30, forrigeUtbetalingslinjeId: UUID30?) = repo.opprettUtbetalingslinje(
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

    private fun insertVilkårsvurderinger(behandlingId: UUID) =
        repo.opprettVilkårsvurderinger(
            behandlingId = behandlingId,
            vilkårsvurderinger = listOf(
                Vilkårsvurdering(
                    vilkår = UFØRHET,
                    begrunnelse = "",
                    status = Vilkårsvurdering.Status.IKKE_VURDERT
                ),
                Vilkårsvurdering(
                    vilkår = FLYKTNING,
                    begrunnelse = "",
                    status = Vilkårsvurdering.Status.IKKE_VURDERT
                )
            )
        )
}
