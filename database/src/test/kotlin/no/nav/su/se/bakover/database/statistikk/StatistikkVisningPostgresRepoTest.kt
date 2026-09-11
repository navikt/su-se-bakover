package no.nav.su.se.bakover.database.statistikk

import BehandlingResultat
import BehandlingStatus
import Behandlingstype
import YtelseType
import behandling.revurdering.domain.Opphørsgrunn
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.domain.statistikk.BehandlingMetode
import no.nav.su.se.bakover.common.domain.statistikk.SakStatistikk
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkAggregatstatus
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkVisningsvalg
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkgrunnlag
import no.nav.su.se.bakover.domain.statistikk.Statistikkoppløsning
import no.nav.su.se.bakover.domain.statistikk.StønadStatistikkAggregatstatus
import no.nav.su.se.bakover.domain.statistikk.StønadStatistikkAggregertRad
import no.nav.su.se.bakover.domain.statistikk.StønadStatistikkBestandsendringRad
import no.nav.su.se.bakover.service.statistikk.SakstatistikkSvar
import no.nav.su.se.bakover.service.statistikk.StatistikkVisningServiceImpl
import no.nav.su.se.bakover.service.statistikk.StønadStatistikkDatagrunnlag
import no.nav.su.se.bakover.service.statistikk.StønadstatistikkSvar
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import statistikk.domain.StønadsklassifiseringDto
import statistikk.domain.StønadstatistikkDto
import java.time.YearMonth
import java.util.UUID
import javax.sql.DataSource

@ExtendWith(DbExtension::class)
internal class StatistikkVisningPostgresRepoTest(private val dataSource: DataSource) {

    @Test
    fun `flytter sakstatistikkaggregat gjennom hele livsløpet`() {
        val repo = TestDataHelper(dataSource).databaseRepos.statistikkVisningRepo
        val januar = YearMonth.of(2026, 1)
        val februar = YearMonth.of(2026, 2)

        val første = repo.hentEllerOpprettSakstatistikkAggregat(januar)
        val samme = repo.hentEllerOpprettSakstatistikkAggregat(januar)
        val andre = repo.hentEllerOpprettSakstatistikkAggregat(februar)

        første.status shouldBe SakStatistikkAggregatstatus.VENTER
        samme.id shouldBe første.id

        val claimetAndre = repo.hentNesteSakstatistikkAggregatTilGenerering(aggregatId = andre.id)!!
        claimetAndre.id shouldBe andre.id
        claimetAndre.status shouldBe SakStatistikkAggregatstatus.PÅGÅR
        claimetAndre.startet shouldNotBe null

        repo.hentNesteSakstatistikkAggregatTilGenerering(aggregatId = andre.id) shouldBe null

        val claimetFørste = repo.hentNesteSakstatistikkAggregatTilGenerering()!!
        claimetFørste.id shouldBe første.id
        repo.ferdigstillSakstatistikkAggregat(
            id = første.id,
            startet = claimetFørste.startet!!.minusSeconds(1),
            grunnlag = SakStatistikkgrunnlag(emptyList()),
            maksSekvensId = 17L,
        )
        repo.hentEllerOpprettSakstatistikkAggregat(januar).status shouldBe
            SakStatistikkAggregatstatus.PÅGÅR

        repo.ferdigstillSakstatistikkAggregat(
            id = første.id,
            startet = claimetFørste.startet!!,
            grunnlag = SakStatistikkgrunnlag(emptyList()),
            maksSekvensId = 17L,
        )

        repo.hentEllerOpprettSakstatistikkAggregat(januar).let {
            it.status shouldBe SakStatistikkAggregatstatus.FERDIG
            it.maksSekvensId shouldBe 17L
            it.grunnlag shouldBe SakStatistikkgrunnlag(emptyList())
            it.ferdig shouldNotBe null
        }

        repo.markerSakstatistikkAggregatForRegenerering(første.id)
        repo.hentEllerOpprettSakstatistikkAggregat(januar).let {
            it.status shouldBe SakStatistikkAggregatstatus.VENTER
            it.grunnlag shouldBe null
            it.startet shouldBe null
            it.ferdig shouldBe null
        }

        val claimetPåNytt = repo.hentNesteSakstatistikkAggregatTilGenerering(aggregatId = første.id)!!
        repo.markerSakstatistikkAggregatFeilet(første.id, claimetPåNytt.startet!!, "forventet feil")
        repo.hentEllerOpprettSakstatistikkAggregat(januar).status shouldBe
            SakStatistikkAggregatstatus.FEILET
    }

