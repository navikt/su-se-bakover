package no.nav.su.se.bakover.web.regulering

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.server.testing.ApplicationTestBuilder
import no.nav.su.se.bakover.client.pesys.AlderBeregningsperiode
import no.nav.su.se.bakover.client.pesys.AlderBeregningsperioderPerPerson
import no.nav.su.se.bakover.client.pesys.PesysClient
import no.nav.su.se.bakover.client.pesys.PesysclientStub
import no.nav.su.se.bakover.client.pesys.UføreBeregningsperiode
import no.nav.su.se.bakover.client.pesys.UføreBeregningsperioderPerPerson
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøring
import no.nav.su.se.bakover.domain.regulering.Reguleringsresultat
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.komponenttest.AppComponents
import no.nav.su.se.bakover.web.regulering.ReguleringGrunnbeløpIT.Companion.GRUNNBELØP_2024
import no.nav.su.se.bakover.web.regulering.ReguleringGrunnbeløpIT.Companion.GRUNNBELØP_2025
import no.nav.su.se.bakover.web.regulering.ReguleringGrunnbeløpIT.Companion.REGULERINGSÅR
import no.nav.su.se.bakover.web.routes.regulering.json.ÅrsakTilManuellReguleringJson
import no.nav.su.se.bakover.web.sak.hent.hentSakRequest
import no.nav.su.se.bakover.web.søknadsbehandling.fradrag.leggTilFradrag
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
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

        // Alle testsaker som trenger beløp fra pesys må legges til her
        val pesysStub = PesysclientStub.build(
            uførePeriode = TestScenarietSaker.alle.filter { it.sakstype == Sakstype.UFØRE && it.innvilgetIPesys }
                .map { it.uførePerioderFraPesys() },
            alderPerioder = TestScenarietSaker.alle.filter { it.sakstype == Sakstype.ALDER && it.innvilgetIPesys }
                .map { it.alderPerioderFraPesys() },
        )

        @Test
        fun `full reguleringsjobb`() {
            withMigratedDb { dataSource ->
                applikasjonFørNyttGrunnbeløp(dataSource) { appComponents ->
                    TestScenarietSaker.AUTOMATISK_UFØRE.opprettSak(client, appComponents)
                    TestScenarietSaker.AUTOMATISK_ALDER.opprettSak(client, appComponents)
                    TestScenarietSaker.MANUELL_UFØRE.opprettSak(client, appComponents)
                    TestScenarietSaker.MÅ_REVURDERES_UFØRE.opprettSak(client, appComponents)
                }
                applikasjonEtterNyttGrunnbeløp(dataSource, pesysStub) {
                    regulerAutomatisk(mai(REGULERINGSÅR), this.client)

                    TestScenarietSaker.AUTOMATISK_UFØRE.verifiserAutomatisk(this.client)

                    TestScenarietSaker.AUTOMATISK_ALDER.verifiserAutomatisk(this.client)

                    TestScenarietSaker.MANUELL_UFØRE.verifiserManuell(client)

                    TestScenarietSaker.MÅ_REVURDERES_UFØRE.verifiserMåRevurderes(client)

                    hentReguleringKjøringRequest(client).single().verifiserFullReguleringskjøring()
                }
            }
        }

        private fun TestSakReguleringIT.opprettSak(client: HttpClient, appComponents: AppComponents) {
            opprettInnvilgetSøknadsbehandling(
                fnr = fnr.toString(),
                sakstype = sakstype,
                fraOgMed = januar(REGULERINGSÅR).fraOgMed.toString(),
                tilOgMed = desember(REGULERINGSÅR).tilOgMed.toString(),
                fradrag = { sakId, behandlingId ->
                    leggTilFradrag(
                        sakId = sakId,
                        behandlingId = behandlingId,
                        fraOgMed = januar(REGULERINGSÅR).fraOgMed.toString(),
                        tilOgMed = desember(REGULERINGSÅR).tilOgMed.toString(),
                        fradragstyper = fradrag,
                        client = client,
                    )
                },
                client = client,
                appComponents = appComponents,
            )
        }

        private fun TestSakReguleringIT.verifiserAutomatisk(client: HttpClient) {
            val sakJson = hentSakRequest(fnr, sakstype, client)
            sakJson.reguleringer.size shouldBe 1
            with(sakJson.reguleringer.single()) {
                reguleringstype shouldBe Reguleringstype.AUTOMATISK.toString()
                val regulertBeregning = beregning!!.månedsberegninger.single {
                    it.fraOgMed == MAI_STRENG
                }

                val søknadsbehandling = sakJson.behandlinger.single()
                val beregningFørRegulering = søknadsbehandling.beregning!!.månedsberegninger.single {
                    it.fraOgMed == MAI_STRENG
                }

                regulertBeregning.beløp shouldBeGreaterThan beregningFørRegulering.beløp
            }
        }

        private fun TestSakReguleringIT.verifiserManuell(client: HttpClient) {
            val sakJson = hentSakRequest(fnr, sakstype, client)
            sakJson.reguleringer.size shouldBe 1
            with(sakJson.reguleringer[0]) {
                reguleringstype shouldBe "MANUELL"
                beregning shouldBe null
                årsakForManuell.size shouldBe 1
                (årsakForManuell.single() as ÅrsakTilManuellReguleringJson.ManglerRegulertBeløpForFradrag).let {
                    fradrag shouldContain Fradragstype.Kategori.valueOf(it.fradragskategori)
                    it.fradragTilhører shouldBe FradragTilhører.BRUKER.name
                }
            }
        }

        private fun TestSakReguleringIT.verifiserMåRevurderes(client: HttpClient) {
            val sakJson = hentSakRequest(fnr, sakstype, client)
            sakJson.reguleringer.size shouldBe 0
        }

        // TODO scenariet allerede åpen regulering
        // TODO scenariet allerede regulert
        // TODO scenariet ikke løpende

        // TODO scenariet forventet feil
        // TODO scenariet uventet feil

        private fun ReguleringKjøring.verifiserFullReguleringskjøring() {
            sakerAntall shouldBe 4

            with(reguleringerAutomatisk) {
                size shouldBe 2
                forEach {
                    deserialize<Reguleringsresultat>(it)
                        .utfall shouldBe Reguleringsresultat.Utfall.AUTOMATISK
                }
            }

            with(reguleringerManuell) {
                size shouldBe 1
                forEach {
                    val resultat = deserialize<Reguleringsresultat>(it)
                    resultat.utfall shouldBe Reguleringsresultat.Utfall.MANUELL
                    resultat.beskrivelse shouldBe "ManglerRegulertBeløpForFradrag"
                }
            }

            with(sakerMåRevurderes) {
                size shouldBe 1
                forEach {
                    val resultat = deserialize<Reguleringsresultat>(it)
                    // TODO resultat.saksnummer shouldBe saksnummer..
                    resultat.utfall shouldBe Reguleringsresultat.Utfall.MÅ_REVURDERE
                    resultat.beskrivelse shouldBe "DIFFERANSE_MED_EKSTERNE_BELØP"
                }
            }
        }
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

    val AUTOMATISK_UFØRE = TestSakReguleringIT.create(
        fnr = Fnr("00000000001"),
        sakstype = Sakstype.UFØRE,
        fradrag = listOf(Fradragstype.Kategori.Uføretrygd),
    )

    val AUTOMATISK_ALDER = TestSakReguleringIT.create(
        fnr = Fnr("00000000002"),
        sakstype = Sakstype.ALDER,
        fradrag = listOf(Fradragstype.Kategori.Alderspensjon),
    )

    val MANUELL_UFØRE = TestSakReguleringIT.create(
        fnr = Fnr("00000000003"),
        sakstype = Sakstype.UFØRE,
        fradrag = listOf(Fradragstype.Kategori.Fosterhjemsgodtgjørelse),
    )

    val MÅ_REVURDERES_UFØRE = TestSakReguleringIT.create(
        fnr = Fnr("00000000004"),
        sakstype = Sakstype.UFØRE,
        fradrag = listOf(Fradragstype.Kategori.Uføretrygd),
        diffMellomSuOgPesys = true,
    )

    // TODO automatisk uten innvilget i Pesys??

    val alle = listOf(AUTOMATISK_UFØRE, AUTOMATISK_ALDER, MANUELL_UFØRE, MÅ_REVURDERES_UFØRE)
}

