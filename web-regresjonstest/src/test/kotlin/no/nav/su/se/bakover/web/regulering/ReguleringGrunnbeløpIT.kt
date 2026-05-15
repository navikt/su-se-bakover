package no.nav.su.se.bakover.web.regulering

import common.presentation.beregning.FradragRequestJson
import common.presentation.beregning.UtenlandskInntektJson
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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
import no.nav.su.se.bakover.common.tid.periode.juli
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.domain.regulering.FeilMedEksternRegulering
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøring
import no.nav.su.se.bakover.domain.regulering.Reguleringsresultat
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.regulering.ÅrsakTilManuellReguleringKategori
import no.nav.su.se.bakover.domain.revurdering.steg.Revurderingsteg
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.komponenttest.AppComponents
import no.nav.su.se.bakover.web.regulering.ReguleringGrunnbeløpIT.Companion.GRUNNBELØP_2024
import no.nav.su.se.bakover.web.regulering.ReguleringGrunnbeløpIT.Companion.GRUNNBELØP_2025
import no.nav.su.se.bakover.web.regulering.ReguleringGrunnbeløpIT.Companion.REGULERINGSÅR
import no.nav.su.se.bakover.web.regulering.TestScenarietSaker.ALDERPENSJON_UTLAND
import no.nav.su.se.bakover.web.regulering.TestScenarietSaker.ALDER_MED_EPS_MED_SU
import no.nav.su.se.bakover.web.regulering.TestScenarietSaker.AUTOMATISK_ALDER
import no.nav.su.se.bakover.web.regulering.TestScenarietSaker.AUTOMATISK_UFØRE
import no.nav.su.se.bakover.web.regulering.TestScenarietSaker.AUTOMATISK_UFØRE_MED_IEU
import no.nav.su.se.bakover.web.regulering.TestScenarietSaker.INNVILGET_SØKNAD_ETTER_NY_G
import no.nav.su.se.bakover.web.regulering.TestScenarietSaker.MANUELL_UFØRE
import no.nav.su.se.bakover.web.regulering.TestScenarietSaker.MANUELL_UFØRE_MED_IEU
import no.nav.su.se.bakover.web.regulering.TestScenarietSaker.MÅ_REVURDERES_UFØRE
import no.nav.su.se.bakover.web.regulering.TestScenarietSaker.OVER_10_PRORSENT_MED_G_FRADRAG
import no.nav.su.se.bakover.web.regulering.TestScenarietSaker.OVER_10_PRORSENT_UTEN_G_FRADRAG
import no.nav.su.se.bakover.web.regulering.TestScenarietSaker.REVURDERING_UFØRE_MED_IEU
import no.nav.su.se.bakover.web.regulering.TestScenarietSaker.UFØRE_FINNES_IKKE_PESYS
import no.nav.su.se.bakover.web.regulering.TestScenarietSaker.UFØRE_IKKE_REGULERT_PESYS
import no.nav.su.se.bakover.web.regulering.TestScenarietSaker.UFØRE_I_SENERE_PERIODE
import no.nav.su.se.bakover.web.regulering.TestScenarietSaker.tilManuell
import no.nav.su.se.bakover.web.revurdering.opprettIverksattRevurdering
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
import kotlin.collections.map

internal class ReguleringGrunnbeløpIT {

