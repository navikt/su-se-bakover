package no.nav.su.se.bakover.database.person

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.beregning
import no.nav.su.se.bakover.database.simulering
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
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
    fun `hent fnr for sak gir behandlings EPSs fnr`() {
        withDbWithDataAndBehandlingEps(ektefellePartnerSamboerFnr) {
            val fnrs = repo.hentFnrForSak(innvilgetSøknadsbehandling.sakId)
            fnrs shouldContainExactlyInAnyOrder listOf(innvilgetSøknadsbehandling.fnr, ektefellePartnerSamboerFnr)
        }
    }

    @Test
    fun `hent fnr for sak gir behandling og revurderings EPSs fnr`() {
        val revurderingEps = FnrGenerator.random()
        withDbWithDataAndBehandlingEpsAndNyRevurderingEps(ektefellePartnerSamboerFnr, revurderingEps) {
            val fnrs = repo.hentFnrForSak(innvilgetSøknadsbehandling.sakId)
            fnrs shouldContainExactlyInAnyOrder listOf(
                innvilgetSøknadsbehandling.fnr,
                ektefellePartnerSamboerFnr,
                revurderingEps,
            )
        }
    }

    @Test
    fun `hent fnr for sak gir behandling og revurdering og revurdering av revurdering EPSs fnr`() {
        val revurderingEps = FnrGenerator.random()
        val revurderingAvRevurderingEps = FnrGenerator.random()
        withDbWithDataAndBehandlingEpsAndNyRevurderingOgRevurderingAvRevurderingEps(
            ektefellePartnerSamboerFnr,
            revurderingEps,
            revurderingAvRevurderingEps,
        ) {
            val fnrs = repo.hentFnrForSak(innvilgetSøknadsbehandling.sakId)
            fnrs shouldContainExactlyInAnyOrder listOf(
                innvilgetSøknadsbehandling.fnr,
                ektefellePartnerSamboerFnr,
                revurderingEps,
                revurderingAvRevurderingEps,
            )
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
        withDbWithDataAndBehandlingEps(ektefellePartnerSamboerFnr) {
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
        withDbWithDataAndBehandlingEps(ektefellePartnerSamboerFnr) {
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
        withDbWithDataAndBehandlingEps(ektefellePartnerSamboerFnr) {
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
        withDbWithDataAndBehandlingEps(ektefellePartnerSamboerFnr) {
            val fnrs = repo.hentFnrForRevurdering(revurderingId = revurdering.id)
            fnrs shouldContainExactlyInAnyOrder listOf(innvilgetSøknadsbehandling.fnr, ektefellePartnerSamboerFnr)
        }
    }

    private fun withDbWithData(test: Ctx.() -> Unit) {
        withDbWithDataAndBehandlingEps(null, test)
    }

    private fun withDbWithDataAndBehandlingEps(
        epsFnr: Fnr?,
        test: Ctx.() -> Unit,
    ) {
        val testDataHelper = TestDataHelper(EmbeddedDatabase.instance())
        withMigratedDb {
            val (innvilget, utbetaling) = testDataHelper.nyIverksattInnvilget(epsFnr = epsFnr)
            val vedtak = testDataHelper.vedtakForSøknadsbehandlingOgUtbetalingId(innvilget, utbetaling.id)
            val revurdering = testDataHelper.nyRevurdering(vedtak, vedtak.periode, epsFnr)

            Ctx(innvilget, utbetaling, revurdering).test()
        }
    }

    private fun withDbWithDataAndBehandlingEpsAndNyRevurderingEps(
        epsFnrBehandling: Fnr?,
        epsFnrRevurdering: Fnr?,
        test: Ctx.() -> Unit,
    ) {
        val testDataHelper = TestDataHelper(EmbeddedDatabase.instance())
        withMigratedDb {
            val (innvilget, utbetaling) = testDataHelper.nyIverksattInnvilget(epsFnr = epsFnrBehandling)
            val vedtak = testDataHelper.vedtakForSøknadsbehandlingOgUtbetalingId(innvilget, utbetaling.id)
            val revurdering = testDataHelper.nyRevurdering(vedtak, vedtak.periode, epsFnrRevurdering)

            Ctx(innvilget, utbetaling, revurdering).test()
        }
    }

    private fun withDbWithDataAndBehandlingEpsAndNyRevurderingOgRevurderingAvRevurderingEps(
        epsFnrBehandling: Fnr?,
        epsFnrRevurdering: Fnr?,
        epsFnrRevurderingAvRevurdering: Fnr?,
        test: Ctx.() -> Unit,
    ) {
        val testDataHelper = TestDataHelper(EmbeddedDatabase.instance())
        withMigratedDb {
            val (innvilget, utbetaling) = testDataHelper.nyIverksattInnvilget(epsFnr = epsFnrBehandling)
            val vedtak = testDataHelper.vedtakForSøknadsbehandlingOgUtbetalingId(innvilget, utbetaling.id)
            val revurdering = testDataHelper.nyRevurdering(vedtak, vedtak.periode, epsFnrRevurdering)
            val revurderingVedtak = testDataHelper.vedtakForRevurdering(
                RevurderingTilAttestering.Innvilget(
                    id = revurdering.id,
                    periode = revurdering.periode,
                    opprettet = revurdering.opprettet,
                    tilRevurdering = revurdering.tilRevurdering,
                    saksbehandler = revurdering.saksbehandler,
                    oppgaveId = revurdering.oppgaveId,
                    fritekstTilBrev = revurdering.fritekstTilBrev,
                    revurderingsårsak = revurdering.revurderingsårsak,
                    beregning = beregning(revurdering.periode),
                    behandlingsinformasjon = revurdering.behandlingsinformasjon,
                    simulering = simulering(revurdering.fnr),
                    forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
                    grunnlagsdata = revurdering.grunnlagsdata,
                    vilkårsvurderinger = revurdering.vilkårsvurderinger,
                    informasjonSomRevurderes = revurdering.informasjonSomRevurderes,
                    attesteringer = Attesteringshistorikk.empty()
                ),
            ).first
            val revurderingAvRevurdering = testDataHelper.nyRevurdering(
                revurderingVedtak,
                revurderingVedtak.periode,
                epsFnrRevurderingAvRevurdering,
            )
            Ctx(innvilget, utbetaling, revurderingAvRevurdering).test()
        }
    }

    private data class Ctx(
        val innvilgetSøknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget,
        val utbetaling: Utbetaling.OversendtUtbetaling.UtenKvittering,
        val revurdering: Revurdering,
    )
}
