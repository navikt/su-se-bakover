package no.nav.su.se.bakover.service.statistikk

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.statistikk.BehandlingMetode
import no.nav.su.se.bakover.common.domain.statistikk.SakStatistikk
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkRepo
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.generer
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import java.math.BigInteger
import java.util.UUID

internal class SakStatistikkBigQueryServiceTest {
    @Test
    fun `in-memory-gateway brukes uten ekstern BigQuery`() {
        val sekvensId = 1L
        val sekvensIder = setOf(sekvensId)
        val rad = lagSakStatistikk(sekvensId)
        val gateway = SakStatistikkBigQueryGatewayInMemory()

        gateway.hentAntallRaderPerSekvensId(sekvensIder) shouldBe emptyMap()

        gateway.writeToBigQuery(listOf(rad))
        gateway.hentAntallRaderPerSekvensId(sekvensIder) shouldBe mapOf(sekvensId to 1L)
        gateway.verifyExactlyOnce(sekvensIder)

        gateway.deleteExactlyOrVerifyMissing(sekvensIder)
        gateway.hentAntallRaderPerSekvensId(sekvensIder) shouldBe emptyMap()
    }

    @Test
    fun `samler alle Postgres-avvik før BigQuery endres`() {
        val eksisterendeSekvensId = 1L
        val manglendeSekvensId = 2L
        val uventetSekvensId = 3L
        val sekvensIder = listOf(eksisterendeSekvensId, manglendeSekvensId)
        val unikeSekvensIder = sekvensIder.toSet()
        val manglendeSekvensIder = setOf(manglendeSekvensId)
        val ikkeUnikeSekvensIder = setOf(eksisterendeSekvensId)
        val uventedeSekvensIder = setOf(uventetSekvensId)
        val repo = mock<SakStatistikkRepo> {
            on { hentSakStatistikk(unikeSekvensIder) } doReturn listOf(
                lagSakStatistikk(eksisterendeSekvensId),
                lagSakStatistikk(eksisterendeSekvensId),
                lagSakStatistikk(uventetSekvensId),
            )
        }
        val bigQueryGateway = mock<SakStatistikkBigQueryGateway>()
        val service = SakStatistikkBigQueryServiceImpl(repo, bigQueryGateway)
        val forventetFeil = KunneIkkeErstatteSakStatistikk.AvvikISakStatistikk(
            manglendeSekvensIder = manglendeSekvensIder,
            ikkeUnikeSekvensIder = ikkeUnikeSekvensIder,
            uventedeSekvensIder = uventedeSekvensIder,
        ).left()

        service.erstattSakStatistikk(sekvensIder) shouldBe forventetFeil

        verifyNoInteractions(bigQueryGateway)
    }

    @Test
    fun `avviser duplikate sekvens-ID-er før Postgres og BigQuery kalles`() {
        val sekvensId = 1L
        val sekvensIder = listOf(sekvensId, sekvensId)
        val repo = mock<SakStatistikkRepo>()
        val bigQueryGateway = mock<SakStatistikkBigQueryGateway>()
        val service = SakStatistikkBigQueryServiceImpl(repo, bigQueryGateway)
        val forventetFeil = KunneIkkeErstatteSakStatistikk.DuplikateSekvensIder(setOf(sekvensId)).left()

        service.erstattSakStatistikk(sekvensIder) shouldBe forventetFeil

        verifyNoInteractions(repo, bigQueryGateway)
    }

    @Test
    fun `sletter laster opp og etterkontrollerer nøyaktig de validerte radene`() {
        val førsteSekvensId = 1L
        val andreSekvensId = 2L
        val sekvensIder = listOf(førsteSekvensId, andreSekvensId)
        val unikeSekvensIder = sekvensIder.toSet()
        val rad1 = lagSakStatistikk(førsteSekvensId)
        val rad2 = lagSakStatistikk(andreSekvensId)
        val rader = listOf(rad1, rad2)
        val repo = mock<SakStatistikkRepo> {
            on { hentSakStatistikk(unikeSekvensIder) } doReturn rader
        }
        val bigQueryRaderPerSekvensId = sekvensIder.associateWith { 1L }
        val bigQueryGateway = mock<SakStatistikkBigQueryGateway> {
            on { hentAntallRaderPerSekvensId(unikeSekvensIder) } doReturn bigQueryRaderPerSekvensId
        }
        val service = SakStatistikkBigQueryServiceImpl(repo, bigQueryGateway)
        val forventetResultat = ErstattetSakStatistikk(rader.size).right()

        service.erstattSakStatistikk(sekvensIder) shouldBe forventetResultat

        inOrder(bigQueryGateway) {
            verify(bigQueryGateway).hentAntallRaderPerSekvensId(unikeSekvensIder)
            verify(bigQueryGateway).deleteExactlyOrVerifyMissing(unikeSekvensIder)
            verify(bigQueryGateway).writeToBigQuery(rader)
            verify(bigQueryGateway).verifyExactlyOnce(unikeSekvensIder)
        }
    }