    companion object {
        const val REGULERINGSÅR = 2025
        const val MAI_STRENG = "$REGULERINGSÅR-05-01"

        const val GRUNNBELØP_2024 = 124028
        const val GRUNNBELØP_2025 = 130160

        val klokkeFørNyttGrunnbeløp: Clock =
            TikkendeKlokke(
                Clock.fixed(
                    1.januar(REGULERINGSÅR).atTime(1, 2, 3, 456789000).toInstant(ZoneOffset.UTC),
                    ZoneOffset.UTC,
                ),
            )
        val klokkeEtterNyttGrunnbeløp: Clock =
            TikkendeKlokke(
                Clock.fixed(
                    25.mai(REGULERINGSÅR).atTime(1, 2, 3, 456789000).toInstant(ZoneOffset.UTC),
                    ZoneOffset.UTC,
                ),
            )
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
                    UFØRE_FINNES_IKKE_PESYS.opprettSak(client, appComponents)
                    UFØRE_IKKE_REGULERT_PESYS.opprettSak(client, appComponents)
                    UFØRE_I_SENERE_PERIODE.opprettSak(client, appComponents).also {
                        UFØRE_I_SENERE_PERIODE.revurder(
                            client,
                            appComponents,
                            tilOgMed = juli(REGULERINGSÅR).tilOgMed,
                            fradrag = emptyList(),
                        )
                    }
                    ALDERPENSJON_UTLAND.opprettSak(client, appComponents)
                    OVER_10_PRORSENT_UTEN_G_FRADRAG.opprettSak(client, appComponents)
                    OVER_10_PRORSENT_MED_G_FRADRAG.opprettSak(client, appComponents)
                }
                applikasjonEtterNyttGrunnbeløp(dataSource, pesysStub) {
                    INNVILGET_SØKNAD_ETTER_NY_G.opprettSak(client, it)

                    verifiserReguleringstatusFørKjøring(client)

                    regulerAutomatisk(mai(REGULERINGSÅR), this.client)

                    AUTOMATISK_UFØRE.verifiserAutomatisk(this.client)

                    AUTOMATISK_ALDER.verifiserAutomatisk(this.client)

                    MANUELL_UFØRE.verifiserManuell(
                        ÅrsakTilManuellReguleringKategori.ManglerRegulertBeløpForFradrag,
                        client,
                    )

                    MÅ_REVURDERES_UFØRE.verifiserBleIkkeRegulert(client)

                    AUTOMATISK_UFØRE_MED_IEU.verifiserAutomatisk(client)
                    MANUELL_UFØRE_MED_IEU.verifiserManuell(ÅrsakTilManuellReguleringKategori.ManglerIeuFraPesys, client)
                    REVURDERING_UFØRE_MED_IEU.verifiserBleIkkeRegulert(client)

                    ALDER_MED_EPS_MED_SU.verifiserAutomatisk(client)

                    UFØRE_FINNES_IKKE_PESYS.verifiserBleIkkeRegulert(client)

                    UFØRE_IKKE_REGULERT_PESYS.verifiserBleIkkeRegulert(client)

                    UFØRE_I_SENERE_PERIODE.verifiserManuell(
                        ÅrsakTilManuellReguleringKategori.EtAutomatiskFradragHarFremtidigPeriode,
                        client,
                    )

                    ALDERPENSJON_UTLAND.verifiserAutomatisk(client)

                    OVER_10_PRORSENT_UTEN_G_FRADRAG.verifiserAutomatisk(client)
                    OVER_10_PRORSENT_MED_G_FRADRAG.verifiserBleIkkeRegulert(client)

                    INNVILGET_SØKNAD_ETTER_NY_G.verifiserBleIkkeRegulert(client)

                    hentReguleringKjøringRequest(client).single().verifiserFullReguleringskjøring()

                    regulerAutomatisk(mai(REGULERINGSÅR), this.client)
                    hentReguleringKjøringRequest(client).last().verifiserRekjøringAvRegulering()

                    verifiserReguleringstatusEtterKjøring(client)
                }
            }
        }

        private fun verifiserReguleringstatusFørKjøring(client: HttpClient) {
            with(hentReguleringStatusRequest(client, REGULERINGSÅR)) {
                aar shouldBe REGULERINGSÅR
                sakerMedUtebetalingIMai shouldBe TestScenarietSaker.alle.size
                sisteGrunnbeløpOgSatser.grunnbeløp shouldBe 130160
                sisteGrunnbeløpOgSatser.garantipensjonOrdinær shouldBe 224248
                sisteGrunnbeløpOgSatser.garantipensjonHøy shouldBe 242418
                sakerMedGammelG.size shouldBe (TestScenarietSaker.alle.size - TestScenarietSaker.alleredeRegulert.size)
            }
        }

        private fun verifiserReguleringstatusEtterKjøring(client: HttpClient) {
            with(hentReguleringStatusRequest(client, REGULERINGSÅR)) {
                aar shouldBe REGULERINGSÅR
                sakerMedUtebetalingIMai shouldBe TestScenarietSaker.alle.size
                sisteGrunnbeløpOgSatser.grunnbeløp shouldBe 130160
                sisteGrunnbeløpOgSatser.garantipensjonOrdinær shouldBe 224248
                sisteGrunnbeløpOgSatser.garantipensjonHøy shouldBe 242418
                sakerMedGammelG.size shouldBe (
                    TestScenarietSaker.alle.size -
                        TestScenarietSaker.alleredeRegulert.size -
                        TestScenarietSaker.tilAutomatisk.size
                    )
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

        private fun TestSakReguleringIT.revurder(
            client: HttpClient,
            appComponents: AppComponents,
            fraOgMed: LocalDate = januar(REGULERINGSÅR).fraOgMed,
            tilOgMed: LocalDate = desember(REGULERINGSÅR).tilOgMed,
            fradrag: List<FradragRequestJson> = this.fradrag,
        ) {
            val sakJson = hentSak(client)
            opprettIverksattRevurdering(
                sakid = sakJson.id,
                client = client,
                fraogmed = fraOgMed.toString(),
                tilogmed = tilOgMed.toString(),
                appComponents = appComponents,
                informasjonSomRevurderes = listOf(
                    Revurderingsteg.Inntekt,
                ),
                leggTilFradrag = { sakId, behandlingId, fraOgMed, tilOgMed ->
                    no.nav.su.se.bakover.web.revurdering.fradrag.leggTilFradrag(
                        sakId = sakId,
                        behandlingId = behandlingId,
                        fraOgMed = fraOgMed,
                        tilOgMed = tilOgMed,
                        fradrag = fradrag,
                        client = client,
                    )
                },
            )
        }

        private fun TestSakReguleringIT.hentSak(client: HttpClient) = hentSakRequest(fnr, sakstype, client).also {
            saksnummer = it.saksnummer
        }

        private fun TestSakReguleringIT.verifiserAutomatisk(client: HttpClient) {
            val sakJson = hentSak(client)
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
            val sakJson = hentSak(client)
            sakJson.reguleringer.size shouldBe 1
            with(sakJson.reguleringer[0]) {
                reguleringstype shouldBe "MANUELL"
                beregning shouldBe null
                if (verifiserÅrsak == ÅrsakTilManuellReguleringKategori.ManglerRegulertBeløpForFradrag) {
                    årsakForManuell.size shouldBe 1
                    (årsakForManuell.single() as ÅrsakTilManuellReguleringJson.ManglerRegulertBeløpForFradrag).let { årsakForManuell ->
                        val fradragÅrsak = fradrag.singleOrNull()
                        fradragÅrsak?.type shouldBe årsakForManuell.fradragskategori
                        fradragÅrsak?.tilhører shouldBe årsakForManuell.fradragTilhører
                        årsakForManuell.fradragTilhører shouldBe FradragTilhører.BRUKER.name
                    }
                }
                if (verifiserÅrsak == ÅrsakTilManuellReguleringKategori.ManglerIeuFraPesys) {
                    årsakForManuell.size shouldBe 1
                    årsakForManuell.single() shouldBe ÅrsakTilManuellReguleringJson.ManglerIeuFraPesys
                }
                if (verifiserÅrsak == ÅrsakTilManuellReguleringKategori.EtAutomatiskFradragHarFremtidigPeriode) {
                    årsakForManuell.size shouldBe 2
                    årsakForManuell.filter { it == ÅrsakTilManuellReguleringJson.EtAutomatiskFradragHarFremtidigPeriode }.size shouldBe 1
                    årsakForManuell.filter { it == ÅrsakTilManuellReguleringJson.ManglerIeuFraPesys }.size shouldBe 1
                }
            }
        }

        private fun TestSakReguleringIT.verifiserBleIkkeRegulert(client: HttpClient) {
            val sakJson = hentSak(client)
            sakJson.reguleringer.size shouldBe 0
        }

        // TODO scenariet ikke løpende
        // TODO scenariet allerede brukt nytt grunnbeløp.. enten revurdert eller søknadsbehandling

        private fun ReguleringKjøring.verifiserFullReguleringskjøring() {
            sakerAntall shouldBe TestScenarietSaker.alle.size

            with(reguleringerAutomatisk) {
                size shouldBe TestScenarietSaker.tilAutomatisk.size
                forEach { resultat ->
                    resultat.utfall shouldBe Reguleringsresultat.Utfall.AUTOMATISK
                }
            }

            with(reguleringerManuell) {
                size shouldBe TestScenarietSaker.tilManuell.size
                filter { it.beskrivelse == "ManglerRegulertBeløpForFradrag" && it.utfall == Reguleringsresultat.Utfall.MANUELL }.size shouldBe 1
                filter { it.beskrivelse == "ManglerIeuFraPesys" && it.utfall == Reguleringsresultat.Utfall.MANUELL }.size shouldBe 1
                filter { it.beskrivelse == "EtAutomatiskFradragHarFremtidigPeriode, ManglerIeuFraPesys" && it.utfall == Reguleringsresultat.Utfall.MANUELL }.size shouldBe 1
            }

            with(sakerMåRevurderes) {
                size shouldBe TestScenarietSaker.tilRevurdering.size
                filter { it.beskrivelse.contains("DIFFERANSE_MED_EKSTERNE_BELØP") }.forEach {
                    it.utfall shouldBe Reguleringsresultat.Utfall.MÅ_REVURDERE
                    it.beskrivelse shouldBe "ÅrsakRevurdering(årsak=DIFFERANSE_MED_EKSTERNE_BELØP, diffBeløp=[Fradrag(eksisterendeBeløp=10000.00, nyttBeløp=10100.00, fradragstype=Uføretrygd, tilhører=BRUKER)])"
                }
                with(single { it.beskrivelse.contains("REGULERING_ER_OVER_TOLERANSEGRENSE") }) {
                    utfall shouldBe Reguleringsresultat.Utfall.MÅ_REVURDERE
                    beskrivelse shouldBe "ÅrsakRevurdering(årsak=REGULERING_ER_OVER_TOLERANSEGRENSE, diffBeløp=[BeregningOverToleranse(eksisterendeBeløp=1479, nyttBeløp=10952, toleransegrense=1626.9)])"
                }
            }

            with(reguleringerSomFeilet) {
                size shouldBe TestScenarietSaker.vilFeile.size

                // TODO Denne bør endres til å falle til revurdering tilsvarende som diff på beløp?
                single { it.saksnummer.nummer == UFØRE_FINNES_IKKE_PESYS.saksnummer }.let {
                    it.utfall shouldBe Reguleringsresultat.Utfall.FEILET
                    it.beskrivelse shouldContain FeilMedEksternRegulering.IngenPeriodeFraPesys.toString()
                }

                single { it.saksnummer.nummer == UFØRE_IKKE_REGULERT_PESYS.saksnummer }.let {
                    it.utfall shouldBe Reguleringsresultat.Utfall.FEILET
                    it.beskrivelse shouldContain FeilMedEksternRegulering.ManglerPeriodeFørOgEtterReguleringFraPesys.toString()
                }
            }

            sakerAlleredeRegulert.size shouldBe TestScenarietSaker.alleredeRegulert.size
        }

        private fun ReguleringKjøring.verifiserRekjøringAvRegulering() {
            reguleringerAutomatisk.size shouldBe 0
            reguleringerManuell.size shouldBe 0
            // samme som forrige kjøring
            sakerMåRevurderes.size shouldBe TestScenarietSaker.tilRevurdering.size
            // samme som forrige kjøring
            reguleringerSomFeilet.size shouldBe TestScenarietSaker.vilFeile.size
            // samme antall som manuell forrige kjøring
            reguleringerAlleredeÅpen.size shouldBe TestScenarietSaker.tilManuell.size
            // samme antall som sist + antall automatisk forrige kjøring
            sakerAlleredeRegulert.size shouldBe TestScenarietSaker.alleredeRegulert.size + TestScenarietSaker.tilAutomatisk.size
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

    val UFØRE_FINNES_IKKE_PESYS = TestSakReguleringIT.create(
        fnr = Fnr("00000000010"),
        sakstype = Sakstype.UFØRE,
        fradrag = listOf(Fradragstype.Kategori.Uføretrygd to FradragTilhører.BRUKER),
        innvilgetIPesys = false,
    )

    val UFØRE_IKKE_REGULERT_PESYS = TestSakReguleringIT.create(
        fnr = Fnr("00000000011"),
        sakstype = Sakstype.UFØRE,
        fradrag = listOf(Fradragstype.Kategori.Uføretrygd to FradragTilhører.BRUKER),
        regulertIPesys = false,
    )

    val UFØRE_I_SENERE_PERIODE = TestSakReguleringIT.create(
        fnr = Fnr("00000000012"),
        sakstype = Sakstype.UFØRE,
        fradrag = listOf(Fradragstype.Kategori.Uføretrygd to FradragTilhører.BRUKER),
        innvilgetIPesys = false,
    )

    val ALDERPENSJON_UTLAND = TestSakReguleringIT.create(
        fnr = Fnr("00000000013"),
        sakstype = Sakstype.ALDER,
        fradrag = listOf(Fradragstype.Kategori.Alderspensjon to FradragTilhører.BRUKER),
        innvilgetIPesys = false,
        utland = true,
    )

    val OVER_10_PRORSENT_UTEN_G_FRADRAG = TestSakReguleringIT.create(
        fnr = Fnr("00000000014"),
        sakstype = Sakstype.ALDER,
        innvilgetIPesys = false,
        fradrag = listOf(Fradragstype.Kategori.Arbeidsinntekt to FradragTilhører.BRUKER),
        overToleranseGrense = true,
    )

    val OVER_10_PRORSENT_MED_G_FRADRAG = TestSakReguleringIT.create(
        fnr = Fnr("00000000015"),
        sakstype = Sakstype.ALDER,
        fradrag = listOf(Fradragstype.Kategori.Alderspensjon to FradragTilhører.BRUKER),
        overToleranseGrense = true,
    )

    val INNVILGET_SØKNAD_ETTER_NY_G = TestSakReguleringIT.create(
        fnr = Fnr("00000000016"),
        sakstype = Sakstype.ALDER,
        fradrag = listOf(Fradragstype.Kategori.Alderspensjon to FradragTilhører.BRUKER),
    )

    // TODO automatisk uten innvilget i Pesys

    val tilAutomatisk = listOf(
        AUTOMATISK_UFØRE,
        AUTOMATISK_ALDER,
        AUTOMATISK_UFØRE_MED_IEU,
        ALDER_MED_EPS_MED_SU,
        ALDERPENSJON_UTLAND,
        OVER_10_PRORSENT_UTEN_G_FRADRAG,
    )
    val tilManuell = listOf(
        MANUELL_UFØRE,
        MANUELL_UFØRE_MED_IEU,
        UFØRE_I_SENERE_PERIODE,
    )
    val tilRevurdering = listOf(
        MÅ_REVURDERES_UFØRE,
        REVURDERING_UFØRE_MED_IEU,
        OVER_10_PRORSENT_MED_G_FRADRAG,
    )
    val vilFeile = listOf(
        UFØRE_FINNES_IKKE_PESYS,
        UFØRE_IKKE_REGULERT_PESYS,
    )
    val alleredeRegulert = listOf(
        INNVILGET_SØKNAD_ETTER_NY_G,
    )

    val alle = tilAutomatisk + tilManuell + tilRevurdering + vilFeile + alleredeRegulert
}

data class TestSakReguleringIT(
    val fnr: Fnr,
    val sakstype: Sakstype,
    var saksnummer: Long? = null,

    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val tilOgMedFørRegulering: LocalDate,
    val fraOgMedEtterRegulering: LocalDate,

    val fradrag: List<FradragRequestJson>,

    val innvilgetIPesys: Boolean,
    val regulertIPesys: Boolean,
    val gradertUføretrygd: Boolean,
    val nullIeu: Boolean,
    val diffMellomSuOgPesys: Boolean,
    val eps: TestSakReguleringIT?,
    val utland: Boolean,
    val overToleranseGrense: Boolean,
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
        ).let {
            if (regulertIPesys) {
                it + listOf(
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
                )
            } else {
                it
            }
        },
    )

    fun alderPerioderFraPesys(): AlderBeregningsperioderPerPerson = AlderBeregningsperioderPerPerson(
        fnr = fnr.toString(),
        perioder = listOf(
            AlderBeregningsperiode(
                netto = if (overToleranseGrense) 18000 else 10000,
                fom = fraOgMed,
                tom = tilOgMedFørRegulering,
                grunnbelop = GRUNNBELØP_2024,
            ),
            AlderBeregningsperiode(
                netto = if (overToleranseGrense) 9250 else 10250,
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
            regulertIPesys: Boolean = true,
            gradertUføretrygd: Boolean = false,
            nullIeu: Boolean = false,
            diffMellomSuOgPesys: Boolean = false,
            eps: TestSakReguleringIT? = null,
            utland: Boolean = false,
            overToleranseGrense: Boolean = false,
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
                            FradragTilhører.BRUKER -> if (overToleranseGrense) 18000.0 else 10000.0
                            FradragTilhører.EPS -> 1000.0
                        },
                        utenlandskInntekt = if (utland) UtenlandskInntektJson(1002, "SEK", 1.02785514) else null,
                        tilhører = tilhører.name,
                    )
                },
                innvilgetIPesys = innvilgetIPesys,
                regulertIPesys = regulertIPesys,
                gradertUføretrygd = gradertUføretrygd,
                nullIeu = nullIeu,
                diffMellomSuOgPesys = diffMellomSuOgPesys,
                eps = eps,
                utland = utland,
                overToleranseGrense = overToleranseGrense,
            )
        }
    }
}
