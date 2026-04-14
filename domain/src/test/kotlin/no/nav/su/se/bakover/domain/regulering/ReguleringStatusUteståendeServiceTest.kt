package no.nav.su.se.bakover.domain.regulering

import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.test.bosituasjonBorMedAndreVoksne
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingAlder
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.utbetaling.oversendtUtbetalingMedKvittering
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import satser.domain.Satskategori
import økonomi.domain.utbetaling.UtbetalingRepo
import økonomi.domain.utbetaling.Utbetalinger
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

internal class ReguleringStatusUteståendeServiceTest {

    private val clock: Clock = Clock.fixed(Instant.parse("2025-06-15T10:15:30Z"), ZoneId.of("Europe/Oslo"))
    private val gammelClock: Clock = Clock.fixed(Instant.parse("2024-06-15T10:15:30Z"), ZoneId.of("Europe/Oslo"))

    @Test
    fun `returnerer ReguleringStatus for uføre og ordinær satskategori`() {
        val saker = opprettTestSaker()

        val sakService = mock<SakService> {
            on { hentSakIdSaksnummerOgFnrForAlleSaker() } doReturn saker.map { it.info() }
            saker.forEach { sak ->
                on { hentSak(sak.id) } doReturn sak.right()
            }
        }

        val utbetalingRepo = mock<UtbetalingRepo> {
            on { hentOversendteUtbetalingerForSakIder(saker.map { it.id }) } doReturn saker.associate { it.id to it.utbetalinger }
        }

        val service = ReguleringStatusUteståendeService(
            sakService = sakService,
            utbetalingRepo = utbetalingRepo,
            satsFactory = satsFactoryTestPåDato(LocalDate.now(clock)),
            clock = clock,
        )

        val result = service.hentStatusSisteGrunnbeløp(2025)

        with(result.sisteGrunnbeløpOgSatser) {
            grunnbeløp shouldBe 130160
            garantipensjonOrdinær shouldBe 224248
            garantipensjonHøy shouldBe 242418
        }
        result.sakerMedUtebetalingIMai shouldBe 6
        result.sakerMedGammelG.size shouldBe 2
        with(result.sakerMedGammelG.single { it.type == Sakstype.UFØRE }) {
            saksnummer shouldBe Saksnummer(1234565)
            benyttetGrunnbeløp shouldBe 124028
            benyttetSatskategori shouldBe Satskategori.HØY
        }
        with(result.sakerMedGammelG.single { it.type == Sakstype.ALDER }) {
            saksnummer shouldBe Saksnummer(1234564)
            benyttetGrunnbeløp shouldBe null
            benyttetSatskategori shouldBe Satskategori.ORDINÆR
            benyttetSats shouldBe 216226.0
        }
    }

    private fun opprettTestSaker(): List<Sak> {
        val periode = Periode.create(1.januar(2025), 31.desember(2025))
        val stønadsperiode = Stønadsperiode.create(periode)

        // Uføre med ordinær satskategori
        val sakUføreOrdinær = iverksattSøknadsbehandlingUføre(
            clock = clock,
            sakInfo = SakInfo(
                sakId = UUID.randomUUID(),
                saksnummer = Saksnummer(12345670),
                fnr = Fnr.generer(),
                type = Sakstype.UFØRE,
            ),
            stønadsperiode = stønadsperiode,
            customGrunnlag = listOf(
                bosituasjonBorMedAndreVoksne(periode = periode),
            ),
            satsPåDato = clock.instant().atZone(ZoneId.systemDefault()).toLocalDate(),
        ).let { (sakUtenUtbetaling, behandling, _) -> sakUtenUtbetaling.leggTilUtbetaling(behandling) }

        // Uføre med høy satskategori
        val sakUføreHøy = iverksattSøknadsbehandlingUføre(
            clock = clock,
            sakInfo = SakInfo(
                sakId = UUID.randomUUID(),
                saksnummer = Saksnummer(12345671),
                fnr = Fnr.generer(),
                type = Sakstype.UFØRE,
            ),
            stønadsperiode = stønadsperiode,
            customGrunnlag = listOf(
                bosituasjongrunnlagEnslig(periode = periode),
            ),
            satsPåDato = clock.instant().atZone(ZoneId.systemDefault()).toLocalDate(),
        ).let { (sakUtenUtbetaling, behandling, _) -> sakUtenUtbetaling.leggTilUtbetaling(behandling) }

        // Alder med ordinær satskategori
        val sakAlderOrdinær = iverksattSøknadsbehandlingAlder(
            clock = clock,
            sakInfo = SakInfo(
                sakId = UUID.randomUUID(),
                saksnummer = Saksnummer(12345672),
                fnr = Fnr.generer(),
                type = Sakstype.ALDER,
            ),
            stønadsperiode = stønadsperiode,
        ).let { (sakUtenUtbetaling, behandling, _) -> sakUtenUtbetaling.leggTilUtbetaling(behandling) }

        // Alder med høy satskategori
        val sakAlderHøy = iverksattSøknadsbehandlingAlder(
            clock = clock,
            sakInfo = SakInfo(
                sakId = UUID.randomUUID(),
                saksnummer = Saksnummer(1234563),
                fnr = Fnr.generer(),
                type = Sakstype.ALDER,
            ),
            stønadsperiode = stønadsperiode,
            customGrunnlag = listOf(bosituasjongrunnlagEnslig(periode = periode)),
            satsPåDato = clock.instant().atZone(ZoneId.systemDefault()).toLocalDate(),
        ).let { (sakUtenUtbetaling, behandling, _) -> sakUtenUtbetaling.leggTilUtbetaling(behandling) }

        val sakAlderGammelSats = iverksattSøknadsbehandlingAlder(
            clock = gammelClock,
            sakInfo = SakInfo(
                sakId = UUID.randomUUID(),
                saksnummer = Saksnummer(1234564),
                fnr = Fnr.generer(),
                type = Sakstype.ALDER,
            ),
            stønadsperiode = stønadsperiode,
            satsPåDato = gammelClock.instant().atZone(ZoneId.systemDefault()).toLocalDate(),
        ).let { (sakUtenUtbetaling, behandling, _) -> sakUtenUtbetaling.leggTilUtbetaling(behandling) }

        val sakUføreGammelG = iverksattSøknadsbehandlingUføre(
            clock = gammelClock,
            sakInfo = SakInfo(
                sakId = UUID.randomUUID(),
                saksnummer = Saksnummer(1234565),
                fnr = Fnr.generer(),
                type = Sakstype.UFØRE,
            ),
            stønadsperiode = stønadsperiode,
            customGrunnlag = listOf(
                bosituasjongrunnlagEnslig(periode = periode),
            ),
            satsPåDato = gammelClock.instant().atZone(ZoneId.systemDefault()).toLocalDate(),
        ).let { (sakUtenUtbetaling, behandling, _) -> sakUtenUtbetaling.leggTilUtbetaling(behandling) }

        return listOf(sakUføreOrdinær, sakUføreHøy, sakAlderOrdinær, sakAlderHøy, sakAlderGammelSats, sakUføreGammelG)
    }

    private fun Sak.leggTilUtbetaling(behandling: IverksattSøknadsbehandling) = copy(
        utbetalinger = Utbetalinger(
            oversendtUtbetalingMedKvittering(
                periode = behandling.periode,
                beregning = behandling.beregning!!,
            ),
        ),
    )
}
