package no.nav.su.se.bakover.database.person

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.Revurdering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import org.junit.jupiter.api.Test

internal class PersonPostgresRepoTest {
    private val FNR = FnrGenerator.random()
    private val EPSFNR = FnrGenerator.random()
    private val repo = PersonPostgresRepo(EmbeddedDatabase.instance())

    private val eps = Behandlingsinformasjon.EktefellePartnerSamboer.Ektefelle(
        fnr = EPSFNR,
        navn = Person.Navn(fornavn = "", mellomnavn = null, etternavn = ""),
        kjønn = null,
        fødselsdato = null,
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

    @Test
    fun `hent fnr for revurdering gir søkers fnr`() {
        withDbWithData {
            val fnrs = repo.hentFnrForRevurdering(revurderingId = revurdering.id)
            fnrs shouldContainExactlyInAnyOrder listOf(FNR)
        }
    }

    @Test
    fun `hent fnr for revurdering gir også EPSs fnr`() {
        withDbWithDataAndEps(eps) {
            val fnrs = repo.hentFnrForRevurdering(revurderingId = revurdering.id)
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
            val sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave(FNR)
            val søknad = sak.søknader().first() as Søknad.Journalført.MedOppgave
            val behandling = testDataHelper.uavklartVilkårsvurdering(
                sak, søknad,
                Behandlingsinformasjon.lagTomBehandlingsinformasjon().copy(
                    ektefelle = eps
                )
            )
            val utbetaling = Utbetaling.OversendtUtbetaling.UtenKvittering(
                id = UUID30.randomUUID(),
                utbetalingslinjer = listOf(),
                sakId = sak.id,
                saksnummer = Saksnummer(-99),
                fnr = FNR,
                avstemmingsnøkkel = Avstemmingsnøkkel(),
                simulering = Simulering(
                    gjelderId = FNR,
                    gjelderNavn = "",
                    datoBeregnet = idag(),
                    nettoBeløp = 0,
                    periodeList = listOf()
                ),
                utbetalingsrequest = Utbetalingsrequest(""),
                type = Utbetaling.UtbetalingsType.NY,
                behandler = NavIdentBruker.Attestant("Z123")
            )
            val revurdering = testDataHelper.insertRevurdering(behandling.id)
            testDataHelper.opprettUtbetaling(utbetaling)

            Ctx(sak, søknad, behandling, utbetaling, behandling.behandlingsinformasjon, revurdering).test()
        }
    }

    private data class Ctx(
        val sak: Sak,
        val søknad: Søknad.Journalført.MedOppgave,
        val behandling: Søknadsbehandling.Vilkårsvurdert.Uavklart,
        val utbetaling: Utbetaling,
        val behandlingsinformasjon: Behandlingsinformasjon,
        val revurdering: Revurdering,
    )
}
