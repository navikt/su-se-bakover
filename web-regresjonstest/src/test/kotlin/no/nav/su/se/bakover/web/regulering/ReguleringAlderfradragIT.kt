package no.nav.su.se.bakover.web.regulering

import common.presentation.beregning.FradragRequestJson
import common.presentation.beregning.UtenlandskInntektJson
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.server.testing.ApplicationTestBuilder
import no.nav.su.se.bakover.client.pesys.AlderBeregningsperiode
import no.nav.su.se.bakover.client.pesys.AlderBeregningsperioderPerPerson
import no.nav.su.se.bakover.client.pesys.PesysClient
import no.nav.su.se.bakover.client.pesys.PesysclientStub
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.oktober
import no.nav.su.se.bakover.common.tid.toTidspunkt
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøring
import no.nav.su.se.bakover.domain.regulering.Reguleringsresultat
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.komponenttest.AppComponents
import no.nav.su.se.bakover.web.regulering.ReguleringAlderfradragIT.Companion.GRUNNBELØP_2025
import no.nav.su.se.bakover.web.regulering.ReguleringAlderfradragIT.Companion.ÅR
import no.nav.su.se.bakover.web.regulering.TestScenarierSaker.ALDER_AP_UTLAND
import no.nav.su.se.bakover.web.regulering.TestScenarierSaker.ALDER_MED_EPS_UNDER_10_PROSENT
import no.nav.su.se.bakover.web.regulering.TestScenarierSaker.AUTOMATISK_TIL_ATTESTERING_INGEN_EPS
import no.nav.su.se.bakover.web.regulering.TestScenarierSaker.AUTOMATISK_TIL_ATTESTERING_MED_EPS
import no.nav.su.se.bakover.web.regulering.TestScenarierSaker.SAK_ER_STANSET
import no.nav.su.se.bakover.web.regulering.TestScenarierSaker.UFØRE_MED_EPS_UNDER_10_PROSENT
import no.nav.su.se.bakover.web.routes.regulering.json.ReguleringJson
import no.nav.su.se.bakover.web.sak.hent.hentSakRequest
import no.nav.su.se.bakover.web.stans.iverksettStans
import no.nav.su.se.bakover.web.stans.opprettStans
import no.nav.su.se.bakover.web.søknadsbehandling.RevurderingJson
import no.nav.su.se.bakover.web.søknadsbehandling.bosituasjon.leggTilBosituasjon
import no.nav.su.se.bakover.web.søknadsbehandling.formue.leggTilFormue
import no.nav.su.se.bakover.web.søknadsbehandling.fradrag.leggTilFradrag
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.web.søknadsbehandling.uførhet.leggTilUføregrunnlag
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import javax.sql.DataSource
import kotlin.collections.map

@ExtendWith(DbExtension::class)
internal class ReguleringAlderfradragIT(private val dataSource: DataSource) {

    companion object {
        const val ÅR = 2025
        const val OMREGNINGSMÅNED = "$ÅR-10-01"
        const val GRUNNBELØP_2025 = 130160

        val NÅ = 25.mai(ÅR).atTime(1, 2, 3, 456789000).toInstant(ZoneOffset.UTC)!!

        val klokke: Clock = TikkendeKlokke(Clock.fixed(NÅ, ZoneOffset.UTC))
    }

