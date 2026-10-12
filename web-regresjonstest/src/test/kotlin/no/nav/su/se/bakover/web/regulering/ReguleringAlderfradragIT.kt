package no.nav.su.se.bakover.web.regulering

import common.presentation.beregning.FradragRequestJson
import common.presentation.beregning.UtenlandskInntektJson
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.server.testing.ApplicationTestBuilder
import no.nav.su.se.bakover.client.pesys.AlderBeregningsperiode
import no.nav.su.se.bakover.client.pesys.AlderBeregningsperioderPerPerson
import no.nav.su.se.bakover.client.pesys.PesysClient
import no.nav.su.se.bakover.client.pesys.PesysclientStub
import no.nav.su.se.bakover.client.pesys.UføreBeregningsperiode
import no.nav.su.se.bakover.client.pesys.UføreBeregningsperioderPerPerson
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.oktober
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøring
import no.nav.su.se.bakover.domain.regulering.Reguleringsresultat
import no.nav.su.se.bakover.domain.regulering.ÅrsakTilManuellReguleringKategori
import no.nav.su.se.bakover.domain.revurdering.steg.Revurderingsteg
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.komponenttest.AppComponents
import no.nav.su.se.bakover.web.regulering.ReguleringAlderfradragIT.Companion.GRUNNBELØP_2025
import no.nav.su.se.bakover.web.regulering.ReguleringAlderfradragIT.Companion.ÅR
import no.nav.su.se.bakover.web.regulering.TestScenarierSaker.AUTOMATISK_TIL_ATTESTERING_INGEN_EPS
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
import org.junit.jupiter.api.extension.ExtendWith
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import javax.sql.DataSource
import kotlin.collections.map

@ExtendWith(DbExtension::class)
internal class ReguleringAlderfradragIT(private val dataSource: DataSource) {

    companion object {
        const val ÅR = 2025
        const val OMREGNINGSMÅNED = "$ÅR-10-01"
        const val GRUNNBELØP_2025 = 130160

        val klokke: Clock =
            TikkendeKlokke(
                Clock.fixed(
                    25.mai(ÅR).atTime(1, 2, 3, 456789000).toInstant(ZoneOffset.UTC),
                    ZoneOffset.UTC,
                ),
            )
    }