    @Test
    fun `erstatter ikke når BigQuery mangler en ID og har duplikat av en annen`() {
        val førsteSekvensId = 636L
        val andreSekvensId = 637L
        val sekvensIder = listOf(førsteSekvensId, andreSekvensId)
        val unikeSekvensIder = sekvensIder.toSet()
        val rader = sekvensIder.map { lagSakStatistikk(it) }
        val bigQueryRaderPerSekvensId = mapOf(førsteSekvensId to 2L)
        val repo = mock<SakStatistikkRepo> {
            on { hentSakStatistikk(unikeSekvensIder) } doReturn rader
        }
        val bigQueryGateway = mock<SakStatistikkBigQueryGateway> {
            on { hentAntallRaderPerSekvensId(unikeSekvensIder) } doReturn bigQueryRaderPerSekvensId
        }
        val service = SakStatistikkBigQueryServiceImpl(repo, bigQueryGateway)
        val forventetFeil = KunneIkkeErstatteSakStatistikk.UgyldigTilstandIBigQuery(
            antallForespurte = sekvensIder.size,
            antallRaderIBigQuery = bigQueryRaderPerSekvensId.values.sum(),
            manglendeSekvensIder = setOf(andreSekvensId),
            ikkeUnikeSekvensIder = setOf(førsteSekvensId),
            uventedeSekvensIder = emptySet(),
        ).left()

        service.erstattSakStatistikk(sekvensIder) shouldBe forventetFeil

        verify(bigQueryGateway).hentAntallRaderPerSekvensId(unikeSekvensIder)
        verifyNoMoreInteractions(bigQueryGateway)
    }

    @Test
    fun `forhåndsvisning avviser delvise treff i BigQuery`() {
        val førsteSekvensId = 636L
        val andreSekvensId = 637L
        val tredjeSekvensId = 638L
        val sekvensIder = listOf(førsteSekvensId, andreSekvensId, tredjeSekvensId)
        val unikeSekvensIder = sekvensIder.toSet()
        val rader = sekvensIder.map { lagSakStatistikk(it) }
        val bigQueryRaderPerSekvensId = mapOf(
            førsteSekvensId to 1L,
            andreSekvensId to 1L,
        )
        val repo = mock<SakStatistikkRepo> {
            on { hentSakStatistikk(unikeSekvensIder) } doReturn rader
        }
        val bigQueryGateway = mock<SakStatistikkBigQueryGateway> {
            on { hentAntallRaderPerSekvensId(unikeSekvensIder) } doReturn bigQueryRaderPerSekvensId
        }
        val service = SakStatistikkBigQueryServiceImpl(repo, bigQueryGateway)
        val forventetResultat = ForhåndsvisErstattSakStatistikk(
            kanErstattes = false,
            antallForespurte = sekvensIder.size,
            antallRaderISakStatistikk = rader.size,
            antallRaderIBigQuery = bigQueryRaderPerSekvensId.values.sum(),
            manglendeISakStatistikk = emptyList(),
            ikkeUnikeISakStatistikk = emptyList(),
            manglendeIBigQuery = listOf(tredjeSekvensId),
            ikkeUnikeIBigQuery = emptyList(),
        ).right()

        service.forhåndsvisErstattSakStatistikk(sekvensIder) shouldBe forventetResultat

        verify(bigQueryGateway).hentAntallRaderPerSekvensId(unikeSekvensIder)
        verifyNoMoreInteractions(bigQueryGateway)
    }

    @Test
    fun `forhåndsvisning returnerer manglende og ikke-unike Postgres-ID-er i requestrekkefølge`() {
        val manglendeSekvensId = 638L
        val duplisertSekvensId = 637L
        val unikSekvensId = 636L
        val sekvensIder = listOf(manglendeSekvensId, duplisertSekvensId, unikSekvensId)
        val unikeSekvensIder = sekvensIder.toSet()
        val rader = listOf(
            lagSakStatistikk(duplisertSekvensId),
            lagSakStatistikk(duplisertSekvensId),
            lagSakStatistikk(unikSekvensId),
        )
        val repo = mock<SakStatistikkRepo> {
            on { hentSakStatistikk(unikeSekvensIder) } doReturn rader
        }
        val bigQueryGateway = mock<SakStatistikkBigQueryGateway>()
        val service = SakStatistikkBigQueryServiceImpl(repo, bigQueryGateway)
        val forventetResultat = ForhåndsvisErstattSakStatistikk(
            kanErstattes = false,
            antallForespurte = sekvensIder.size,
            antallRaderISakStatistikk = rader.size,
            antallRaderIBigQuery = null,
            manglendeISakStatistikk = listOf(manglendeSekvensId),
            ikkeUnikeISakStatistikk = listOf(duplisertSekvensId),
            manglendeIBigQuery = null,
            ikkeUnikeIBigQuery = null,
        ).right()

        service.forhåndsvisErstattSakStatistikk(sekvensIder) shouldBe forventetResultat

        verifyNoInteractions(bigQueryGateway)
    }

    private fun lagSakStatistikk(sekvensId: Long) = SakStatistikk(
        funksjonellTid = Tidspunkt.now(fixedClock),
        tekniskTid = Tidspunkt.now(fixedClock),
        sakId = UUID.randomUUID(),
        saksnummer = 123L,
        behandlingId = UUID.randomUUID(),
        aktorId = Fnr.generer(),
        sakYtelse = "SU_ALDER",
        behandlingType = "SØKNAD",
        behandlingMetode = BehandlingMetode.MANUELL,
        mottattTid = Tidspunkt.now(fixedClock),
        registrertTid = Tidspunkt.now(fixedClock),
        behandlingStatus = "REGISTRERT",
        opprettetAv = "opprettet_av",
        sekvensId = BigInteger.valueOf(sekvensId),
    )
}
