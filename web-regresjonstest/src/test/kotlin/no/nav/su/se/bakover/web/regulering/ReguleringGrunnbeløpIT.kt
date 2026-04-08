package no.nav.su.se.bakover.web.regulering

import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.ApplicationTestBuilder
import no.nav.su.se.bakover.client.pesys.AlderBeregningsperiode
import no.nav.su.se.bakover.client.pesys.AlderBeregningsperioderPerPerson
import no.nav.su.se.bakover.client.pesys.PesysClient
import no.nav.su.se.bakover.client.pesys.PesysPerioderForPerson
import no.nav.su.se.bakover.client.pesys.PesysclientStub
import no.nav.su.se.bakover.client.pesys.UføreBeregningsperiode
import no.nav.su.se.bakover.client.pesys.UføreBeregningsperioderPerPerson
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.test.fnrOver67
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.komponenttest.AppComponents
import no.nav.su.se.bakover.web.regulering.ReguleringGrunnbeløpIT.Companion.GRUNNBELØP_2024
import no.nav.su.se.bakover.web.regulering.ReguleringGrunnbeløpIT.Companion.GRUNNBELØP_2025
import no.nav.su.se.bakover.web.regulering.ReguleringGrunnbeløpIT.Companion.REGULERINGSÅR
import no.nav.su.se.bakover.web.sak.hent.hentSakRequest
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import javax.sql.DataSource

internal class ReguleringGrunnbeløpIT {