    @Nested
    inner class `Automatisk omregning aldersfradrag` {

        // Alle testsaker som trenger beløp fra pesys må legges til her
        val pesysStub = PesysclientStub.build(
            alderPerioder = TestScenarierSaker.alle.filter { it.sakstype == Sakstype.ALDER }
                .map { it.alderPerioderFraPesys() },
        )

        @Test
        fun `full reguleringsjobb`() {
            withMigratedDb { dataSource ->
                testApplikasjon(dataSource, pesysStub) { appComp ->
                    AUTOMATISK_TIL_ATTESTERING_INGEN_EPS.opprettOgInnvilgSøknad(client, appComp)
                    AUTOMATISK_TIL_ATTESTERING_MED_EPS.opprettOgInnvilgSøknad(client, appComp)
                    ALDER_MED_EPS_UNDER_10_PROSENT.opprettOgInnvilgSøknad(client, appComp)
                    UFØRE_MED_EPS_UNDER_10_PROSENT.opprettOgInnvilgSøknad(client, appComp)
                    ALDER_AP_UTLAND.opprettOgInnvilgSøknad(client, appComp)
                    SAK_ER_STANSET.opprettOgInnvilgSøknad(client, appComp).also {
                        SAK_ER_STANSET.stans(client, appComp)
                    }

                    regulerAlderfradragAutomatisk(oktober(ÅR), this.client)

                    AUTOMATISK_TIL_ATTESTERING_INGEN_EPS.verifiserAutomatiskTilAttestering(client)
                    AUTOMATISK_TIL_ATTESTERING_MED_EPS.verifiserAutomatiskTilAttestering(client)
                    ALDER_MED_EPS_UNDER_10_PROSENT.verifiserSkalIkkeOmregnes(client)
                    UFØRE_MED_EPS_UNDER_10_PROSENT.verifiserSkalIkkeOmregnes(client)
                    ALDER_AP_UTLAND.verifiserSkalIkkeOmregnes(client)
                    SAK_ER_STANSET.verifiserSkalIkkeOmregnes(client)

                    hentReguleringKjøringRequestAlderfradrag(client).single().verifiserFullReguleringskjøring()

                    regulerAlderfradragAutomatisk(oktober(ÅR), this.client)
                    hentReguleringKjøringRequestAlderfradrag(client).last().verifiserRekjøringAvRegulering()
                }
            }
        }

        private fun TestSakAlderFradrag.opprettOgInnvilgSøknad(client: HttpClient, appComponents: AppComponents) {
            opprettInnvilgetSøknadsbehandling(
                fnr = fnr.toString(),
                sakstype = sakstype,
                fraOgMed = januar(ÅR).fraOgMed.toString(),
                tilOgMed = desember(ÅR).tilOgMed.toString(),
                leggTilUføregrunnlag = { sakId, behandlingId ->
                    leggTilUføregrunnlag(
                        sakId = sakId,
                        behandlingId = behandlingId,
                        fraOgMed = januar(ÅR).fraOgMed.toString(),
                        tilOgMed = desember(ÅR).tilOgMed.toString(),
                        client = client,
                    )
                },
                fradrag = { sakId, behandlingId ->
                    leggTilFradrag(
                        sakId = sakId,
                        behandlingId = behandlingId,
                        fraOgMed = januar(ÅR).fraOgMed.toString(),
                        tilOgMed = desember(ÅR).tilOgMed.toString(),
                        fradrag = fradrag,
                        client = client,
                    )
                },
                leggTilBosituasjon = { sakId, behandlingId ->
                    leggTilBosituasjon(
                        sakId = sakId,
                        behandlingId = behandlingId,
                        fraOgMed = januar(ÅR).fraOgMed.toString(),
                        tilOgMed = desember(ÅR).tilOgMed.toString(),
                        epsFnr = eps?.fnr?.toString(),
                        delerBolig = if (eps == null) false else null,
                        erEpsFylt67 = eps?.sakstype == Sakstype.ALDER,
                        erEPSUførFlyktning = eps?.sakstype == Sakstype.UFØRE,
                        client = client,
                    )
                },
                leggTilFormue = { sakId, behandlingId ->
                    leggTilFormue(
                        sakId = sakId,
                        behandlingId = behandlingId,
                        fraOgMed = januar(ÅR).fraOgMed.toString(),
                        tilOgMed = desember(ÅR).tilOgMed.toString(),
                        harEps = eps != null,
                        client = client,
                    )
                },
                client = client,
                appComponents = appComponents,
            )
        }

        private fun TestSakAlderFradrag.stans(client: HttpClient, appComponents: AppComponents) {
            val stansMåned = NÅ.toTidspunkt().toLocalDate(ZoneId.of("Europe/Oslo")).plusMonths(1).withDayOfMonth(1)
            val sakJson = hentSak(client)
            val stansId = opprettStans(sakJson.id, fraOgMed = stansMåned.toString(), client = client).let {
                RevurderingJson.hentRevurderingId(it)
            }
            iverksettStans(sakJson.id, stansId, client = client, appComponents = appComponents)
        }

        private fun TestSakAlderFradrag.hentSak(client: HttpClient) = hentSakRequest(fnr, sakstype, client).also {
            saksnummer = it.saksnummer
        }

        private fun TestSakAlderFradrag.verifiserAutomatiskTilAttestering(client: HttpClient) {
            val sakJson = hentSak(client)
            sakJson.reguleringer.size shouldBe 1
            with(sakJson.reguleringer.single()) {
                reguleringstype shouldBe "MANUELL"
                reguleringsstatus shouldBe ReguleringJson.Status.ATTESTERING
                val regulertBeregning = beregning!!.månedsberegninger.single {
                    it.fraOgMed == OMREGNINGSMÅNED
                }

                val søknadsbehandling = sakJson.behandlinger.single()
                val beregningFørRegulering = søknadsbehandling.beregning!!.månedsberegninger.single {
                    it.fraOgMed == OMREGNINGSMÅNED
                }

                if (regulertBeregning.beløp == 0) {
                    beregningFørRegulering.beløp shouldBe 0
                } else {
                    regulertBeregning.beløp shouldBeLessThan beregningFørRegulering.beløp
                }
            }
        }

        private fun TestSakAlderFradrag.verifiserSkalIkkeOmregnes(client: HttpClient) {
            val sakJson = hentSak(client)
            sakJson.reguleringer.size shouldBe 0
        }

        private fun ReguleringKjøring.Aldersfradrag.verifiserFullReguleringskjøring() {
            sakerAntall shouldBe TestScenarierSaker.alle.size

            // De som er automatisk til attestering havner til manuell i kjøringsutfallet da de ikke er heltautomatisk
            with(reguleringerManuell) {
                size shouldBe TestScenarierSaker.automatiskTilAttestering.size
                forEach { resultat ->
                    resultat.utfall shouldBe Reguleringsresultat.Utfall.MANUELL
                }
            }

            with(skalIkkeOmregnes) {
                size shouldBe TestScenarierSaker.skalIkkeOmregnes.size

                single { it.saksnummer.nummer == ALDER_MED_EPS_UNDER_10_PROSENT.saksnummer }.let { resultat ->
                    resultat.utfall shouldBe Reguleringsresultat.Utfall.SKAL_IKKE_OMREGNES
                    resultat.beskrivelse shouldContain "ErUnder10ProsentEndring"
                }
                single { it.saksnummer.nummer == UFØRE_MED_EPS_UNDER_10_PROSENT.saksnummer }.let { resultat ->
                    resultat.utfall shouldBe Reguleringsresultat.Utfall.SKAL_IKKE_OMREGNES
                    resultat.beskrivelse shouldContain "ErUnder10ProsentEndring"
                }
                single { it.saksnummer.nummer == ALDER_AP_UTLAND.saksnummer }.let { resultat ->
                    resultat.utfall shouldBe Reguleringsresultat.Utfall.SKAL_IKKE_OMREGNES
                    resultat.beskrivelse shouldContain "HarIkkeAlderspensjonFradrag"
                }
                single { it.saksnummer.nummer == SAK_ER_STANSET.saksnummer }.let { resultat ->
                    resultat.utfall shouldBe Reguleringsresultat.Utfall.SKAL_IKKE_OMREGNES
                    resultat.beskrivelse shouldContain "HarStans"
                }
            }
        }

        private fun ReguleringKjøring.Aldersfradrag.verifiserRekjøringAvRegulering() {
            sakerAntall shouldBe TestScenarierSaker.alle.size
            skalIkkeOmregnes.size shouldBe TestScenarierSaker.skalIkkeOmregnes.size

            // Ble opprettet i forriuge kjøring
            reguleringerAlleredeÅpen.size shouldBe TestScenarierSaker.automatiskTilAttestering.size
            reguleringerManuell.size shouldBe 0
        }
    }

