package no.nav.su.se.bakover.database.person

import arrow.core.nonEmptyListOf
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeBehovForTilbakekrevingUnderBehandling
import no.nav.su.se.bakover.domain.person.PersonRepo
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.test.beregning
import no.nav.su.se.bakover.test.bosituasjonEpsUnder67
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.bosituasjongrunnlagEpsUførFlyktning
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.iverksattRevurdering
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.simulering
import no.nav.su.se.bakover.test.vilkår.formuevilkårMedEps0Innvilget
import no.nav.su.se.bakover.test.vilkår.formuevilkårUtenEps0Innvilget
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
            val bosituasjon = if (epsFnr == null) bosituasjongrunnlagEnslig() else bosituasjongrunnlagEpsUførFlyktning(epsFnr = epsFnr)
            val formueVilkår = if (epsFnr == null) formuevilkårUtenEps0Innvilget() else formuevilkårMedEps0Innvilget(bosituasjon = nonEmptyListOf(bosituasjongrunnlagEpsUførFlyktning(epsFnr = epsFnr)))

            val (sak, vedtak, utbetaling) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling { (sak, søknad) ->
                iverksattSøknadsbehandlingUføre(
                    sakInfo = SakInfo(
                        sakId = sak.id,
                        saksnummer = sak.saksnummer,
                        fnr = sak.fnr,
                        type = sak.type,
                    ),
                    sakOgSøknad = sak to søknad,
                    customGrunnlag = listOf(
                        bosituasjon,
                    ),
                    customVilkår = listOf(formueVilkår),
                )
            }
            val revurdering = testDataHelper.persisterRevurderingOpprettet((sak to vedtak)).second

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
            val bosituasjon = if (epsFnrBehandling == null) bosituasjongrunnlagEnslig() else bosituasjongrunnlagEpsUførFlyktning(epsFnr = epsFnrBehandling)
            val formueVilkår = if (epsFnrBehandling == null) formuevilkårUtenEps0Innvilget() else formuevilkårMedEps0Innvilget(bosituasjon = nonEmptyListOf(bosituasjongrunnlagEpsUførFlyktning(epsFnr = epsFnrBehandling)))

            val (sak, vedtak, utbetaling) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling { (sak, søknad) ->
                iverksattSøknadsbehandlingUføre(
                    sakInfo = SakInfo(
                        sakId = sak.id,
                        saksnummer = sak.saksnummer,
                        fnr = sak.fnr,
                        type = sak.type,
                    ),
                    sakOgSøknad = sak to søknad,
                    customGrunnlag = listOf(
                        bosituasjon,
                    ),
                    customVilkår = listOf(formueVilkår),
                )
            }

            val revurdering = testDataHelper.persisterRevurderingOpprettet((sak to vedtak)) { (s, v) ->
                opprettetRevurdering(
                    sakOgVedtakSomKanRevurderes = s to v,
                    grunnlagsdataOverrides = if (epsFnrRevurdering == null) listOf(bosituasjongrunnlagEnslig()) else listOf(bosituasjonEpsUnder67(fnr = epsFnrRevurdering)),
                )
            }.second

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
            val bosituasjon = if (epsFnrBehandling == null) bosituasjongrunnlagEnslig() else bosituasjongrunnlagEpsUførFlyktning(epsFnr = epsFnrBehandling)
            val formueVilkår = if (epsFnrBehandling == null) formuevilkårUtenEps0Innvilget() else formuevilkårMedEps0Innvilget(bosituasjon = nonEmptyListOf(bosituasjongrunnlagEpsUførFlyktning(epsFnr = epsFnrBehandling)))

            val (sak, vedtak, _) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling { (sak, søknad) ->
                iverksattSøknadsbehandlingUføre(
                    sakInfo = SakInfo(
                        sakId = sak.id,
                        saksnummer = sak.saksnummer,
                        fnr = sak.fnr,
                        type = sak.type,
                    ),
                    sakOgSøknad = sak to søknad,
                    customGrunnlag = listOf(
                        bosituasjon,
                    ),
                    customVilkår = listOf(formueVilkår),
                )
            }

            val revurdering = testDataHelper.persisterIverksattRevurdering((sak to vedtak)) { (s, v) ->
                iverksattRevurdering(
                    sakOgVedtakSomKanRevurderes = s to v,
                    grunnlagsdataOverrides = if (epsFnrRevurdering == null) listOf(bosituasjongrunnlagEnslig()) else listOf(bosituasjonEpsUnder67(fnr = epsFnrRevurdering)),
                )
            }.second
            val revurderingVedtak =
                testDataHelper.persisterVedtakMedInnvilgetRevurderingOgOversendtUtbetalingMedKvittering(
                    RevurderingTilAttestering.Innvilget(
                        id = revurdering.id,
                        periode = revurdering.periode,
                        opprettet = revurdering.opprettet,
                        tilRevurdering = revurdering.tilRevurdering,
                        saksbehandler = revurdering.saksbehandler,
                        oppgaveId = revurdering.oppgaveId,
                        revurderingsårsak = revurdering.revurderingsårsak,
                        beregning = beregning(periode = revurdering.periode),
                        simulering = simulering(fnr = revurdering.fnr),
                        grunnlagsdata = revurdering.grunnlagsdata,
                        vilkårsvurderinger = revurdering.vilkårsvurderinger,
                        informasjonSomRevurderes = revurdering.informasjonSomRevurderes,
                        attesteringer = Attesteringshistorikk.empty(),
                        avkorting = AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
                        tilbakekrevingsbehandling = IkkeBehovForTilbakekrevingUnderBehandling,
                        sakinfo = revurdering.sakinfo,
                        brevvalgRevurdering = revurdering.brevvalgRevurdering,
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
            val bosituasjon = if (epsFnrBehandling == null) bosituasjongrunnlagEnslig() else bosituasjongrunnlagEpsUførFlyktning(epsFnr = epsFnrBehandling)
            val formueVilkår = if (epsFnrBehandling == null) formuevilkårUtenEps0Innvilget() else formuevilkårMedEps0Innvilget(bosituasjon = nonEmptyListOf(bosituasjongrunnlagEpsUførFlyktning(epsFnr = epsFnrBehandling)))

            val (sak, vedtak, utbetaling) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling { (sak, søknad) ->
                iverksattSøknadsbehandlingUføre(
                    sakInfo = SakInfo(
                        sakId = sak.id,
                        saksnummer = sak.saksnummer,
                        fnr = sak.fnr,
                        type = sak.type,
                    ),
                    sakOgSøknad = sak to søknad,
                    customGrunnlag = listOf(
                        bosituasjon,
                    ),
                    customVilkår = listOf(formueVilkår),
                )
            }

            val (sak2, revurdering) = testDataHelper.persisterIverksattRevurdering((sak to vedtak)) { (s, v) ->
                iverksattRevurdering(
                    sakOgVedtakSomKanRevurderes = s to v,
                    grunnlagsdataOverrides = if (epsFnrRevurdering == null) listOf(bosituasjongrunnlagEnslig()) else listOf(bosituasjonEpsUnder67(fnr = epsFnrRevurdering)),
                )
            }
            val revurderingVedtak =
                testDataHelper.persisterVedtakMedInnvilgetRevurderingOgOversendtUtbetalingMedKvittering(
                    RevurderingTilAttestering.Innvilget(
                        id = revurdering.id,
                        periode = revurdering.periode,
                        opprettet = revurdering.opprettet,
                        tilRevurdering = revurdering.tilRevurdering,
                        saksbehandler = revurdering.saksbehandler,
                        oppgaveId = revurdering.oppgaveId,
                        revurderingsårsak = revurdering.revurderingsårsak,
                        beregning = beregning(periode = revurdering.periode),
                        simulering = simulering(revurdering.fnr),
                        grunnlagsdata = revurdering.grunnlagsdata,
                        vilkårsvurderinger = revurdering.vilkårsvurderinger,
                        informasjonSomRevurderes = revurdering.informasjonSomRevurderes,
                        attesteringer = Attesteringshistorikk.empty(),
                        avkorting = AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
                        tilbakekrevingsbehandling = IkkeBehovForTilbakekrevingUnderBehandling,
                        sakinfo = revurdering.sakinfo,
                        brevvalgRevurdering = revurdering.brevvalgRevurdering,
                    ),
                ).first

            val revurderingAvRevurdering = testDataHelper.persisterRevurderingOpprettet(sakOgVedtak = sak2 to revurderingVedtak) { (s, v) ->
                opprettetRevurdering(
                    sakOgVedtakSomKanRevurderes = s to v,
                    grunnlagsdataOverrides = if (epsFnrRevurderingAvRevurdering == null) listOf(bosituasjongrunnlagEnslig()) else listOf(bosituasjonEpsUnder67(fnr = epsFnrRevurderingAvRevurdering)),

                )
            }
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
        val repo: PersonRepo,
        val innvilgetSøknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget,
        val utbetaling: Utbetaling.OversendtUtbetaling.MedKvittering,
        val revurdering: Revurdering,
    )

    private data class VedtakCtx(
        val dataSource: DataSource,
        val repo: PersonRepo,
        val søknadsbehandlingVedtak: VedtakSomKanRevurderes,
        val revurderingVedtak: VedtakSomKanRevurderes,
    )
}
