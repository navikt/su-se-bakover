package no.nav.su.se.bakover.database.person

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.innvilgetBeregning
import no.nav.su.se.bakover.database.simulering
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrevingsbehandling
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.test.generer
import org.junit.jupiter.api.Test
import javax.sql.DataSource

internal class PersonPostgresRepoTest {

    private val ektefellePartnerSamboerFnr = Fnr.generer()

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
        val revurderingEps = Fnr.generer()
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
        val revurderingEps = Fnr.generer()
        val revurderingAvRevurderingEps = Fnr.generer()
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

    @Test
    fun `hent fnr for vedtak søknadsbehandling`() {
        val epsFnrSøknadsbehandling = Fnr.generer()
        val epsFnrRevurdering = Fnr.generer()
        withDbWithDataAndSøknadsbehandlingVedtakAndRevurderingVedtak(
            epsFnrBehandling = epsFnrSøknadsbehandling,
            epsFnrRevurdering = epsFnrRevurdering,
        ) {
            repo.hentFnrForVedtak(vedtakId = this.søknadsbehandlingVedtak.id) shouldContainExactlyInAnyOrder listOf(
                søknadsbehandlingVedtak.behandling.fnr,
                epsFnrSøknadsbehandling,
            )

            repo.hentFnrForVedtak(vedtakId = this.revurderingVedtak.id) shouldContainExactlyInAnyOrder listOf(
                revurderingVedtak.behandling.fnr,
                epsFnrRevurdering,
            )
        }
    }

    private fun withDbWithData(test: Ctx.() -> Unit) {
        withDbWithDataAndBehandlingEps(null, test)
    }

    private fun withDbWithDataAndBehandlingEps(
        epsFnr: Fnr?,
        test: Ctx.() -> Unit,
    ) {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val (innvilget, utbetaling) = testDataHelper.nyIverksattInnvilget(epsFnr = epsFnr)
            val vedtak = testDataHelper.vedtakForSøknadsbehandlingOgUtbetalingId(innvilget, utbetaling.id)
            val revurdering = testDataHelper.nyRevurdering(vedtak, vedtak.periode, epsFnr)

            Ctx(dataSource, testDataHelper.personRepo, innvilget, utbetaling, revurdering).test()
        }
    }

    private fun withDbWithDataAndBehandlingEpsAndNyRevurderingEps(
        epsFnrBehandling: Fnr?,
        epsFnrRevurdering: Fnr?,
        test: Ctx.() -> Unit,
    ) {

        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val (innvilget, utbetaling) = testDataHelper.nyIverksattInnvilget(epsFnr = epsFnrBehandling)
            val vedtak = testDataHelper.vedtakForSøknadsbehandlingOgUtbetalingId(innvilget, utbetaling.id)
            val revurdering = testDataHelper.nyRevurdering(vedtak, vedtak.periode, epsFnrRevurdering)

            Ctx(dataSource, testDataHelper.personRepo, innvilget, utbetaling, revurdering).test()
        }
    }

    private fun withDbWithDataAndSøknadsbehandlingVedtakAndRevurderingVedtak(
        epsFnrBehandling: Fnr?,
        epsFnrRevurdering: Fnr?,
        test: VedtakCtx.() -> Unit,
    ) {

        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
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
                    beregning = innvilgetBeregning(revurdering.periode),
                    simulering = simulering(revurdering.fnr),
                    forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles,
                    grunnlagsdata = revurdering.grunnlagsdata,
                    vilkårsvurderinger = revurdering.vilkårsvurderinger,
                    informasjonSomRevurderes = revurdering.informasjonSomRevurderes,
                    attesteringer = Attesteringshistorikk.empty(),
                    avkorting = AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
                    tilbakekrevingsbehandling = Tilbakekrevingsbehandling.IkkeBehovForTilbakekreving,
                ),
            ).first

            VedtakCtx(
                dataSource = dataSource,
                repo = testDataHelper.personRepo,
                søknadsbehandlingVedtak = vedtak,
                revurderingVedtak = revurderingVedtak,
            ).test()
        }
    }

    private fun withDbWithDataAndBehandlingEpsAndNyRevurderingOgRevurderingAvRevurderingEps(
        epsFnrBehandling: Fnr?,
        epsFnrRevurdering: Fnr?,
        epsFnrRevurderingAvRevurdering: Fnr?,
        test: Ctx.() -> Unit,
    ) {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
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
                    beregning = innvilgetBeregning(revurdering.periode),
                    simulering = simulering(revurdering.fnr),
                    forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles,
                    grunnlagsdata = revurdering.grunnlagsdata,
                    vilkårsvurderinger = revurdering.vilkårsvurderinger,
                    informasjonSomRevurderes = revurdering.informasjonSomRevurderes,
                    attesteringer = Attesteringshistorikk.empty(),
                    avkorting = AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
                    tilbakekrevingsbehandling = Tilbakekrevingsbehandling.IkkeBehovForTilbakekreving
                ),
            ).first
            val revurderingAvRevurdering = testDataHelper.nyRevurdering(
                revurderingVedtak,
                revurderingVedtak.periode,
                epsFnrRevurderingAvRevurdering,
            )
            Ctx(dataSource, testDataHelper.personRepo, innvilget, utbetaling, revurderingAvRevurdering).test()
        }
    }

    private data class Ctx(
        val dataSource: DataSource,
        val repo: PersonPostgresRepo,
        val innvilgetSøknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget,
        val utbetaling: Utbetaling.OversendtUtbetaling.UtenKvittering,
        val revurdering: Revurdering,
    )

    private data class VedtakCtx(
        val dataSource: DataSource,
        val repo: PersonPostgresRepo,
        val søknadsbehandlingVedtak: VedtakSomKanRevurderes,
        val revurderingVedtak: VedtakSomKanRevurderes,
    )
}
