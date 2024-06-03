package no.nav.su.se.bakover.kontrollsamtale.domain

import arrow.core.left
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.tid.april
import no.nav.su.se.bakover.common.domain.tid.august
import no.nav.su.se.bakover.common.domain.tid.februar
import no.nav.su.se.bakover.common.domain.tid.juni
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.august
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.kontrollsamtale.domain.opprett.KanIkkeOppretteKontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.opprett.OpprettKontrollsamtaleCommand
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.kontrollsamtale.planlagtKontrollsamtale
import org.junit.jupiter.api.Test
import java.util.UUID

internal class Kontrollsamtaler_OpprettTest {

    @Test
    fun `kan ikke opprette kontrollsamtale inneværende måned`() {
        val sakId = UUID.randomUUID()
        val clock = TikkendeKlokke()
        Kontrollsamtaler(sakId, emptyList()).opprettKontrollsamtale(
            command = OpprettKontrollsamtaleCommand(
                sakId = sakId,
                saksbehandler = NavIdentBruker.Saksbehandler("Z999999"),
                innkallingsmåned = januar(2021),
            ),
            clock = clock,
        ) shouldBe KanIkkeOppretteKontrollsamtale.InnkallingsmånedMåVæreEtterNåværendeMåned(januar(2021)).left()
    }

    @Test
    fun `kan ikke opprette kontrollsamtale som er for nært annen kontrollsamtale`() {
        val sakId = UUID.randomUUID()
        val clock = TikkendeKlokke()
        val kontrollsamtaler = Kontrollsamtaler(
            sakId = sakId,
            kontrollsamtaler = listOf(
                planlagtKontrollsamtale(
                    sakId = sakId,
                    innkallingsdato = 1.februar(2021),
                    frist = 28.februar(2021),
                ),
            ),
        )
        kontrollsamtaler.opprettKontrollsamtale(
            command = OpprettKontrollsamtaleCommand(
                sakId = sakId,
                saksbehandler = NavIdentBruker.Saksbehandler("Z999999"),
                innkallingsmåned = februar(2021),
            ),
            clock = clock,
        ) shouldBe KanIkkeOppretteKontrollsamtale.UgyldigInnkallingsmåned(februar(2021)).left()

        kontrollsamtaler.opprettKontrollsamtale(
            command = OpprettKontrollsamtaleCommand(
                sakId = sakId,
                saksbehandler = NavIdentBruker.Saksbehandler("Z999999"),
                innkallingsmåned = mars(2021),
            ),
            clock = clock,
        ) shouldBe KanIkkeOppretteKontrollsamtale.UgyldigInnkallingsmåned(mars(2021)).left()
    }

    @Test
    fun `kan opprette kontrollsamtale to måneder før`() {
        val sakId = UUID.randomUUID()
        val clock = TikkendeKlokke()
        val eksisterendeKontrollsamtale = planlagtKontrollsamtale(
            sakId = sakId,
            innkallingsdato = 1.juni(2021),
            frist = 30.juni(2021),
        )
        val kontrollsamtaler = Kontrollsamtaler(
            sakId = sakId,
            kontrollsamtaler = listOf(eksisterendeKontrollsamtale),
        )
        val (actualKontrollsamtale, actualKontrollsamtaler) = kontrollsamtaler.opprettKontrollsamtale(
            command = OpprettKontrollsamtaleCommand(
                sakId = sakId,
                saksbehandler = NavIdentBruker.Saksbehandler("Z999999"),
                innkallingsmåned = april(2021),
            ),
            clock = clock,
        ).getOrFail()
        val expectedKontrollsamtale = Kontrollsamtale(
            id = actualKontrollsamtale.id,
            opprettet = Tidspunkt.parse("2021-01-01T01:02:05.456789Z"),
            sakId = sakId,
            innkallingsdato = 1.april(2021),
            status = Kontrollsamtalestatus.PLANLAGT_INNKALLING,
            frist = 30.april(2021),
            dokumentId = null,
            journalpostIdKontrollnotat = null,
        )
        actualKontrollsamtale shouldBe expectedKontrollsamtale
        actualKontrollsamtaler.kontrollsamtaler shouldBe listOf(eksisterendeKontrollsamtale, expectedKontrollsamtale)
    }

    @Test
    fun `kan opprette kontrollsamtale to måneder etter`() {
        val sakId = UUID.randomUUID()
        val clock = TikkendeKlokke()
        val eksisterendeKontrollsamtale = planlagtKontrollsamtale(
            sakId = sakId,
            innkallingsdato = 1.juni(2021),
            frist = 30.juni(2021),
        )
        val kontrollsamtaler = Kontrollsamtaler(
            sakId = sakId,
            kontrollsamtaler = listOf(eksisterendeKontrollsamtale),
        )

        val (actualKontrollsamtale, actualKontrollsamtaler) = kontrollsamtaler.opprettKontrollsamtale(
            command = OpprettKontrollsamtaleCommand(
                sakId = sakId,
                saksbehandler = NavIdentBruker.Saksbehandler("Z999999"),
                innkallingsmåned = august(2021),
            ),
            clock = clock,
        ).getOrFail()
        val expectedKontrollsamtale = Kontrollsamtale(
            id = actualKontrollsamtale.id,
            opprettet = Tidspunkt.parse("2021-01-01T01:02:05.456789Z"),
            sakId = sakId,
            innkallingsdato = 1.august(2021),
            status = Kontrollsamtalestatus.PLANLAGT_INNKALLING,
            frist = 31.august(2021),
            dokumentId = null,
            journalpostIdKontrollnotat = null,
        )
        actualKontrollsamtale shouldBe expectedKontrollsamtale
        actualKontrollsamtaler.kontrollsamtaler shouldBe listOf(eksisterendeKontrollsamtale, expectedKontrollsamtale)
    }

    @Test
    fun `kan opprette kontrollsamtale neste måned`() {
        val sakId = UUID.randomUUID()
        val clock = TikkendeKlokke()
        val (actualKontrollsamtale, actualKontrollsamtaler) = Kontrollsamtaler(
            sakId,
            emptyList(),
        ).opprettKontrollsamtale(
            command = OpprettKontrollsamtaleCommand(
                sakId = sakId,
                saksbehandler = NavIdentBruker.Saksbehandler("Z999999"),
                innkallingsmåned = februar(2021),
            ),
            clock = clock,
        ).getOrFail()
        val expectedKontrollsamtale = Kontrollsamtale(
            id = actualKontrollsamtale.id,
            opprettet = Tidspunkt.parse("2021-01-01T01:02:05.456789Z"),
            sakId = sakId,
            innkallingsdato = 1.februar(2021),
            status = Kontrollsamtalestatus.PLANLAGT_INNKALLING,
            frist = 28.februar(2021),
            dokumentId = null,
            journalpostIdKontrollnotat = null,
        )
        actualKontrollsamtale shouldBe expectedKontrollsamtale
        actualKontrollsamtaler.kontrollsamtaler shouldBe listOf(expectedKontrollsamtale)
    }
}
