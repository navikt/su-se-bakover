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
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkAggregatnøkkel
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkAggregatstatus
import no.nav.su.se.bakover.domain.statistikk.Statistikkoppløsning
import no.nav.su.se.bakover.domain.statistikk.StønadStatistikkAggregertRad
import no.nav.su.se.bakover.domain.statistikk.StønadStatistikkBestandsendringRad
import no.nav.su.se.bakover.service.statistikk.SakstatistikkSvar
import no.nav.su.se.bakover.service.statistikk.StatistikkVisningServiceImpl
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
        val førsteNøkkel = aggregatnøkkel(fraOgMed = "2026-01-01", tilOgMed = "2026-01-31")
        val andreNøkkel = aggregatnøkkel(fraOgMed = "2026-02-01", tilOgMed = "2026-02-28")

        val første = repo.hentEllerOpprettSakstatistikkAggregat(førsteNøkkel, versjon = 1)
        val samme = repo.hentEllerOpprettSakstatistikkAggregat(førsteNøkkel, versjon = 2)
        val andre = repo.hentEllerOpprettSakstatistikkAggregat(andreNøkkel, versjon = 1)

        første.status shouldBe SakStatistikkAggregatstatus.VENTER
        samme.id shouldBe første.id
        samme.versjon shouldBe 1

        val claimetAndre = repo.hentNesteSakstatistikkAggregatTilGenerering(bareId = andre.id)!!
        claimetAndre.id shouldBe andre.id
        claimetAndre.status shouldBe SakStatistikkAggregatstatus.PÅGÅR
        claimetAndre.startet shouldNotBe null

        repo.hentNesteSakstatistikkAggregatTilGenerering(bareId = andre.id) shouldBe null

        val claimetFørste = repo.hentNesteSakstatistikkAggregatTilGenerering()!!
        claimetFørste.id shouldBe første.id
        repo.ferdigstillSakstatistikkAggregat(
            id = første.id,
            payload = """{"antall":1}""",
            maksSekvensId = 17L,
            versjon = 3,
        )

        repo.hentEllerOpprettSakstatistikkAggregat(førsteNøkkel, versjon = 3).let {
            it.status shouldBe SakStatistikkAggregatstatus.FERDIG
            it.versjon shouldBe 3
            it.maksSekvensId shouldBe 17L
            it.payload shouldBe """{"antall": 1}"""
            it.ferdig shouldNotBe null
        }

        repo.markerSakstatistikkAggregatForRegenerering(første.id, versjon = 4)
        repo.hentEllerOpprettSakstatistikkAggregat(førsteNøkkel, versjon = 4).let {
            it.status shouldBe SakStatistikkAggregatstatus.VENTER
            it.versjon shouldBe 4
            it.payload shouldBe null
            it.startet shouldBe null
            it.ferdig shouldBe null
        }

        repo.hentNesteSakstatistikkAggregatTilGenerering(bareId = første.id)
        repo.markerSakstatistikkAggregatFeilet(første.id, "forventet feil")
        repo.hentEllerOpprettSakstatistikkAggregat(førsteNøkkel, versjon = 4).status shouldBe
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
        val maksEtterFørste = repo.hentMaksSakstatistikkSekvensId(aggregatnøkkel())!!
        testDataHelper.sakStatistikkRepo.lagreSakStatistikk(andre)

        repo.hentSakstatistikkgrunnlag(aggregatnøkkel(), maksEtterFørste).single().let {
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

        repo.hentSakstatistikkgrunnlag(aggregatnøkkel(), maksSekvensId = null).let {
            it.map { rad -> rad.behandlingStatus } shouldContainExactly
                listOf(BehandlingStatus.Registrert.value, BehandlingStatus.Iverksatt.value)
            it.last().behandlingResultat shouldBe BehandlingResultat.Opphør.value
            it.last().resultatBegrunnelse shouldBe Opphørsgrunn.FOR_HØY_INNTEKT.name
        }
    }

    @Test
    fun `gjqenbruker ferdig sakstatistikk til en ny relevant rad krever regenerering`() {
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
        service.genererVentendeSakstatistikk(bareId = førsteGenerering.aggregatId, maksAntall = 1)
        val førstePayload = (service.hentSakstatistikk(nøkkel) as SakstatistikkSvar.Ferdig).payload

        service.hentSakstatistikk(nøkkel) shouldBe SakstatistikkSvar.Ferdig(førstePayload)
        repo.hentNesteSakstatistikkAggregatTilGenerering(bareId = førsteGenerering.aggregatId) shouldBe null

        testDataHelper.sakStatistikkRepo.lagreSakStatistikk(
            lagSakstatistikk(
                behandlingId = UUID.randomUUID(),
                status = BehandlingStatus.Iverksatt,
                funksjonellTid = "2026-06-01T00:00:00Z",
                resultat = BehandlingResultat.Innvilget,
                begrunnelse = null,
            ),
        )

        service.hentSakstatistikk(nøkkel) shouldBe SakstatistikkSvar.Ferdig(førstePayload)

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
        regenerering.aggregatId shouldBe førsteGenerering.aggregatId
        service.genererVentendeSakstatistikk(bareId = regenerering.aggregatId, maksAntall = 1)

        val regenerertPayload = (service.hentSakstatistikk(nøkkel) as SakstatistikkSvar.Ferdig).payload
        regenerertPayload shouldNotBe førstePayload
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

        repo.hentStønadstatistikk(mai, mai) shouldContainExactly listOf(
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
        repo.hentStønadstatistikkBestandsendringer(mai, mai) shouldContainExactly listOf(
            StønadStatistikkBestandsendringRad(
                måned = mai,
                stønadstype = StønadstatistikkDto.Stønadstype.SU_ALDER,
                nye = 1,
                videreført = 1,
                utgått = 1,
                endretStønadsklassifisering = 1,
            ),
        )
        repo.hentGenererteStønadstatistikkmåneder(april, mai) shouldBe setOf(april, mai)
    }

    private fun aggregatnøkkel(
        fraOgMed: String = "2026-05-01",
        tilOgMed: String = "2026-05-31",
    ) = SakStatistikkAggregatnøkkel(
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