    @Test
    fun `henter komplett sakstatistikkgrunnlag med sekvensavgrensning`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = testDataHelper.databaseRepos.statistikkVisningRepo
        val behandlingId = UUID.randomUUID()
        val første = lagSakstatistikk(
            behandlingId = behandlingId,
            status = BehandlingStatus.Registrert,
            funksjonellTid = "2026-05-02T10:00:00Z",
            resultat = null,
            begrunnelse = null,
        )
        val andre = lagSakstatistikk(
            behandlingId = behandlingId,
            status = BehandlingStatus.Iverksatt,
            funksjonellTid = "2026-05-03T10:00:00Z",
            resultat = BehandlingResultat.Opphør,
            begrunnelse = Opphørsgrunn.FOR_HØY_INNTEKT.name,
        )
        testDataHelper.sakStatistikkRepo.lagreSakStatistikk(første)
        val mai = YearMonth.of(2026, 5)
        val maksEtterFørste = repo.hentMaksSakstatistikkSekvensId(mai)!!
        testDataHelper.sakStatistikkRepo.lagreSakStatistikk(andre)

        repo.hentSakstatistikkgrunnlag(mai, maksEtterFørste).single().let {
            it.sekvensId shouldBe maksEtterFørste
            it.behandlingId shouldBe behandlingId
            it.sakYtelse shouldBe første.sakYtelse
            it.behandlingType shouldBe første.behandlingType
            it.behandlingAarsak shouldBe første.behandlingAarsak
            it.behandlingStatus shouldBe første.behandlingStatus
            it.behandlingResultat shouldBe null
            it.resultatBegrunnelse shouldBe null
            it.mottattTid shouldBe første.mottattTid
            it.registrertTid shouldBe første.registrertTid
            it.funksjonellTid shouldBe første.funksjonellTid
            it.tekniskTid shouldBe første.tekniskTid
            it.revurderingstype shouldBe null
        }

