package no.nav.su.se.bakover.database.person

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.innvilgetBeregning
import no.nav.su.se.bakover.database.simulering
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeBehovForTilbakekrevingUnderBehandling
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
            val (sak, vedtak, utbetaling) = testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering(
                epsFnr = epsFnr,
            )
            val revurdering = testDataHelper.persisterRevurderingOpprettet((sak to vedtak), vedtak.periode, epsFnr).second

            Ctx(
                dataSource,
                testDataHelper.personRepo,
                sak.søknadsbehandlinger.first() as Søknadsbehandling.Iverksatt.Innvilget,
                utbetaling,
                revurdering,
            ).test()
        }
    }

    private fun withDbWithDataAndBehandlingEpsAndNyRevurderingEps(
        epsFnrBehandling: Fnr?,
        epsFnrRevurdering: Fnr?,
        test: Ctx.() -> Unit,
    ) {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val (sak, vedtak, utbetaling) = testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering(
                epsFnr = epsFnrBehandling,
            )
            val revurdering = testDataHelper.persisterRevurderingOpprettet((sak to vedtak), vedtak.periode, epsFnrRevurdering).second

            Ctx(
                dataSource = dataSource,
                repo = testDataHelper.personRepo,
                innvilgetSøknadsbehandling = sak.søknadsbehandlinger.first() as Søknadsbehandling.Iverksatt.Innvilget,
                utbetaling = utbetaling,
                revurdering = revurdering,
            ).test()
        }
    }

    private fun withDbWithDataAndSøknadsbehandlingVedtakAndRevurderingVedtak(
        epsFnrBehandling: Fnr?,
        epsFnrRevurdering: Fnr?,
        test: VedtakCtx.() -> Unit,
    ) {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val (sak, vedtak, _) = testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering(
                epsFnr = epsFnrBehandling,
            )
            val revurdering = testDataHelper.persisterRevurderingOpprettet((sak to vedtak), vedtak.periode, epsFnrRevurdering).second
            val revurderingVedtak =
                testDataHelper.persisterVedtakMedInnvilgetRevurderingOgOversendtUtbetalingMedKvittering(
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
                        tilbakekrevingsbehandling = IkkeBehovForTilbakekrevingUnderBehandling,
                        sakinfo = revurdering.sakinfo,
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
            val (sak, vedtak, utbetaling) = testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering(
                epsFnr = epsFnrBehandling,
            )
            val (sak2, revurdering) = testDataHelper.persisterRevurderingOpprettet((sak to vedtak), vedtak.periode, epsFnrRevurdering)
            val revurderingVedtak =
                testDataHelper.persisterVedtakMedInnvilgetRevurderingOgOversendtUtbetalingMedKvittering(
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
                        tilbakekrevingsbehandling = IkkeBehovForTilbakekrevingUnderBehandling,
                        sakinfo = revurdering.sakinfo,
                    ),
                ).first
            val revurderingAvRevurdering = testDataHelper.persisterRevurderingOpprettet(
                sakOgVedtak = sak2 to revurderingVedtak,
                periode = revurderingVedtak.periode,
                epsFnr = epsFnrRevurderingAvRevurdering,
            )
            Ctx(
                dataSource = dataSource,
                repo = testDataHelper.personRepo,
                innvilgetSøknadsbehandling = sak.søknadsbehandlinger.first() as Søknadsbehandling.Iverksatt.Innvilget,
                utbetaling = utbetaling,
                revurdering = revurderingAvRevurdering.second,
            ).test()
        }
    }

    private data class Ctx(
        val dataSource: DataSource,
        val repo: PersonPostgresRepo,
        val innvilgetSøknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget,
        val utbetaling: Utbetaling.OversendtUtbetaling.MedKvittering,
        val revurdering: Revurdering,
    )

    private data class VedtakCtx(
        val dataSource: DataSource,
        val repo: PersonPostgresRepo,
        val søknadsbehandlingVedtak: VedtakSomKanRevurderes,
        val revurderingVedtak: VedtakSomKanRevurderes,
    )
}
