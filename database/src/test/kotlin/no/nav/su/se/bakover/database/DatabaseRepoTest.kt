package no.nav.su.se.bakover.database

import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotliquery.queryOf
import kotliquery.using
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.database.utbetaling.UtbetalingInternalRepo.hentUtbetalingslinjer
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.BehandlingPersistenceObserver
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.PersistenceObserver
import no.nav.su.se.bakover.domain.PersistenceObserverException
import no.nav.su.se.bakover.domain.PersistentDomainObject
import no.nav.su.se.bakover.domain.SakPersistenceObserver
import no.nav.su.se.bakover.domain.Saksbehandler
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
    fun `saksbehandle behandling`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            val søknad = insertSøknad(sak.id)
            val behandling = insertBehandling(sak.id, søknad)

            val saksbehandler = repo.settSaksbehandler(behandling.id, Saksbehandler("Per"))
            val hentet = repo.hentBehandling(behandling.id)!!

            hentet.saksbehandler() shouldBe saksbehandler
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
    fun `opprett og slett beregning`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            val søknad = insertSøknad(sak.id)
            val behandling = insertBehandling(sak.id, søknad)

            insertBeregning(behandling.id)
            repo.deleteBeregninger(behandling.id)

            val hentet = repo.hentBehandling(behandling.id)
            hentet!!.beregning() shouldBe null
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
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.desember(2020),
                    sats = Sats.HØY,
                    fradrag = listOf(
                        Fradrag(
                            type = Fradragstype.AndreYtelser,
                            beløp = 10000
                        )
                    ),
                    forventetInntekt = 200
                )
            )

            selectCount(from = "beregning", where = "behandlingId", id = behandling.id.toString()) shouldBe 1
            selectCount(from = "beregning", where = "id", id = gammelBeregning.id.toString()) shouldBe 1
            selectCount(from = "månedsberegning", where = "beregningId", id = gammelBeregning.id.toString()) shouldBe 12
            selectCount(from = "fradrag", where = "beregningId", id = gammelBeregning.id.toString()) shouldBe 1

            val nyBeregning = Beregning(
                fraOgMed = 1.januar(2020),
                tilOgMed = 31.desember(2020),
                sats = Sats.HØY,
                fradrag = emptyList(),
                forventetInntekt = 0
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

            val utbetaling = insertUtbetaling(sak.oppdrag.id)
            val hentetUtbetalinger = repo.hentUtbetaling(utbetaling.id)
            utbetaling shouldBe hentetUtbetalinger
            listOf(hentetUtbetalinger!!, utbetaling).forEach {
                assertPersistenceObserverAssigned(it, utbetalingPersistenceObserver())
            }
        }
    }

    @Test
    fun `opprett og hent utbetalingslinjer`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            val utbetaling = insertUtbetaling(sak.oppdrag.id)
            val utbetalingslinje1 = insertUtbetalingslinje(utbetaling.id, null)
            val utbetalingslinje2 = insertUtbetalingslinje(utbetaling.id, utbetalingslinje1.forrigeUtbetalingslinjeId)
            using(sessionOf(EmbeddedDatabase.instance())) {
                val hentet = hentUtbetalingslinjer(utbetaling.id, it)
                listOf(utbetalingslinje1, utbetalingslinje2) shouldBe hentet
            }
        }
    }

    @Test
    fun `sletter eksisterende utbetalinger og utbetalingslinjer dersom det lages nye for samme behandling`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            val utbetaling = insertUtbetaling(sak.oppdrag.id)
            val utbetalingslinje1 = insertUtbetalingslinje(utbetaling.id, null)
            val utbetalingslinje2 = insertUtbetalingslinje(utbetaling.id, utbetalingslinje1.forrigeUtbetalingslinjeId)

            val hentet = repo.hentUtbetaling(utbetaling.id)
            hentet!!.utbetalingslinjer shouldBe listOf(utbetalingslinje1, utbetalingslinje2)

            val nyeLinjer = listOf(
                Utbetalingslinje(
                    fraOgMed = 1.mai(2020),
                    tilOgMed = 30.april(2020),
                    beløp = 5000,
                    forrigeUtbetalingslinjeId = null
                )
            )

            val nyUtbetaling = Utbetaling(
                utbetalingslinjer = nyeLinjer,
                fnr = FNR
            )

            repo.opprettUtbetaling(
                oppdragId = sak.oppdrag.id,
                utbetaling = nyUtbetaling
            )
            val nyHenting = repo.hentUtbetaling(nyUtbetaling.id)
            nyHenting!!.utbetalingslinjer shouldBe nyeLinjer
        }
    }

    @Test
    fun `beskytter mot sletting av utbetalinger som ikke skal slettes`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            val utbetaling = insertUtbetaling(sak.oppdrag.id)
            val utbetalingslinje1 = insertUtbetalingslinje(utbetaling.id, null)
            val utbetalingslinje2 = insertUtbetalingslinje(utbetaling.id, utbetalingslinje1.forrigeUtbetalingslinjeId)
            utbetaling.addOppdragsmelding(Oppdragsmelding(Oppdragsmelding.Oppdragsmeldingstatus.SENDT, ""))
            utbetaling.addKvittering(Kvittering(Kvittering.Utbetalingsstatus.OK, ""))

            assertThrows<IllegalStateException> { repo.slettUtbetaling(utbetaling) }

            val skulleIkkeSlettes = repo.hentUtbetaling(utbetaling.id)
            skulleIkkeSlettes!!.id shouldBe utbetaling.id
            skulleIkkeSlettes.utbetalingslinjer shouldBe listOf(utbetalingslinje1, utbetalingslinje2)
        }
    }

    @Test
    fun `legg til og hent simulering`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            val utbetaling = insertUtbetaling(sak.oppdrag.id)
            val simulering = Simulering(
                gjelderId = Fnr("12345678910"),
                gjelderNavn = "gjelderNavn",
                datoBeregnet = LocalDate.now(),
                nettoBeløp = 1,
                periodeList = listOf(
                    SimulertPeriode(
                        fraOgMed = LocalDate.now(),
                        tilOgMed = LocalDate.now(),
                        utbetaling = listOf(
                            SimulertUtbetaling(
                                fagSystemId = "fagSystemId",
                                utbetalesTilId = Fnr("12345678910"),
                                utbetalesTilNavn = "utbetalesTilNavn",
                                forfall = LocalDate.now(),
                                feilkonto = false,
                                detaljer = listOf(
                                    SimulertDetaljer(
                                        faktiskFraOgMed = LocalDate.now(),
                                        faktiskTilOgMed = LocalDate.now(),
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
            ).also {
                repo.addSimulering(
                    utbetalingId = utbetaling.id,
                    simulering = it
                )
            }
            val hentet = repo.hentUtbetaling(utbetaling.id)!!.getSimulering()!!
            simulering shouldBe hentet
        }
    }

    @Test
    fun `legg til og hent kvittering`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            val utbetaling = insertUtbetaling(sak.oppdrag.id)
            val kvittering = Kvittering(
                utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
                originalKvittering = "someXmlHere",
                mottattTidspunkt = Tidspunkt.EPOCH
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
            val utbetaling = insertUtbetaling(sak.oppdrag.id)
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
        override fun nySøknad(sakId: UUID, søknad: Søknad) = throw NotImplementedError()
        override fun opprettSøknadsbehandling(sakId: UUID, behandling: Behandling) = throw NotImplementedError()
    }

    private fun voidObserver() = object : VoidObserver {}

    private fun behandlingPersistenceObserver() = object : BehandlingPersistenceObserver {
        override fun opprettBeregning(behandlingId: UUID, beregning: Beregning): Beregning {
            throw NotImplementedError()
        }

        override fun deleteBeregninger(behandlingId: UUID) {
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
            return Oppdrag(
                id = UUID30.randomUUID(),
                opprettet = Tidspunkt.EPOCH,
                sakId = sakId
            )
        }

        override fun hentFnr(sakId: UUID): Fnr {
            return Fnr("sakId")
        }

        override fun attester(behandlingId: UUID, attestant: Attestant): Attestant {
            return attestant
        }

        override fun settSaksbehandler(behandlingId: UUID, saksbehandler: Saksbehandler): Saksbehandler {
            return saksbehandler
        }

        override fun leggTilUtbetaling(behandlingId: UUID, utbetalingId: UUID30) {}
    }

    private fun oppdragPersistenceObserver() = object : OppdragPersistenceObserver {
        override fun opprettUtbetaling(oppdragId: UUID30, utbetaling: Utbetaling) = throw NotImplementedError()

        override fun slettUtbetaling(utbetaling: Utbetaling) = throw NotImplementedError()

        override fun hentFnr(sakId: UUID): Fnr {
            return FNR
        }
    }

    private fun utbetalingPersistenceObserver() = object : UtbetalingPersistenceObserver {
        override fun addSimulering(utbetalingId: UUID30, simulering: Simulering) {
            throw NotImplementedError()
        }

        override fun addKvittering(utbetalingId: UUID30, kvittering: Kvittering): Kvittering {
            throw NotImplementedError()
        }

        override fun addOppdragsmelding(utbetalingId: UUID30, oppdragsmelding: Oppdragsmelding): Oppdragsmelding {
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

    private fun insertUtbetaling(oppdragId: UUID30): Utbetaling = Utbetaling(
        utbetalingslinjer = emptyList(),
        fnr = FNR
    ).also {
        repo.opprettUtbetaling(
            oppdragId = oppdragId,
            utbetaling = it
        )
    }

    private fun insertUtbetalingslinje(utbetalingId: UUID30, forrigeUtbetalingslinjeId: UUID30?) =
        repo.opprettUtbetalingslinje(
            utbetalingId = utbetalingId,
            utbetalingslinje = Utbetalingslinje(
                fraOgMed = 1.januar(2020),
                tilOgMed = 31.desember(2020),
                forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId,
                beløp = 25000
            )
        )

    private fun insertBeregning(behandlingId: UUID) = repo.opprettBeregning(
        behandlingId = behandlingId,
        beregning = Beregning(
            fraOgMed = 1.januar(2020),
            tilOgMed = 31.desember(2020),
            sats = Sats.HØY,
            fradrag = emptyList(),
            forventetInntekt = 0
        )
    )
}