        repo.hentSakstatistikkgrunnlag(mai, maksSekvensId = null).let {
            it.map { rad -> rad.behandlingStatus } shouldContainExactly
                listOf(BehandlingStatus.Registrert.value, BehandlingStatus.Iverksatt.value)
            it.last().behandlingResultat shouldBe BehandlingResultat.Opphør.value
            it.last().resultatBegrunnelse shouldBe Opphørsgrunn.FOR_HØY_INNTEKT.name
        }
    }

    @Test
    fun `gjenbruker ferdig sakstatistikk til en ny relevant rad krever regenerering`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = testDataHelper.databaseRepos.statistikkVisningRepo
        val service = StatistikkVisningServiceImpl(repo)
        val nøkkel = aggregatnøkkel()
        val behandlingId = UUID.randomUUID()
        testDataHelper.sakStatistikkRepo.lagreSakStatistikk(
            lagSakstatistikk(
                behandlingId = behandlingId,
                status = BehandlingStatus.Registrert,
                funksjonellTid = "2026-05-02T10:00:00Z",
                resultat = null,
                begrunnelse = null,
            ),
        )

        val førsteGenerering = service.hentSakstatistikk(nøkkel) as SakstatistikkSvar.Genererer
        service.genererSakstatistikk(førsteGenerering.aggregatIder)
        val førsteOppsummering = (service.hentSakstatistikk(nøkkel) as SakstatistikkSvar.Ferdig).oppsummering

        service.hentSakstatistikk(nøkkel) shouldBe SakstatistikkSvar.Ferdig(førsteOppsummering)
        val ukesoppsummering =
            service.hentSakstatistikk(nøkkel.copy(oppløsning = Statistikkoppløsning.UKE))
                .let { it as SakstatistikkSvar.Ferdig }
                .oppsummering
        ukesoppsummering.oppløsning shouldBe Statistikkoppløsning.UKE
        ukesoppsummering.perioder.size shouldBe 5
        val avkortetUkesoppsummering =
            service.hentSakstatistikk(
                nøkkel.copy(
                    fraOgMed = java.time.LocalDate.of(2026, 5, 10),
                    tilOgMed = java.time.LocalDate.of(2026, 5, 20),
                    oppløsning = Statistikkoppløsning.UKE,
                ),
            ).let { it as SakstatistikkSvar.Ferdig }
                .oppsummering
        avkortetUkesoppsummering.fraOgMed shouldBe java.time.LocalDate.of(2026, 5, 10)
        avkortetUkesoppsummering.tilOgMed shouldBe java.time.LocalDate.of(2026, 5, 20)
        avkortetUkesoppsummering.perioder.first().fraOgMed shouldBe java.time.LocalDate.of(2026, 5, 10)
        avkortetUkesoppsummering.perioder.last().tilOgMed shouldBe java.time.LocalDate.of(2026, 5, 20)
        repo.hentNesteSakstatistikkAggregatTilGenerering(
            aggregatId = førsteGenerering.aggregatIder.single(),
        ) shouldBe null

        testDataHelper.sakStatistikkRepo.lagreSakStatistikk(
            lagSakstatistikk(
                behandlingId = UUID.randomUUID(),
                status = BehandlingStatus.Iverksatt,
                funksjonellTid = "2026-06-01T00:00:00Z",
                resultat = BehandlingResultat.Innvilget,
                begrunnelse = null,
            ),
        )

        service.hentSakstatistikk(nøkkel) shouldBe SakstatistikkSvar.Ferdig(førsteOppsummering)

        val tomånedersnøkkel = SakStatistikkVisningsvalg(
            fraOgMed = nøkkel.fraOgMed,
            tilOgMed = YearMonth.of(2026, 6).atEndOfMonth(),
            oppløsning = Statistikkoppløsning.MÅNED,
        )
        val juniGenerering = service.hentSakstatistikk(tomånedersnøkkel) as SakstatistikkSvar.Genererer
        juniGenerering.aggregatIder.size shouldBe 1
        service.genererSakstatistikk(juniGenerering.aggregatIder)
        val ukesvisning = service.hentSakstatistikk(
            tomånedersnøkkel.copy(oppløsning = Statistikkoppløsning.UKE),
        ) as SakstatistikkSvar.Ferdig
        ukesvisning.oppsummering.oppløsning shouldBe Statistikkoppløsning.UKE
        ukesvisning.oppsummering.fraOgMed shouldBe nøkkel.fraOgMed
        ukesvisning.oppsummering.tilOgMed shouldBe YearMonth.of(2026, 6).atEndOfMonth()

        testDataHelper.sakStatistikkRepo.lagreSakStatistikk(
            lagSakstatistikk(
                behandlingId = behandlingId,
                status = BehandlingStatus.Iverksatt,
                funksjonellTid = "2026-05-03T10:00:00Z",
                resultat = BehandlingResultat.Innvilget,
                begrunnelse = null,
            ),
        )

        val regenerering = service.hentSakstatistikk(nøkkel) as SakstatistikkSvar.Genererer
        regenerering.aggregatIder shouldBe førsteGenerering.aggregatIder
        service.genererSakstatistikk(regenerering.aggregatIder)

        val regenerertOppsummering =
            (service.hentSakstatistikk(nøkkel) as SakstatistikkSvar.Ferdig).oppsummering
        regenerertOppsummering shouldNotBe førsteOppsummering
    }

    @Test
    fun `aggregerer siste stønadsrad og beregner bestandsendringer`() {
        val testDataHelper = TestDataHelper(dataSource)
        val stønadRepo = testDataHelper.stønadStatistikkRepo
        val repo = testDataHelper.databaseRepos.statistikkVisningRepo
        val april = YearMonth.of(2026, 4)
        val mai = YearMonth.of(2026, 5)
        val videreførtSak = UUID.randomUUID()
        val utgåttSak = UUID.randomUUID()
        val nySak = UUID.randomUUID()

        stønadRepo.lagreMånedStatistikk(
            listOf(
                lagStønadstatistikkRad(måned = april, sakId = videreførtSak),
                lagStønadstatistikkRad(måned = april, sakId = utgåttSak),
                lagStønadstatistikkRad(
                    måned = mai,
                    sakId = videreførtSak,
                    tekniskTid = Tidspunkt.parse("2026-05-10T10:00:00Z"),
                ),
                lagStønadstatistikkRad(
                    måned = mai,
                    sakId = videreførtSak,
                    tekniskTid = Tidspunkt.parse("2026-05-20T10:00:00Z"),
                    stonadsklassifisering = StønadsklassifiseringDto.BOR_MED_ANDRE_VOKSNE,
                ),
                lagStønadstatistikkRad(måned = mai, sakId = nySak),
            ),
        )
        stønadRepo.markerMånedGenerert(april)
        stønadRepo.markerMånedGenerert(mai)

        repo.hentStønadstatistikk(mai) shouldContainExactly listOf(
            StønadStatistikkAggregertRad(
                måned = mai,
                stønadstype = StønadstatistikkDto.Stønadstype.SU_ALDER,
                vedtakstype = StønadstatistikkDto.Vedtakstype.REVURDERING,
                vedtaksresultat = StønadstatistikkDto.Vedtaksresultat.INNVILGET,
                stønadsklassifisering = StønadsklassifiseringDto.BOR_ALENE,
                antall = 1,
            ),
            StønadStatistikkAggregertRad(
                måned = mai,
                stønadstype = StønadstatistikkDto.Stønadstype.SU_ALDER,
                vedtakstype = StønadstatistikkDto.Vedtakstype.REVURDERING,
                vedtaksresultat = StønadstatistikkDto.Vedtaksresultat.INNVILGET,
                stønadsklassifisering = StønadsklassifiseringDto.BOR_MED_ANDRE_VOKSNE,
                antall = 1,
            ),
        )
        repo.hentStønadstatistikkBestandsendringer(mai) shouldContainExactly listOf(
            StønadStatistikkBestandsendringRad(
                måned = mai,
                stønadstype = StønadstatistikkDto.Stønadstype.SU_ALDER,
                nye = 1,
                videreført = 1,
                utgått = 1,
                endretStønadsklassifisering = 1,
            ),
        )
        repo.hentStønadstatistikkAggregater(april, mai).map { it.måned }.toSet() shouldBe setOf(april, mai)
    }

    @Test
    fun `flytter månedlig stønadstatistikkaggregat gjennom livsløpet og gjerder gamle arbeidere`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = testDataHelper.databaseRepos.statistikkVisningRepo
        val april = YearMonth.of(2026, 4)
        val mai = YearMonth.of(2026, 5)
        testDataHelper.stønadStatistikkRepo.markerMånedGenerert(april)
        testDataHelper.stønadStatistikkRepo.markerMånedGenerert(mai)
        val aggregater = repo.hentStønadstatistikkAggregater(april, mai)
        aggregater.map { it.måned } shouldContainExactly listOf(april, mai)
        aggregater.forEach { it.status shouldBe StønadStatistikkAggregatstatus.VENTER }

        val maiAggregat = aggregater.single { it.måned == mai }
        val claimet = repo.hentNesteStønadstatistikkAggregatTilGenerering(maiAggregat.id)!!
        claimet.status shouldBe StønadStatistikkAggregatstatus.PÅGÅR
        val startet = claimet.startet!!

        repo.ferdigstillStønadstatistikkAggregat(
            id = claimet.id,
            startet = startet.minusSeconds(1),
            payloadJson = "{}",
        )
        repo.hentStønadstatistikkAggregater(mai, mai).single().status shouldBe
            StønadStatistikkAggregatstatus.PÅGÅR

        repo.ferdigstillStønadstatistikkAggregat(
            id = claimet.id,
            startet = startet,
            payloadJson = "{}",
        )
        repo.hentStønadstatistikkAggregater(mai, mai).single().let {
            it.status shouldBe StønadStatistikkAggregatstatus.FERDIG
            it.payloadJson shouldBe "{}"
        }

        testDataHelper.stønadStatistikkRepo.markerMånedGenerert(mai)
        repo.hentStønadstatistikkAggregater(mai, mai).single().let {
            it.status shouldBe StønadStatistikkAggregatstatus.VENTER
            it.payloadJson shouldBe null
        }
    }

    @Test
    fun `returnerer manglende stønadstatistikk uten cache før den offisielle måneden er generert`() {
        val repo = TestDataHelper(dataSource).databaseRepos.statistikkVisningRepo
        val service = StatistikkVisningServiceImpl(repo)
        val mai = YearMonth.of(2026, 5)

        val svar = service.hentStønadstatistikk(mai, mai) as StønadstatistikkSvar.Ferdig

        svar.oppsummering.perioder.single().let {
            it.måned shouldBe mai
            it.datagrunnlag shouldBe StønadStatistikkDatagrunnlag.MANGLER
            it.rader shouldBe emptyList()
            it.bestandsendringerTilgjengelig shouldBe false
            it.bestandsendringer shouldBe emptyList()
        }
        repo.hentNesteStønadstatistikkAggregatTilGenerering() shouldBe null
    }

    @Test
    fun `cacher en offisielt generert tom stønadsmåned`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = testDataHelper.databaseRepos.statistikkVisningRepo
        val service = StatistikkVisningServiceImpl(repo)
        val april = YearMonth.of(2026, 4)
        val mai = YearMonth.of(2026, 5)
        testDataHelper.stønadStatistikkRepo.markerMånedGenerert(april)
        testDataHelper.stønadStatistikkRepo.markerMånedGenerert(mai)

        val generering = service.hentStønadstatistikk(mai, mai) as StønadstatistikkSvar.Genererer
        service.genererStønadstatistikk(generering.aggregatIder)

        val svar = service.hentStønadstatistikk(mai, mai) as StønadstatistikkSvar.Ferdig
        svar.oppsummering.perioder.single().let {
            it.datagrunnlag shouldBe StønadStatistikkDatagrunnlag.TILGJENGELIG
            it.rader shouldBe emptyList()
            it.bestandsendringerTilgjengelig shouldBe true
            it.bestandsendringer shouldBe emptyList()
        }
        service.hentStønadstatistikk(mai, mai) shouldBe svar
    }

    @Test
    fun `cacher ferdig generert stønadsmåned og regenererer når den offisielle markøren endres`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = testDataHelper.databaseRepos.statistikkVisningRepo
        val stønadRepo = testDataHelper.stønadStatistikkRepo
        val service = StatistikkVisningServiceImpl(repo)
        val april = YearMonth.of(2026, 4)
        val mai = YearMonth.of(2026, 5)
        val sakId = UUID.randomUUID()
        val aprilrad = lagStønadstatistikkRad(måned = april, sakId = sakId)
        val førsteMairad = lagStønadstatistikkRad(
            måned = mai,
            sakId = sakId,
            tekniskTid = Tidspunkt.parse("2026-05-10T10:00:00Z"),
        )
        stønadRepo.lagreMånedStatistikk(listOf(aprilrad, førsteMairad))
        stønadRepo.markerMånedGenerert(april)
        stønadRepo.markerMånedGenerert(mai)

        val førsteGenerering = service.hentStønadstatistikk(mai, mai) as StønadstatistikkSvar.Genererer
        førsteGenerering.aggregatIder.size shouldBe 1
        service.genererStønadstatistikk(førsteGenerering.aggregatIder)

        val førsteSvar = service.hentStønadstatistikk(mai, mai) as StønadstatistikkSvar.Ferdig
        førsteSvar.oppsummering.perioder.single().let {
            it.datagrunnlag shouldBe StønadStatistikkDatagrunnlag.TILGJENGELIG
            it.rader.single().stønadsklassifisering shouldBe StønadsklassifiseringDto.BOR_ALENE
            it.bestandsendringerTilgjengelig shouldBe true
            it.bestandsendringer.single().videreført shouldBe 1
        }
        service.hentStønadstatistikk(mai, mai) shouldBe førsteSvar

        val korrigertMairad = lagStønadstatistikkRad(
            måned = mai,
            sakId = sakId,
            tekniskTid = Tidspunkt.parse("2026-05-20T10:00:00Z"),
            stonadsklassifisering = StønadsklassifiseringDto.BOR_MED_ANDRE_VOKSNE,
        )
        stønadRepo.lagreMånedStatistikk(korrigertMairad)
        stønadRepo.markerMånedGenerert(mai)

        val regenerering = service.hentStønadstatistikk(mai, mai) as StønadstatistikkSvar.Genererer
        regenerering.aggregatIder shouldBe førsteGenerering.aggregatIder
        service.genererStønadstatistikk(regenerering.aggregatIder)

        val regenerertSvar = service.hentStønadstatistikk(mai, mai) as StønadstatistikkSvar.Ferdig
        regenerertSvar.oppsummering.perioder.single().rader.single().stønadsklassifisering shouldBe
            StønadsklassifiseringDto.BOR_MED_ANDRE_VOKSNE
        stønadRepo.hentStatistikkForMåned(mai).toSet() shouldBe setOf(førsteMairad, korrigertMairad)
    }

    private fun aggregatnøkkel(
        fraOgMed: String = "2026-05-01",
        tilOgMed: String = "2026-05-31",
    ) = SakStatistikkVisningsvalg(
        fraOgMed = java.time.LocalDate.parse(fraOgMed),
        tilOgMed = java.time.LocalDate.parse(tilOgMed),
        oppløsning = Statistikkoppløsning.MÅNED,
    )

    private fun lagSakstatistikk(
        behandlingId: UUID,
        status: BehandlingStatus,
        funksjonellTid: String,
        resultat: BehandlingResultat?,
        begrunnelse: String?,
    ) = SakStatistikk(
        funksjonellTid = Tidspunkt.parse(funksjonellTid),
        tekniskTid = Tidspunkt.parse(funksjonellTid).plusUnits(1),
        sakId = UUID.randomUUID(),
        saksnummer = 123L,
        behandlingId = behandlingId,
        aktorId = Fnr.generer(),
        sakYtelse = YtelseType.SUALDER.name,
        sakUtland = "NASJONAL",
        behandlingType = Behandlingstype.REVURDERING.name,
        behandlingMetode = BehandlingMetode.MANUELL,
        mottattTid = Tidspunkt.parse("2026-05-01T08:00:00Z"),
        registrertTid = Tidspunkt.parse("2026-05-01T09:00:00Z"),
        behandlingStatus = status.value,
        behandlingResultat = resultat?.value,
        resultatBegrunnelse = begrunnelse,
        behandlingAarsak = "SØKNAD",
        opprettetAv = "system",
        ansvarligEnhet = "4815",
    )
}