    private fun testApplikasjon(
        dataSource: DataSource,
        pesysStub: PesysClient,
        test: ApplicationTestBuilder.(appComponents: AppComponents) -> Unit,
    ) = SharedRegressionTestData.withTestApplication(
        clock = klokke,
        dataSource = dataSource,
        pesysClientStub = pesysStub,
    ) { test(it) }
}

object TestScenarierSaker {

    val AUTOMATISK_TIL_ATTESTERING_INGEN_EPS = TestSakAlderFradrag.create(
        fnr = Fnr.generer(),
        sakstype = Sakstype.ALDER,
        fradragstyper = listOf(Fradragstype.Kategori.Alderspensjon to FradragTilhører.BRUKER),
    )

    val AUTOMATISK_TIL_ATTESTERING_MED_EPS = TestSakAlderFradrag.create(
        fnr = Fnr.generer(),
        sakstype = Sakstype.ALDER,
        fradragstyper = listOf(Fradragstype.Kategori.Alderspensjon to FradragTilhører.EPS),
        eps = AUTOMATISK_TIL_ATTESTERING_INGEN_EPS,
    )

    val ALDER_MED_EPS_UNDER_10_PROSENT = TestSakAlderFradrag.create(
        fnr = Fnr.generer(),
        sakstype = Sakstype.UFØRE,
        fradragstyper = listOf(Fradragstype.Kategori.Alderspensjon to FradragTilhører.EPS),
        eps = AUTOMATISK_TIL_ATTESTERING_INGEN_EPS,
        under10prosent = true,
    )

    val UFØRE_MED_EPS_UNDER_10_PROSENT = TestSakAlderFradrag.create(
        fnr = Fnr.generer(),
        sakstype = Sakstype.UFØRE,
        fradragstyper = listOf(Fradragstype.Kategori.Alderspensjon to FradragTilhører.EPS),
        eps = AUTOMATISK_TIL_ATTESTERING_INGEN_EPS,
        under10prosent = true,
    )

