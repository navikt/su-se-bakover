package no.nav.su.se.bakover.database.person

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.NySak
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import org.junit.jupiter.api.Test

internal class PersonPostgresRepoTest {
    private val FNR = FnrGenerator.random()
    private val EPSFNR = FnrGenerator.random()
    private val repo = PersonPostgresRepo(EmbeddedDatabase.instance())

    private val eps = Behandlingsinformasjon.EktefellePartnerSamboer.Ektefelle(
        fnr = EPSFNR,
        navn = Person.Navn(fornavn = "", mellomnavn = null, etternavn = ""),
        kjønn = null,
        adressebeskyttelse = null,
        skjermet = null
    )

    @Test
    fun `hent fnr for sak gir søkers fnr`() {
        withDbWithData {
            val fnrs = repo.hentFnrForSak(sak.id)
            fnrs shouldContainExactlyInAnyOrder listOf(FNR)
        }
    }

    @Test
    fun `hent fnr for sak gir også EPSs fnr`() {
        withDbWithDataAndEps(eps) {
            val fnrs = repo.hentFnrForSak(sak.id)
            fnrs shouldContainExactlyInAnyOrder listOf(FNR, EPSFNR)
        }
    }

    @Test
    fun `hent fnr for søknad gir søkers fnr`() {
        withDbWithData {
            val fnrs = repo.hentFnrForSøknad(søknad.id)
            fnrs shouldContainExactlyInAnyOrder listOf(FNR)
        }
    }

    @Test
    fun `hent fnr for søknad gir også EPSs fnr`() {
        withDbWithDataAndEps(eps) {
            val fnrs = repo.hentFnrForSøknad(søknad.id)
            fnrs shouldContainExactlyInAnyOrder listOf(FNR, EPSFNR)
        }
    }

    @Test
    fun `hent fnr for behandling gir brukers fnr`() {
        withDbWithData {
            val fnrs = repo.hentFnrForBehandling(behandling.id)
            fnrs shouldContainExactlyInAnyOrder listOf(FNR)
        }
    }

    @Test
    fun `hent fnr for behandling gir også EPSs fnr`() {
        withDbWithDataAndEps(eps) {
            val fnrs = repo.hentFnrForBehandling(behandling.id)
            fnrs shouldContainExactlyInAnyOrder listOf(FNR, EPSFNR)
        }
    }

    @Test
    fun `hent fnr for utbetaling gir søkers fnr`() {
        withDbWithData {
            val fnrs = repo.hentFnrForUtbetaling(utbetaling.id)
            fnrs shouldContainExactlyInAnyOrder listOf(FNR)
        }
    }

    @Test
    fun `hent fnr for utbetaling gir også EPSs fnr`() {
        withDbWithDataAndEps(eps) {
            val fnrs = repo.hentFnrForUtbetaling(utbetaling.id)
            fnrs shouldContainExactlyInAnyOrder listOf(FNR, EPSFNR)
        }
    }

    private fun withDbWithData(test: Ctx.() -> Unit) {
        withDbWithDataAndEps(null, test)
    }

    private fun withDbWithDataAndEps(
        eps: Behandlingsinformasjon.EktefellePartnerSamboer.Ektefelle?,
        test: Ctx.() -> Unit
    ) {
        val testDataHelper = TestDataHelper(EmbeddedDatabase.instance())
        withMigratedDb {
            val sak = testDataHelper.insertSak(FNR)
            val søknad = testDataHelper.insertSøknad(sak.id)
            val behandling = testDataHelper.insertBehandling(sak.id, søknad)
            val behandlingsinformasjon = testDataHelper.insertBehandlingsinformasjonMedEps(behandling.id, eps)
            val utbetaling = Utbetaling.OversendtUtbetaling.UtenKvittering(
                id = UUID30.randomUUID(),
                utbetalingslinjer = listOf(),
                fnr = FNR,
                avstemmingsnøkkel = Avstemmingsnøkkel(),
                simulering = Simulering(
                    gjelderId = FNR,
                    gjelderNavn = "",
                    datoBeregnet = idag(),
                    nettoBeløp = 0,
                    periodeList = listOf()
                ),
                utbetalingsrequest = Utbetalingsrequest(
                    value = ""
                ),
                type = Utbetaling.UtbetalingsType.NY,
                oppdragId = sak.oppdrag.id,
                behandler = NavIdentBruker.Attestant("Z123")
            )
            testDataHelper.opprettUtbetaling(utbetaling)

            Ctx(sak, søknad, behandling, utbetaling, behandlingsinformasjon).test()
        }
    }

    private data class Ctx(
        val sak: NySak,
        val søknad: Søknad,
        val behandling: NySøknadsbehandling,
        val utbetaling: Utbetaling,
        val behandlingsinformasjon: Behandlingsinformasjon
    )
}
