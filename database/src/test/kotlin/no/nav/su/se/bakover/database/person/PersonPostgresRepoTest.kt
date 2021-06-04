package no.nav.su.se.bakover.database.person

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import org.junit.jupiter.api.Test

internal class PersonPostgresRepoTest {
    private val ektefellePartnerSamboerFnr = FnrGenerator.random()
    private val repo = PersonPostgresRepo(EmbeddedDatabase.instance())

    @Test
    fun `hent fnr for sak gir søkers fnr`() {
        withDbWithData {
            val fnrs = repo.hentFnrForSak(innvilgetSøknadsbehandling.sakId)
            fnrs shouldContainExactlyInAnyOrder listOf(innvilgetSøknadsbehandling.fnr)
        }
    }

    @Test
    fun `hent fnr for sak gir også EPSs fnr`() {
        withDbWithDataAndEps(ektefellePartnerSamboerFnr) {
            val fnrs = repo.hentFnrForSak(innvilgetSøknadsbehandling.sakId)
            fnrs shouldContainExactlyInAnyOrder listOf(innvilgetSøknadsbehandling.fnr, ektefellePartnerSamboerFnr)
        }
    }

    @Test
    fun `hent fnr for søknad gir søkers fnr`() {
        withDbWithData {
            val fnrs = repo.hentFnrForSøknad(innvilgetSøknadsbehandling.søknad.id)
            fnrs shouldContainExactlyInAnyOrder listOf(innvilgetSøknadsbehandling.fnr)
        }
    }

    @Test
    fun `hent fnr for søknad gir også EPSs fnr`() {
        withDbWithDataAndEps(ektefellePartnerSamboerFnr) {
            val fnrs = repo.hentFnrForSøknad(innvilgetSøknadsbehandling.søknad.id)
            fnrs shouldContainExactlyInAnyOrder listOf(innvilgetSøknadsbehandling.fnr, ektefellePartnerSamboerFnr)
        }
    }

    @Test
    fun `hent fnr for behandling gir brukers fnr`() {
        withDbWithData {
            val fnrs = repo.hentFnrForBehandling(innvilgetSøknadsbehandling.id)
            fnrs shouldContainExactlyInAnyOrder listOf(innvilgetSøknadsbehandling.fnr)
        }
    }

    @Test
    fun `hent fnr for behandling gir også EPSs fnr`() {
        withDbWithDataAndEps(ektefellePartnerSamboerFnr) {
            val fnrs = repo.hentFnrForBehandling(innvilgetSøknadsbehandling.id)
            fnrs shouldContainExactlyInAnyOrder listOf(innvilgetSøknadsbehandling.fnr, ektefellePartnerSamboerFnr)
        }
    }

    @Test
    fun `hent fnr for utbetaling gir søkers fnr`() {
        withDbWithData {
            val fnrs = repo.hentFnrForUtbetaling(utbetaling.id)
            fnrs shouldContainExactlyInAnyOrder listOf(innvilgetSøknadsbehandling.fnr)
        }
    }

    @Test
    fun `hent fnr for utbetaling gir også EPSs fnr`() {
        withDbWithDataAndEps(ektefellePartnerSamboerFnr) {
            val fnrs = repo.hentFnrForUtbetaling(utbetaling.id)
            fnrs shouldContainExactlyInAnyOrder listOf(innvilgetSøknadsbehandling.fnr, ektefellePartnerSamboerFnr)
        }
    }

    @Test
    fun `hent fnr for revurdering gir søkers fnr`() {
        withDbWithData {
            val fnrs = repo.hentFnrForRevurdering(revurderingId = revurdering.id)
            fnrs shouldContainExactlyInAnyOrder listOf(innvilgetSøknadsbehandling.fnr)
        }
    }

    @Test
    fun `hent fnr for revurdering gir også EPSs fnr`() {
        withDbWithDataAndEps(ektefellePartnerSamboerFnr) {
            val fnrs = repo.hentFnrForRevurdering(revurderingId = revurdering.id)
            fnrs shouldContainExactlyInAnyOrder listOf(innvilgetSøknadsbehandling.fnr, ektefellePartnerSamboerFnr)
        }
    }

    private fun withDbWithData(test: Ctx.() -> Unit) {
        withDbWithDataAndEps(null, test)
    }

    private fun withDbWithDataAndEps(
        epsFnr: Fnr?,
        test: Ctx.() -> Unit
    ) {
        val testDataHelper = TestDataHelper(EmbeddedDatabase.instance())
        withMigratedDb {
            val (innvilget, utbetaling) = testDataHelper.nyIverksattInnvilget(epsFnr = epsFnr)
            val vedtak = testDataHelper.vedtakForSøknadsbehandlingOgUtbetalingId(innvilget, utbetaling.id)
            val revurdering = testDataHelper.nyRevurdering(vedtak, vedtak.periode, epsFnr)

            Ctx(innvilget, utbetaling, revurdering).test()
        }
    }

    private data class Ctx(
        val innvilgetSøknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget,
        val utbetaling: Utbetaling.OversendtUtbetaling.UtenKvittering,
        val revurdering: Revurdering,
    )
}