    companion object {
        const val REGULERINGSÅR = 2025
        const val MAI_STRENG = "$REGULERINGSÅR-05-01"

        const val GRUNNBELØP_2024 = 124028
        const val GRUNNBELØP_2025 = 130160

        val klokkeFørNyttGrunnbeløp: Clock =
            Clock.fixed(1.januar(REGULERINGSÅR).atTime(1, 2, 3, 456789000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        val klokkeEtterNyttGrunnbeløp: Clock =
            Clock.fixed(25.mai(REGULERINGSÅR).atTime(1, 2, 3, 456789000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    }

    @Nested
    inner class `Full reguleringsjobb` {
        val automtatiskUføreSak = TestScenarietSaker.automatiskUføreSak(Fnr("00000000001"))
        val automatiskAlderSak = TestScenarietSaker.automatiskAlderSak(fnrOver67)

        // Alle testsaker som trenger beløp fra pesys må legges til her
        val pesysStub = PesysclientStub.build(
            uførePeriode = listOf(automtatiskUføreSak.uførePerioderFraPesys()),
            alderPerioder = listOf(automatiskAlderSak.alderPerioderFraPesys()),
        )

        @Test
        fun `full reguleringsjobb`() {
            withMigratedDb { dataSource ->
                applikasjonFørNyttGrunnbeløp(dataSource) { appComponents ->
                    opprettInnvilgetSøknadsbehandling(
                        fnr = automtatiskUføreSak.fnr.toString(),
                        sakstype = Sakstype.UFØRE,
                        fraOgMed = januar(REGULERINGSÅR).fraOgMed.toString(),
                        tilOgMed = desember(REGULERINGSÅR).tilOgMed.toString(),
                        client = this.client,
                        appComponents = appComponents,
                    )
                    // TODO feiler fordi personoppsalg stuber person under 67 år.. testoppbyggingen må også stube personoppslag...
                    opprettInnvilgetSøknadsbehandling(
                        fnr = automatiskAlderSak.fnr.toString(),
                        sakstype = Sakstype.ALDER,
                        fraOgMed = januar(REGULERINGSÅR).fraOgMed.toString(),
                        tilOgMed = desember(REGULERINGSÅR).tilOgMed.toString(),
                        client = this.client,
                        appComponents = appComponents,
                    )
                }
                applikasjonEtterNyttGrunnbeløp(dataSource, pesysStub) {
                    regulerAutomatisk(mai(REGULERINGSÅR), this.client)

                    val automatiskSakUføre =
                        hentSakRequest(automtatiskUføreSak.fnr, automtatiskUføreSak.sakstype, client)
                    with(automatiskSakUføre) {
                        reguleringer.size shouldBe 1
                        with(reguleringer[0]) {
                            reguleringstype shouldBe Reguleringstype.AUTOMATISK.toString()
                            val regulertBeregning = beregning!!.månedsberegninger.single {
                                it.fraOgMed == MAI_STRENG
                            }

                            val søknadsbehandling = behandlinger.single()
                            val beregningFørRegulering = søknadsbehandling.beregning!!.månedsberegninger.single {
                                it.fraOgMed == MAI_STRENG
                            }

                            regulertBeregning.beløp shouldBeGreaterThan beregningFørRegulering.beløp
                        }
                    }

                    val automatiskSakAlder = hentSakRequest(automatiskAlderSak.fnr, automatiskAlderSak.sakstype, client)
                    with(automatiskSakAlder) {
                        reguleringer.size shouldBe 1
                        with(reguleringer[0]) {
                            reguleringstype shouldBe Reguleringstype.AUTOMATISK.toString()
                            val regulertBeregning = beregning!!.månedsberegninger.single {
                                it.fraOgMed == MAI_STRENG
                            }

                            val søknadsbehandling = behandlinger.single()
                            val beregningFørRegulering = søknadsbehandling.beregning!!.månedsberegninger.single {
                                it.fraOgMed == MAI_STRENG
                            }

                            regulertBeregning.beløp shouldBeGreaterThan beregningFørRegulering.beløp
                        }
                    }

                    val kjøringer = hentReguleringKjøringRequest(client)
                    kjøringer.size shouldBe 1
                    val kjøring = kjøringer.single()
                    kjøring.sakerAntall shouldBe 2
                    kjøring.reguleringerAutomatisk.size shouldBe 2
                }
            }
        }
        // TODO en test som kjører reguleringsjobb med flere utfall samtidig
        // TODO scenariet 1 automatisk - uføre CHECK
        // TODO scenariet 1 automatisk - alder CHECK
        // TODO scenariet 2 manuell forventet - Har fradrag som
        // TODO scenariet 2 manuell forventet - en annen varient? alder?
        // TODO scenariet 3 Fører til revurdering

        // TODO scenariet 3 forventet feil
        // TODO scenariet 4 uventet feil
        // TODO Verifier hver enkelt overordnet
        // TODO Verifiser reguleringsjobb resultat mer detaljert
    }

    @Nested
    inner class `Enkelt scenariet` {
        // TODO egne scope med tester for alle scenarier vi ønsker å teste
    }

    private fun applikasjonFørNyttGrunnbeløp(
        dataSource: DataSource,
        test: ApplicationTestBuilder.(appComponents: AppComponents) -> Unit,
    ) = SharedRegressionTestData.withTestApplication(
        clock = klokkeFørNyttGrunnbeløp,
        dataSource = dataSource,
    ) { test(it) }

    private fun applikasjonEtterNyttGrunnbeløp(
        dataSource: DataSource,
        pesysStub: PesysClient,
        test: ApplicationTestBuilder.(appComponents: AppComponents) -> Unit,
    ) = SharedRegressionTestData.withTestApplication(
        clock = klokkeEtterNyttGrunnbeløp,
        dataSource = dataSource,
        pesysClientStub = pesysStub,
    ) { test(it) }
}

// TODO egen fil
object TestScenarietSaker {

    fun automatiskUføreSak(fnr: Fnr): TestSakReguleringIT {
        return TestSakReguleringIT.create(
            fnr = fnr,
            sakstype = Sakstype.UFØRE,
            finnesIPesys = true,
        )
    }

    fun automatiskAlderSak(fnr: Fnr): TestSakReguleringIT {
        return TestSakReguleringIT.create(
            fnr = fnr,
            sakstype = Sakstype.ALDER,
            finnesIPesys = true,
        )
    }
}

data class TestSakReguleringIT(
    val fnr: Fnr,
    val sakstype: Sakstype,

    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val tilOgMedFørRegulering: LocalDate,
    val fraOgMedEtterRegulering: LocalDate,

    val perioderFraPesys: PesysPerioderForPerson,
) {

    fun uførePerioderFraPesys(): UføreBeregningsperioderPerPerson = perioderFraPesys as UføreBeregningsperioderPerPerson
    fun alderPerioderFraPesys(): AlderBeregningsperioderPerPerson = perioderFraPesys as AlderBeregningsperioderPerPerson

    companion object {
        fun create(
            fnr: Fnr,
            sakstype: Sakstype,
            fraOgMed: LocalDate = januar(REGULERINGSÅR).fraOgMed,
            tilOgMed: LocalDate = desember(REGULERINGSÅR).tilOgMed,
            tilOgMedFørRegulering: LocalDate = april(REGULERINGSÅR).tilOgMed,
            fraOgMedEtterRegulering: LocalDate = januar(REGULERINGSÅR).fraOgMed,
            finnesIPesys: Boolean = false,
        ): TestSakReguleringIT {
            val perioderFraPesys = if (finnesIPesys) {
                when (sakstype) {
                    Sakstype.ALDER -> AlderBeregningsperioderPerPerson(
                        fnr = fnr.toString(),
                        perioder = listOf(
                            AlderBeregningsperiode(
                                netto = 1000,
                                fom = fraOgMed,
                                tom = tilOgMedFørRegulering,
                                grunnbelop = GRUNNBELØP_2024,
                            ),
                            AlderBeregningsperiode(
                                netto = 1200,
                                fom = fraOgMedEtterRegulering,
                                tom = null,
                                grunnbelop = GRUNNBELØP_2025,
                            ),
                        ),
                    )

                    Sakstype.UFØRE,
                    -> UføreBeregningsperioderPerPerson(
                        fnr = fnr.toString(),
                        perioder = listOf(
                            UføreBeregningsperiode(
                                netto = 1000,
                                fom = fraOgMed,
                                tom = tilOgMedFørRegulering,
                                grunnbelop = GRUNNBELØP_2024,
                                oppjustertInntektEtterUfore = 0,
                            ),
                            UføreBeregningsperiode(
                                netto = 1200,
                                fom = fraOgMedEtterRegulering,
                                tom = null,
                                grunnbelop = GRUNNBELØP_2025,
                                oppjustertInntektEtterUfore = 0,
                            ),
                        ),
                    )
                }
            } else {
                null
            }

            return TestSakReguleringIT(
                fnr = fnr,
                sakstype = sakstype,
                fraOgMed = fraOgMed,
                tilOgMed = tilOgMed,
                tilOgMedFørRegulering = tilOgMedFørRegulering,
                fraOgMedEtterRegulering = fraOgMedEtterRegulering,
                perioderFraPesys = perioderFraPesys ?: UføreBeregningsperioderPerPerson(fnr.toString(), emptyList()),
            )
        }
    }
}
