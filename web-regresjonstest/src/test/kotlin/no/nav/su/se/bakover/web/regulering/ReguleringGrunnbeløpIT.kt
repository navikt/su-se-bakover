package no.nav.su.se.bakover.web.regulering

import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.ApplicationTestBuilder
import no.nav.su.se.bakover.client.pesys.PesysclientStub
import no.nav.su.se.bakover.client.pesys.UføreBeregningsperiode
import no.nav.su.se.bakover.client.pesys.UføreBeregningsperioderPerPerson
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.komponenttest.AppComponents
import no.nav.su.se.bakover.web.regulering.ReguleringGrunnbeløpIT.Companion.REGULERINGSÅR
import no.nav.su.se.bakover.web.routes.sak.SakJson
import no.nav.su.se.bakover.web.sak.hent.hentSakForFnr
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import javax.sql.DataSource

internal class ReguleringGrunnbeløpIT {

    companion object {
        const val REGULERINGSÅR = 2025
        const val MAI_STRENG = "$REGULERINGSÅR-05-01"
        val klokkeFørNyttGrunnbeløp: Clock =
            Clock.fixed(1.januar(REGULERINGSÅR).atTime(1, 2, 3, 456789000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        val klokkeEtterNyttGrunnbeløp: Clock =
            Clock.fixed(25.mai(REGULERINGSÅR).atTime(1, 2, 3, 456789000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    }

    val testSakUføre = TestSakReguleringIT(fnr = Fnr("00000000001"))
    val uføreSakPesys = UføreBeregningsperioderPerPerson(
        fnr = testSakUføre.fnr.toString(),
        perioder = listOf(
            UføreBeregningsperiode(
                netto = 1000,
                fom = testSakUføre.fraOgMed,
                tom = testSakUføre.tilOgMedFørRegulering,
                grunnbelop = 124028,
                oppjustertInntektEtterUfore = 0,
            ),
            UføreBeregningsperiode(
                netto = 1000,
                fom = testSakUføre.fraOgMedEtterRegulering,
                tom = null,
                grunnbelop = 130160,
                oppjustertInntektEtterUfore = 0,
            ),
        ),
    )

    val pesysStub = PesysclientStub.build(
        uførePeriode = listOf(uføreSakPesys),
    )

    @Test
    fun `automatisk regulering`() {
        withMigratedDb { dataSource ->
            applikasjonFørNyttGrunnbeløp(dataSource) { appComponents ->
                opprettInnvilgetSøknadsbehandling(
                    fnr = testSakUføre.fnr.toString(),
                    fraOgMed = januar(REGULERINGSÅR).fraOgMed.toString(),
                    tilOgMed = desember(REGULERINGSÅR).tilOgMed.toString(),
                    client = this.client,
                    appComponents = appComponents,
                )
            }
            applikasjonFørEtterGrunnbeløp(dataSource) {
                regulerAutomatisk(mai(REGULERINGSÅR), this.client)

                val sak = deserialize<SakJson>(hentSakForFnr(testSakUføre.fnr.toString(), client = this.client))
                val reguleringer = sak.reguleringer
                reguleringer.size shouldBe 1
                val søknadsbehandling = sak.behandlinger.single()
                with(reguleringer[0]) {
                    reguleringstype shouldBe Reguleringstype.AUTOMATISK.toString()
                    val regulertBeregning = beregning!!.månedsberegninger.single {
                        it.fraOgMed == MAI_STRENG
                    }
                    val beregningFørRegulering = søknadsbehandling.beregning!!.månedsberegninger.single {
                        it.fraOgMed == MAI_STRENG
                    }
                    regulertBeregning.beløp shouldBeGreaterThan beregningFørRegulering.beløp

                    // TODO verifiser mot reguleringsresultat tabell
                }
            }
        }
    }

    private fun applikasjonFørNyttGrunnbeløp(
        dataSource: DataSource,
        test: ApplicationTestBuilder.(appComponents: AppComponents) -> Unit,
    ) = SharedRegressionTestData.withTestApplication(
        clock = klokkeFørNyttGrunnbeløp,
        dataSource = dataSource,
    ) { test(it) }

    private fun applikasjonFørEtterGrunnbeløp(
        dataSource: DataSource,
        test: ApplicationTestBuilder.(appComponents: AppComponents) -> Unit,
    ) = SharedRegressionTestData.withTestApplication(
        clock = klokkeEtterNyttGrunnbeløp,
        dataSource = dataSource,
        pesysClientStub = pesysStub,
    ) { test(it) }
}

data class TestSakReguleringIT(
    val fnr: Fnr,
    val fraOgMed: LocalDate = januar(REGULERINGSÅR).fraOgMed,
    val tilOgMed: LocalDate = desember(REGULERINGSÅR).tilOgMed,
    val tilOgMedFørRegulering: LocalDate = april(REGULERINGSÅR).tilOgMed,
    val fraOgMedEtterRegulering: LocalDate = januar(REGULERINGSÅR).fraOgMed,
)
