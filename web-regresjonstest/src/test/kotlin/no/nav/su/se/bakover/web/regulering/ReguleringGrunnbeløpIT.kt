package no.nav.su.se.bakover.web.regulering

import common.presentation.beregning.FradragRequestJson
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.HttpClient
import io.ktor.server.testing.ApplicationTestBuilder
import no.nav.su.se.bakover.client.pesys.AlderBeregningsperiode
import no.nav.su.se.bakover.client.pesys.AlderBeregningsperioderPerPerson
import no.nav.su.se.bakover.client.pesys.PesysClient
import no.nav.su.se.bakover.client.pesys.PesysclientStub
import no.nav.su.se.bakover.client.pesys.UføreBeregningsperiode
import no.nav.su.se.bakover.client.pesys.UføreBeregningsperioderPerPerson
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøring
import no.nav.su.se.bakover.domain.regulering.Reguleringsresultat
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.regulering.ÅrsakTilManuellReguleringKategori
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.komponenttest.AppComponents
import no.nav.su.se.bakover.web.regulering.ReguleringGrunnbeløpIT.Companion.GRUNNBELØP_2024
import no.nav.su.se.bakover.web.regulering.ReguleringGrunnbeløpIT.Companion.GRUNNBELØP_2025
import no.nav.su.se.bakover.web.regulering.ReguleringGrunnbeløpIT.Companion.REGULERINGSÅR
import no.nav.su.se.bakover.web.regulering.TestScenarietSaker.ALDER_MED_EPS_MED_SU
import no.nav.su.se.bakover.web.regulering.TestScenarietSaker.AUTOMATISK_ALDER
import no.nav.su.se.bakover.web.regulering.TestScenarietSaker.AUTOMATISK_UFØRE
import no.nav.su.se.bakover.web.regulering.TestScenarietSaker.AUTOMATISK_UFØRE_MED_IEU
import no.nav.su.se.bakover.web.regulering.TestScenarietSaker.MANUELL_UFØRE
import no.nav.su.se.bakover.web.regulering.TestScenarietSaker.MANUELL_UFØRE_MED_IEU
import no.nav.su.se.bakover.web.regulering.TestScenarietSaker.MÅ_REVURDERES_UFØRE
import no.nav.su.se.bakover.web.regulering.TestScenarietSaker.REVURDERING_UFØRE_MED_IEU
import no.nav.su.se.bakover.web.routes.regulering.json.ÅrsakTilManuellReguleringJson
import no.nav.su.se.bakover.web.sak.hent.hentSakRequest
import no.nav.su.se.bakover.web.søknadsbehandling.bosituasjon.leggTilBosituasjon
import no.nav.su.se.bakover.web.søknadsbehandling.formue.leggTilFormue
import no.nav.su.se.bakover.web.søknadsbehandling.fradrag.leggTilFradrag
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.web.søknadsbehandling.uførhet.leggTilUføregrunnlag
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
                    AUTOMATISK_UFØRE.opprettSak(client, appComponents)
                    AUTOMATISK_ALDER.opprettSak(client, appComponents)
                    MANUELL_UFØRE.opprettSak(client, appComponents)
                    MÅ_REVURDERES_UFØRE.opprettSak(client, appComponents)
                    AUTOMATISK_UFØRE_MED_IEU.opprettSak(client, appComponents)
                    MANUELL_UFØRE_MED_IEU.opprettSak(client, appComponents)
                    REVURDERING_UFØRE_MED_IEU.opprettSak(client, appComponents)
                    ALDER_MED_EPS_MED_SU.opprettSak(client, appComponents)
                }
                applikasjonEtterNyttGrunnbeløp(dataSource, pesysStub) {
                    regulerAutomatisk(mai(REGULERINGSÅR), this.client)

                    AUTOMATISK_UFØRE.verifiserAutomatisk(this.client)

                    AUTOMATISK_ALDER.verifiserAutomatisk(this.client)

                    MANUELL_UFØRE.verifiserManuell(
                        ÅrsakTilManuellReguleringKategori.ManglerRegulertBeløpForFradrag,
                        client,
                    )

                    MÅ_REVURDERES_UFØRE.verifiserMåRevurderes(client)

                    AUTOMATISK_UFØRE_MED_IEU.verifiserAutomatisk(client)
                    MANUELL_UFØRE_MED_IEU.verifiserManuell(ÅrsakTilManuellReguleringKategori.ManglerIeuFraPesys, client)
                    REVURDERING_UFØRE_MED_IEU.verifiserMåRevurderes(client)

                    ALDER_MED_EPS_MED_SU.verifiserManuell(ÅrsakTilManuellReguleringKategori.ManglerRegulertBeløpForFradrag, client)

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
                leggTilUføregrunnlag = { sakId, behandlingId ->
                    leggTilUføregrunnlag(
                        sakId = sakId,
                        behandlingId = behandlingId,
                        fraOgMed = januar(REGULERINGSÅR).fraOgMed.toString(),
                        tilOgMed = desember(REGULERINGSÅR).tilOgMed.toString(),
                        uføregrad = if (gradertUføretrygd) 50 else 100,
                        forventetInntekt = if (gradertUføretrygd) 1000 else 0,
                        client = client,
                    )
                },
                fradrag = { sakId, behandlingId ->
                    leggTilFradrag(
                        sakId = sakId,
                        behandlingId = behandlingId,
                        fraOgMed = januar(REGULERINGSÅR).fraOgMed.toString(),
                        tilOgMed = desember(REGULERINGSÅR).tilOgMed.toString(),
                        fradrag = fradrag,
                        client = client,
                    )
                },
                leggTilBosituasjon = { sakId, behandlingId ->
                    leggTilBosituasjon(
                        sakId = sakId,
                        behandlingId = behandlingId,
                        fraOgMed = januar(REGULERINGSÅR).fraOgMed.toString(),
                        tilOgMed = desember(REGULERINGSÅR).tilOgMed.toString(),
                        epsFnr = eps?.fnr?.toString(),
                        delerBolig = if (eps == null) false else null,
                        erEpsFylt67 = eps?.sakstype == Sakstype.ALDER,
                        client = client,
                    )
                },
                leggTilFormue = { sakId, behandlingId ->
                    leggTilFormue(
                        sakId = sakId,
                        behandlingId = behandlingId,
                        fraOgMed = januar(REGULERINGSÅR).fraOgMed.toString(),
                        tilOgMed = desember(REGULERINGSÅR).tilOgMed.toString(),
                        harEps = eps != null,
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

                if (sakstype != Sakstype.ALDER.value) {
                    val gammelForventetIeu =
                        beregningFørRegulering.fradrag.filter { it.type == Fradragstype.Kategori.ForventetInntekt.name }
                    gammelForventetIeu.size shouldBe 1

                    val regulertForventetIeu =
                        regulertBeregning.fradrag.filter { it.type == Fradragstype.Kategori.ForventetInntekt.name }
                    regulertForventetIeu.size shouldBe 1
                    if (gradertUføretrygd) {
                        regulertForventetIeu.single().beløp shouldBeGreaterThan gammelForventetIeu.single().beløp
                    } else {
                        regulertForventetIeu.single().beløp shouldBe gammelForventetIeu.single().beløp
                        regulertForventetIeu.single().beløp shouldBe 0.0
                    }
                }
            }
        }

        private fun TestSakReguleringIT.verifiserManuell(
            verifiserÅrsak: ÅrsakTilManuellReguleringKategori,
            client: HttpClient,
        ) {
            val sakJson = hentSakRequest(fnr, sakstype, client)
            sakJson.reguleringer.size shouldBe 1
            with(sakJson.reguleringer[0]) {
                reguleringstype shouldBe "MANUELL"
                beregning shouldBe null
                årsakForManuell.size shouldBe 1
                if (verifiserÅrsak == ÅrsakTilManuellReguleringKategori.ManglerRegulertBeløpForFradrag) {
                    (årsakForManuell.single() as ÅrsakTilManuellReguleringJson.ManglerRegulertBeløpForFradrag).let { årsakForManuell ->
                        fradrag.map { it.type } shouldContain årsakForManuell.fradragskategori
                        val fradragÅrsak = fradrag.singleOrNull { it.type == årsakForManuell.fradragskategori }
                        fradragÅrsak shouldNotBe null
                        fradragÅrsak?.tilhører shouldBe årsakForManuell.fradragTilhører
                        if (fradragÅrsak!!.type == Fradragstype.Kategori.SupplerendeStønad.name) {
                            årsakForManuell.fradragTilhører shouldBe FradragTilhører.EPS.name
                        } else {
                            årsakForManuell.fradragTilhører shouldBe FradragTilhører.BRUKER.name
                        }
                    }
                }
                if (verifiserÅrsak == ÅrsakTilManuellReguleringKategori.ManglerIeuFraPesys) {
                    årsakForManuell.single() shouldBe ÅrsakTilManuellReguleringJson.ManglerIeuFraPesys
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
            sakerAntall shouldBe 8

            with(reguleringerAutomatisk) {
                size shouldBe 3
                forEach { resultat ->
                    resultat.utfall shouldBe Reguleringsresultat.Utfall.AUTOMATISK
                }
            }

            with(reguleringerManuell) {
                size shouldBe 3
                filter { it.beskrivelse == "ManglerRegulertBeløpForFradrag" && it.utfall == Reguleringsresultat.Utfall.MANUELL }.size shouldBe 2
                filter { it.beskrivelse == "ManglerIeuFraPesys" && it.utfall == Reguleringsresultat.Utfall.MANUELL }.size shouldBe 1
            }

            with(sakerMåRevurderes) {
                size shouldBe 2
                forEach { resultat ->
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
        fradrag = listOf(Fradragstype.Kategori.Uføretrygd to FradragTilhører.BRUKER),
    )

    val AUTOMATISK_ALDER = TestSakReguleringIT.create(
        fnr = Fnr("00000000002"),
        sakstype = Sakstype.ALDER,
        fradrag = listOf(Fradragstype.Kategori.Alderspensjon to FradragTilhører.BRUKER),
    )

    val MANUELL_UFØRE = TestSakReguleringIT.create(
        fnr = Fnr("00000000003"),
        sakstype = Sakstype.UFØRE,
        fradrag = listOf(Fradragstype.Kategori.Fosterhjemsgodtgjørelse to FradragTilhører.BRUKER),
    )

    val MÅ_REVURDERES_UFØRE = TestSakReguleringIT.create(
        fnr = Fnr("00000000004"),
        sakstype = Sakstype.UFØRE,
        fradrag = listOf(Fradragstype.Kategori.Uføretrygd to FradragTilhører.BRUKER),
        diffMellomSuOgPesys = true,
    )

    val AUTOMATISK_UFØRE_MED_IEU = TestSakReguleringIT.create(
        fnr = Fnr("00000000005"),
        sakstype = Sakstype.UFØRE,
        fradrag = listOf(Fradragstype.Kategori.Uføretrygd to FradragTilhører.BRUKER),
        gradertUføretrygd = true,
    )

    val MANUELL_UFØRE_MED_IEU = TestSakReguleringIT.create(
        fnr = Fnr("00000000006"),
        sakstype = Sakstype.UFØRE,
        fradrag = listOf(Fradragstype.Kategori.Uføretrygd to FradragTilhører.BRUKER),
        gradertUføretrygd = true,
        nullIeu = true,
    )

    val REVURDERING_UFØRE_MED_IEU = TestSakReguleringIT.create(
        fnr = Fnr("00000000007"),
        sakstype = Sakstype.UFØRE,
        fradrag = listOf(Fradragstype.Kategori.Uføretrygd to FradragTilhører.BRUKER),
        gradertUføretrygd = true,
        diffMellomSuOgPesys = true,
    )

    val ALDER_MED_EPS_MED_SU = TestSakReguleringIT.create(
        fnr = Fnr("00000000009"),
        sakstype = Sakstype.ALDER,
        fradrag = listOf(
            Fradragstype.Kategori.Alderspensjon to FradragTilhører.BRUKER,
            Fradragstype.Kategori.SupplerendeStønad to FradragTilhører.EPS,
        ),
        eps = AUTOMATISK_ALDER,
    )

    // TODO automatisk uten innvilget i Pesys??

    val alle = listOf(
        AUTOMATISK_UFØRE,
        AUTOMATISK_ALDER,
        MANUELL_UFØRE,
        MÅ_REVURDERES_UFØRE,
        AUTOMATISK_UFØRE_MED_IEU,
        MANUELL_UFØRE_MED_IEU,
        REVURDERING_UFØRE_MED_IEU,
        ALDER_MED_EPS_MED_SU,
    )
}

data class TestSakReguleringIT(
    val fnr: Fnr,
    val sakstype: Sakstype,

    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val tilOgMedFørRegulering: LocalDate,
    val fraOgMedEtterRegulering: LocalDate,

    val fradrag: List<FradragRequestJson>,

    val innvilgetIPesys: Boolean,
    val gradertUføretrygd: Boolean,
    val nullIeu: Boolean,
    val diffMellomSuOgPesys: Boolean,
    val eps: TestSakReguleringIT?,
) {

    fun uførePerioderFraPesys(): UføreBeregningsperioderPerPerson = UføreBeregningsperioderPerPerson(
        fnr = fnr.toString(),
        perioder = listOf(
            UføreBeregningsperiode(
                netto = if (diffMellomSuOgPesys) 10100 else 10000,
                fom = fraOgMed,
                tom = tilOgMedFørRegulering,
                grunnbelop = GRUNNBELØP_2024,
                oppjustertInntektEtterUfore = if (gradertUføretrygd) {
                    if (diffMellomSuOgPesys) {
                        1010
                    } else if (nullIeu) {
                        null
                    } else {
                        1000
                    }
                } else {
                    0
                },
            ),
            UføreBeregningsperiode(
                netto = 10250,
                fom = fraOgMedEtterRegulering,
                tom = null,
                grunnbelop = GRUNNBELØP_2025,
                oppjustertInntektEtterUfore = if (gradertUføretrygd) {
                    if (nullIeu) {
                        null
                    } else {
                        1100
                    }
                } else {
                    0
                },
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
            fradrag: List<Pair<Fradragstype.Kategori, FradragTilhører>> = emptyList(),
            innvilgetIPesys: Boolean = true,
            gradertUføretrygd: Boolean = false,
            nullIeu: Boolean = false,
            diffMellomSuOgPesys: Boolean = false,
            eps: TestSakReguleringIT? = null,
        ): TestSakReguleringIT {
            return TestSakReguleringIT(
                fnr = fnr,
                sakstype = sakstype,
                fraOgMed = fraOgMed,
                tilOgMed = tilOgMed,
                tilOgMedFørRegulering = tilOgMedFørRegulering,
                fraOgMedEtterRegulering = fraOgMedEtterRegulering,
                fradrag = fradrag.map { (type, tilhører) ->
                    FradragRequestJson(
                        periode = PeriodeJson(fraOgMed = fraOgMed.toString(), tilOgMed = tilOgMed.toString()),
                        type = type.name,
                        beskrivelse = null,
                        beløp = when (tilhører) {
                            FradragTilhører.BRUKER -> 10000.0
                            FradragTilhører.EPS -> 1000.0
                        },
                        utenlandskInntekt = null,
                        tilhører = tilhører.name,
                    )
                },
                innvilgetIPesys = innvilgetIPesys,
                gradertUføretrygd = gradertUføretrygd,
                nullIeu = nullIeu,
                diffMellomSuOgPesys = diffMellomSuOgPesys,
                eps = eps,
            )
        }
    }
}
