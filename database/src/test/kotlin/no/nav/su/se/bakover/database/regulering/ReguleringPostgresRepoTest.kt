package no.nav.su.se.bakover.database.regulering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.migratedDb
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.regulering.AutomatiskEllerManuellSak
import no.nav.su.se.bakover.domain.regulering.BehandlingType
import no.nav.su.se.bakover.domain.regulering.VedtakType
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.stønadsperiode2021
import org.junit.jupiter.api.Test
import java.util.UUID

internal class ReguleringPostgresRepoTest {

    @Test
    fun `vedtak hentes og mappes med riktige referanser til behandlingen`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val søknadsbehandling = testDataHelper.nyIverksattInnvilget().first
            val list = testDataHelper.reguleringRepo.hentVedtakSomKanReguleres(1.mai(2021))

            list.size shouldBe 1
            list.first() shouldBe AutomatiskEllerManuellSak(
                sakId = søknadsbehandling.sakId,
                saksnummer = søknadsbehandling.saksnummer,
                opprettet = søknadsbehandling.opprettet,
                behandlingId = søknadsbehandling.id,
                fraOgMed = søknadsbehandling.periode.fraOgMed,
                tilOgMed = søknadsbehandling.periode.tilOgMed,
                vedtakType = VedtakType.SØKNAD,
                behandlingType = BehandlingType.AUTOMATISK,
            )
        }
    }

    @Test
    fun `En sak med innvilget periode etter første mai skal vurderes AUTOMATISK`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            testDataHelper.nyIverksattInnvilget()

            val list = testDataHelper.reguleringRepo.hentVedtakSomKanReguleres(1.mai(2021))

            list.size shouldBe 1
            list.first().behandlingType shouldBe BehandlingType.AUTOMATISK
        }
    }

    @Test
    fun `En sak med offentlig pensjon skal gi MANUELL`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            testDataHelper.nyIverksattInnvilget(
                grunnlagsdata = Grunnlagsdata.create(
                    fradragsgrunnlag = listOf(lagFradragsgrunnlag(Fradragstype.OffentligPensjon)),
                    bosituasjon = listOf(bosituasjongrunnlagEnslig(UUID.randomUUID(), stønadsperiode2021.periode))
                ),
            )

            val list = testDataHelper.reguleringRepo.hentVedtakSomKanReguleres(1.mai(2021))

            list.size shouldBe 1
            list.first().behandlingType shouldBe BehandlingType.MANUELL
        }
    }

    @Test
    fun `En sak med NAVytelserTilLivsopphold skal gi MANUELL`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            testDataHelper.nyIverksattInnvilget(
                grunnlagsdata = Grunnlagsdata.create(
                    fradragsgrunnlag = listOf(lagFradragsgrunnlag(Fradragstype.NAVytelserTilLivsopphold)),
                    bosituasjon = listOf(bosituasjongrunnlagEnslig(UUID.randomUUID(), stønadsperiode2021.periode))
                ),
            )

            val list = testDataHelper.reguleringRepo.hentVedtakSomKanReguleres(1.mai(2021))

            list.size shouldBe 1
            list.first().behandlingType shouldBe BehandlingType.MANUELL
        }
    }

    @Test
    fun `En sak med offentlig pensjon innen gitt dato skal gi AUTOMATISK`() {
        val periodeUtenforGittDato = Periode.create(1.januar(2021), 28.februar(2021))

        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            testDataHelper.nyIverksattInnvilget(
                grunnlagsdata = Grunnlagsdata.create(
                    fradragsgrunnlag = listOf(lagFradragsgrunnlag(Fradragstype.OffentligPensjon, periodeUtenforGittDato)),
                    bosituasjon = listOf(bosituasjongrunnlagEnslig(UUID.randomUUID(), stønadsperiode2021.periode))
                ),
            )

            val list = testDataHelper.reguleringRepo.hentVedtakSomKanReguleres(1.mai(2021))

            list.size shouldBe 1
            list.first().behandlingType shouldBe BehandlingType.AUTOMATISK
        }
    }

    @Test
    fun `lik fradragsperiode som behandlingsperioden gir MANUELL`() {
        val testDataHelper = setupTestData(stønadsperiode2021.periode)
        val list = testDataHelper.reguleringRepo.hentVedtakSomKanReguleres(1.mai(2021))

        list.size shouldBe 1
        list.first().behandlingType shouldBe BehandlingType.MANUELL
    }

    @Test
    fun `fradragsperiode som starter samtidig som behandlingen og slutter rett over første mai skal gi MANUELL`() {
        val periodeUtenforGittDato = Periode.create(1.januar(2021), 30.juni(2021))
        val testDataHelper = setupTestData(periodeUtenforGittDato)
        val list = testDataHelper.reguleringRepo.hentVedtakSomKanReguleres(1.mai(2021))

        list.size shouldBe 1
        list.first().behandlingType shouldBe BehandlingType.MANUELL
    }

    @Test
    fun `fradragsperiode som starter samtidig som behandlingen og slutter i mai skal gi MANUELL`() {
        val periodeUtenforGittDato = Periode.create(1.januar(2021), 31.mai(2021))
        val testDataHelper = setupTestData(periodeUtenforGittDato)
        val list = testDataHelper.reguleringRepo.hentVedtakSomKanReguleres(1.mai(2021))

        list.size shouldBe 1
        list.first().behandlingType shouldBe BehandlingType.MANUELL
    }

    @Test
    fun `fradragsperiode som starter samtidig som behandlingen og slutter innen mai skal gi AUTOMATISK`() {
        val periodeUtenforGittDato = Periode.create(1.januar(2021), 30.april(2021))
        val testDataHelper = setupTestData(periodeUtenforGittDato)
        val list = testDataHelper.reguleringRepo.hentVedtakSomKanReguleres(1.mai(2021))

        list.size shouldBe 1
        list.first().behandlingType shouldBe BehandlingType.AUTOMATISK
    }

    @Test
    fun `fradragsperiode som starter første mai og går ut behandlingsperioden skal gi MANUELL`() {
        val periodeUtenforGittDato = Periode.create(1.mai(2021), 31.desember(2021))
        val testDataHelper = setupTestData(periodeUtenforGittDato)
        val list = testDataHelper.reguleringRepo.hentVedtakSomKanReguleres(1.mai(2021))

        list.size shouldBe 1
        list.first().behandlingType shouldBe BehandlingType.MANUELL
    }

    @Test
    fun `fradragsperiode som starter første mai og slutter innen behandlingsperiodens slutt skal gi MANUELL`() {
        val periodeUtenforGittDato = Periode.create(1.mai(2021), 31.juli(2021))
        val testDataHelper = setupTestData(periodeUtenforGittDato)
        val list = testDataHelper.reguleringRepo.hentVedtakSomKanReguleres(1.mai(2021))

        list.size shouldBe 1
        list.first().behandlingType shouldBe BehandlingType.MANUELL
    }

    @Test
    fun `fradragsperiode som starter etter første mai og er innenfor behandlingen skal gi MANUELL`() {
        val periodeUtenforGittDato = Periode.create(1.juni(2021), 31.desember(2021))
        val testDataHelper = setupTestData(periodeUtenforGittDato)
        val list = testDataHelper.reguleringRepo.hentVedtakSomKanReguleres(1.mai(2021))

        list.size shouldBe 1
        list.first().behandlingType shouldBe BehandlingType.MANUELL
    }

    private fun setupTestData(fradragsPeriode: Periode): TestDataHelper {
        val dataSource = migratedDb()
        val testDataHelper = TestDataHelper(dataSource).apply {
            nyIverksattInnvilget(
                grunnlagsdata = Grunnlagsdata.create(
                    fradragsgrunnlag = listOf(lagFradragsgrunnlag(Fradragstype.OffentligPensjon, fradragsPeriode)),
                    bosituasjon = listOf(bosituasjongrunnlagEnslig(UUID.randomUUID(), stønadsperiode2021.periode))
                ),
            )
        }

        return testDataHelper
    }

    private fun lagFradragsgrunnlag(fradragstype: Fradragstype, periode: Periode = stønadsperiode2021.periode): Grunnlag.Fradragsgrunnlag {
        return Grunnlag.Fradragsgrunnlag.create(
            UUID.randomUUID(), Tidspunkt.now(),
            FradragFactory.ny(
                type = fradragstype,
                månedsbeløp = 3000.0,
                periode = periode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER
            )
        )
    }
}