data class TestSakReguleringIT(
    val fnr: Fnr,
    val sakstype: Sakstype,

    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val tilOgMedFørRegulering: LocalDate,
    val fraOgMedEtterRegulering: LocalDate,

    val fradrag: List<Fradragstype.Kategori>,

    val innvilgetIPesys: Boolean,
    val diffMellomSuOgPesys: Boolean,
) {

    fun uførePerioderFraPesys(): UføreBeregningsperioderPerPerson = UføreBeregningsperioderPerPerson(
        fnr = fnr.toString(),
        perioder = listOf(
            UføreBeregningsperiode(
                netto = if (diffMellomSuOgPesys) 10100 else 10000,
                fom = fraOgMed,
                tom = tilOgMedFørRegulering,
                grunnbelop = GRUNNBELØP_2024,
                oppjustertInntektEtterUfore = 0,
            ),
            UføreBeregningsperiode(
                netto = 10250,
                fom = fraOgMedEtterRegulering,
                tom = null,
                grunnbelop = GRUNNBELØP_2025,
                oppjustertInntektEtterUfore = 0,
            ),
        ),
    )

    fun alderPerioderFraPesys(): AlderBeregningsperioderPerPerson = AlderBeregningsperioderPerPerson(
        fnr = fnr.toString(),
        perioder = listOf(
            AlderBeregningsperiode(
                netto = 10000,
                fom = fraOgMed,
                tom = tilOgMedFørRegulering,
                grunnbelop = GRUNNBELØP_2024,
            ),
            AlderBeregningsperiode(
                netto = 10250,
                fom = fraOgMedEtterRegulering,
                tom = null,
                grunnbelop = GRUNNBELØP_2025,
            ),
        ),
    )

    companion object {
        fun create(
            fnr: Fnr,
            sakstype: Sakstype,
            fraOgMed: LocalDate = januar(REGULERINGSÅR).fraOgMed,
            tilOgMed: LocalDate = desember(REGULERINGSÅR).tilOgMed,
            tilOgMedFørRegulering: LocalDate = april(REGULERINGSÅR).tilOgMed,
            fraOgMedEtterRegulering: LocalDate = januar(REGULERINGSÅR).fraOgMed,
            fradrag: List<Fradragstype.Kategori>,
            innvilgetIPesys: Boolean = true,
            diffMellomSuOgPesys: Boolean = false,
        ): TestSakReguleringIT {
            return TestSakReguleringIT(
                fnr = fnr,
                sakstype = sakstype,
                fraOgMed = fraOgMed,
                tilOgMed = tilOgMed,
                tilOgMedFørRegulering = tilOgMedFørRegulering,
                fraOgMedEtterRegulering = fraOgMedEtterRegulering,
                fradrag = fradrag,
                innvilgetIPesys = innvilgetIPesys,
                diffMellomSuOgPesys = diffMellomSuOgPesys,
            )
        }
    }
}