    val ALDER_AP_UTLAND = TestSakAlderFradrag.create(
        fnr = Fnr.generer(),
        sakstype = Sakstype.ALDER,
        fradragstyper = listOf(Fradragstype.Kategori.Alderspensjon to FradragTilhører.BRUKER),
        utland = true,
    )

    val SAK_ER_STANSET = TestSakAlderFradrag.create(
        fnr = Fnr.generer(),
        sakstype = Sakstype.UFØRE,
        fradragstyper = listOf(Fradragstype.Kategori.Alderspensjon to FradragTilhører.BRUKER),
        stans = true,
    )

    val automatiskTilAttestering = listOf(
        AUTOMATISK_TIL_ATTESTERING_INGEN_EPS,
        AUTOMATISK_TIL_ATTESTERING_MED_EPS,
    )
    val skalIkkeOmregnes = listOf(
        ALDER_MED_EPS_UNDER_10_PROSENT,
        UFØRE_MED_EPS_UNDER_10_PROSENT,
        ALDER_AP_UTLAND,
        SAK_ER_STANSET,
    )
    val vilFeile = listOf<TestSakAlderFradrag>()

    val alle = automatiskTilAttestering + skalIkkeOmregnes + vilFeile
}

data class TestSakAlderFradrag(
    val fnr: Fnr,
    val sakstype: Sakstype,
    var saksnummer: Long? = null,

    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val tilOgMedFørRegulering: LocalDate,
    val fraOgMedEtterRegulering: LocalDate,

    val fradrag: List<FradragRequestJson>,

    val eps: TestSakAlderFradrag?,
    val stans: Boolean,
) {

    fun alderPerioderFraPesys(): AlderBeregningsperioderPerPerson = AlderBeregningsperioderPerPerson(
        fnr = fnr.toString(),
        perioder = listOf(
            AlderBeregningsperiode(
                netto = 10000,
                fom = fraOgMed,
                tom = tilOgMedFørRegulering,
                grunnbelop = GRUNNBELØP_2025,
            ),
            AlderBeregningsperiode(
                netto = 11021,
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
            fraOgMed: LocalDate = januar(ÅR).fraOgMed,
            tilOgMed: LocalDate = desember(ÅR).tilOgMed,
            tilOgMedFørRegulering: LocalDate = april(ÅR).tilOgMed,
            fraOgMedEtterRegulering: LocalDate = mai(ÅR).fraOgMed,
            fradrag: List<FradragRequestJson> = emptyList(),
            fradragstyper: List<Pair<Fradragstype.Kategori, FradragTilhører>> = emptyList(),
            eps: TestSakAlderFradrag? = null,
            under10prosent: Boolean = false,
            utland: Boolean = false,
            stans: Boolean = false,
        ): TestSakAlderFradrag {
            return TestSakAlderFradrag(
                fnr = fnr,
                sakstype = sakstype,
                fraOgMed = fraOgMed,
                tilOgMed = tilOgMed,
                tilOgMedFørRegulering = tilOgMedFørRegulering,
                fraOgMedEtterRegulering = fraOgMedEtterRegulering,
                fradrag = fradrag.ifEmpty {
                    lagfradrag(
                        fradragstyper,
                        utland,
                        fraOgMed,
                        tilOgMed,
                        beløp = 10000.0,
                    ).let {
                        if (eps == null) {
                            it
                        } else {
                            // eps med AP må ekstra fradrag for å komme overpga REGEL_FRADRAG_EPS_OVER_FRIBELØP
                            it + lagfradrag(
                                fradrag = listOf(Pair(Fradragstype.Kategori.Arbeidsinntekt, FradragTilhører.EPS)),
                                beløp = if (under10prosent) 1000.0 else 20550.5,
                                utland = utland,
                            )
                        }
                    }
                },
                eps = eps,
                stans = stans,
            )
        }

        fun lagfradrag(
            fradrag: List<Pair<Fradragstype.Kategori, FradragTilhører>>,
            utland: Boolean = false,
            fraOgMed: LocalDate = januar(ÅR).fraOgMed,
            tilOgMed: LocalDate = desember(ÅR).tilOgMed,
            beløp: Double,
        ) = fradrag.map { (type, tilhører) ->
            FradragRequestJson(
                periode = PeriodeJson(fraOgMed = fraOgMed.toString(), tilOgMed = tilOgMed.toString()),
                type = type.name,
                beskrivelse = null,
                beløp = beløp,
                utenlandskInntekt = if (utland) UtenlandskInntektJson(10000, "SEK", 1.02785514) else null,
                tilhører = tilhører.name,
            )
        }
    }
}