    @Nested
    inner class `Automatisk omregning aldersfradrag` {

        // Alle testsaker som trenger beløp fra pesys må legges til her
        val pesysStub = PesysclientStub.build(
            uførePeriode = TestScenarierSaker.alle.filter { it.sakstype == Sakstype.UFØRE }
                .map { it.uførePerioderFraPesys() },
            alderPerioder = TestScenarierSaker.alle.filter { it.sakstype == Sakstype.ALDER }
                .map { it.alderPerioderFraPesys() },
        )

        @Test
        fun `full reguleringsjobb`() {
            withMigratedDb { dataSource ->
                testApplikasjon(dataSource, pesysStub) {
                    AUTOMATISK_TIL_ATTESTERING_INGEN_EPS.opprettOgInnvilgSøknad(client, it)

                    regulerAlderfradragAutomatisk(oktober(ÅR), this.client)

                    AUTOMATISK_TIL_ATTESTERING_INGEN_EPS.verifiserAutomatiskTilAttestering(client)

                    hentReguleringKjøringRequestAlderfradrag(client).single().verifiserFullReguleringskjøring()
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

        private fun TestSakAlderFradrag.revurder(
            client: HttpClient,
            appComponents: AppComponents,
            fraOgMed: LocalDate = januar(ÅR).fraOgMed,
            tilOgMed: LocalDate = desember(ÅR).tilOgMed,
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

        private fun TestSakAlderFradrag.hentSak(client: HttpClient) = hentSakRequest(fnr, sakstype, client).also {
            saksnummer = it.saksnummer
        }

        private fun TestSakAlderFradrag.verifiserAutomatiskTilAttestering(client: HttpClient) {
            val sakJson = hentSak(client)
            sakJson.reguleringer.size shouldBe 1
            with(sakJson.reguleringer.single()) {
                reguleringstype shouldBe "MANUELL"
                val regulertBeregning = beregning!!.månedsberegninger.single {
                    it.fraOgMed == OMREGNINGSMÅNED
                }

                val søknadsbehandling = sakJson.behandlinger.single()
                val beregningFørRegulering = søknadsbehandling.beregning!!.månedsberegninger.single {
                    it.fraOgMed == OMREGNINGSMÅNED
                }

                if (regulertBeregning.beløp == 0) {
                    regulertBeregning.beløp shouldBe beregningFørRegulering.beløp
                } else {
                    regulertBeregning.beløp shouldBeLessThan beregningFørRegulering.beløp
                }
            }
        }

        private fun TestSakAlderFradrag.verifiserManuell(
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

        private fun TestSakAlderFradrag.verifiserBleIkkeRegulert(client: HttpClient) {
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

            with(reguleringerSomFeilet) {
                size shouldBe TestScenarierSaker.vilFeile.size
                // TODO
            }
        }

        private fun ReguleringKjøring.Aldersfradrag.verifiserRekjøringAvRegulering() {
            // TODO
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
    // TODO val AUTOMATISK_TIL_ATTESTERING_EPS = TestSakAlderFradrag.create
    // TODO val AUTOMATISK_TIL_ATTESTERING_UFØRE = TestSakAlderFradrag.create

    // TODO val SKAL_IKKE_OMREGNES_UNDER_10_PROSENT = TestSakAlderFradrag.create
    // TODO val SKAL_IKKE_OMREGNES_HAR_IKKE_FRADRAG_ALDER = TestSakAlderFradrag.create

    val automatiskTilAttestering = listOf(
        AUTOMATISK_TIL_ATTESTERING_INGEN_EPS,
    )
    val skalIkkeOmregnes = listOf<TestSakAlderFradrag>()
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

    val omregnetIPesys: Boolean,

    val fradrag: List<FradragRequestJson>,

    val eps: TestSakAlderFradrag?,
    val utland: Boolean,
    val under10prosent: Boolean,
) {

    fun alderPerioderFraPesys(): AlderBeregningsperioderPerPerson = AlderBeregningsperioderPerPerson(
        fnr = fnr.toString(),
        perioder = listOf(
            AlderBeregningsperiode(
                netto = if (under10prosent) 18000 else PESYS_BELØP_FØR_OMREGNING, // TODO
                fom = fraOgMed,
                tom = tilOgMedFørRegulering,
                grunnbelop = GRUNNBELØP_2025,
            ),
            AlderBeregningsperiode(
                netto = if (under10prosent) 9250 else PESYS_BELØP_ETTER_OMREGNING,
                fom = fraOgMedEtterRegulering,
                tom = null,
                grunnbelop = GRUNNBELØP_2025,
            ),
        ),
    )

    fun uførePerioderFraPesys(): UføreBeregningsperioderPerPerson = UføreBeregningsperioderPerPerson(
        fnr = fnr.toString(),
        perioder =
        listOf(
            UføreBeregningsperiode(
                netto = PESYS_BELØP_FØR_OMREGNING,
                fom = fraOgMed,
                tom = tilOgMedFørRegulering,
                grunnbelop = GRUNNBELØP_2025,
                oppjustertInntektEtterUfore = null,
            ),
        ).let {
            if (omregnetIPesys) {
                it + listOf(
                    UføreBeregningsperiode(
                        netto = PESYS_BELØP_ETTER_OMREGNING,
                        fom = fraOgMedEtterRegulering,
                        tom = null,
                        grunnbelop = GRUNNBELØP_2025,
                        oppjustertInntektEtterUfore = null,
                    ),
                )
            } else {
                it
            }
        },
    )

    companion object {
        const val PESYS_BELØP_FØR_OMREGNING = 10000
        const val PESYS_BELØP_ETTER_OMREGNING = 11021 // akkurat over 10%

        fun create(
            fnr: Fnr,
            sakstype: Sakstype,
            fraOgMed: LocalDate = januar(ÅR).fraOgMed,
            tilOgMed: LocalDate = desember(ÅR).tilOgMed,
            tilOgMedFørRegulering: LocalDate = april(ÅR).tilOgMed,
            fraOgMedEtterRegulering: LocalDate = mai(ÅR).fraOgMed,
            fradrag: List<FradragRequestJson> = emptyList(),
            fradragstyper: List<Pair<Fradragstype.Kategori, FradragTilhører>> = emptyList(),
            omregnetIPesys: Boolean = true,
            eps: TestSakAlderFradrag? = null,
            utland: Boolean = false,
            under10prosent: Boolean = false,
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
                        under10prosent,
                        utland,
                        fraOgMed,
                        tilOgMed,
                    )
                },
                omregnetIPesys = omregnetIPesys,
                eps = eps,
                utland = utland,
                under10prosent = under10prosent,
            )
        }

        fun lagfradrag(
            fradrag: List<Pair<Fradragstype.Kategori, FradragTilhører>>,
            under10Prosent: Boolean = false,
            utland: Boolean = false,
            fraOgMed: LocalDate = januar(ÅR).fraOgMed,
            tilOgMed: LocalDate = desember(ÅR).tilOgMed,
        ) = fradrag.map { (type, tilhører) ->
            FradragRequestJson(
                periode = PeriodeJson(fraOgMed = fraOgMed.toString(), tilOgMed = tilOgMed.toString()),
                type = type.name,
                beskrivelse = null,
                beløp = when (tilhører) {
                    FradragTilhører.BRUKER -> if (under10Prosent) 18000.0 else 10000.0 // TODO under 10 prosent blir feil
                    FradragTilhører.EPS -> 1000.0
                },
                utenlandskInntekt = if (utland) UtenlandskInntektJson(1002, "SEK", 1.02785514) else null,
                tilhører = tilhører.name,
            )
        }
    }
}
