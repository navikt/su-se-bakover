package no.nav.su.se.bakover.kontrollsamtale.domain.opprett

import arrow.core.left
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.tid.februar
import no.nav.su.se.bakover.common.domain.tid.juli
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.common.tid.periode.oktober
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtaler
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.kontrollsamtale.planlagtKontrollsamtale
import no.nav.su.se.bakover.test.søknadsbehandlingBeregnetInnvilget
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test

internal class SakOpprettKontrollsamtaleTest {
    @Test
    fun `Kan ikke opprette kontrollsamtale uten en stønadsperiode`() {
        val clock = TikkendeKlokke()
        val (sak, _) = søknadsbehandlingBeregnetInnvilget()
        sak.opprettKontrollsamtale(
            command = OpprettKontrollsamtaleCommand(
                sakId = sak.id,
                saksbehandler = NavIdentBruker.Saksbehandler("Z999999"),
                innkallingsmåned = januar(2021),
            ),
            clock = clock,
            eksisterendeKontrollsamtalerForSak = Kontrollsamtaler(sak.id, emptyList()),
        ) shouldBe KanIkkeOppretteKontrollsamtale.IngenStønadsperiode.left()
    }

    @Test
    fun `Innkallingsmåned kan ikke være etter stønadsperiode`() {
        val clock = TikkendeKlokke()
        val (sak, søknadsbehandling) = vedtakSøknadsbehandlingIverksattInnvilget()
        sak.opprettKontrollsamtale(
            command = OpprettKontrollsamtaleCommand(
                sakId = sak.id,
                saksbehandler = NavIdentBruker.Saksbehandler("Z999999"),
                innkallingsmåned = januar(2022),
            ),
            clock = clock,
            eksisterendeKontrollsamtalerForSak = Kontrollsamtaler(sak.id, emptyList()),
        ) shouldBe KanIkkeOppretteKontrollsamtale.InnkallingsmånedUtenforStønadsperiode(
            januar(2022),
            søknadsbehandling.periode.tilPerioder(),
        ).left()
    }

    @Test
    fun `Innkallingsmåned kan ikke være før stønadsperiode`() {
        val clock = TikkendeKlokke()
        val (sak, søknadsbehandling) = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(mars(2021)..februar(2022)),
        )
        sak.opprettKontrollsamtale(
            command = OpprettKontrollsamtaleCommand(
                sakId = sak.id,
                saksbehandler = NavIdentBruker.Saksbehandler("Z999999"),
                innkallingsmåned = februar(2021),
            ),
            clock = clock,
            eksisterendeKontrollsamtalerForSak = Kontrollsamtaler(sak.id, emptyList()),
        ) shouldBe KanIkkeOppretteKontrollsamtale.InnkallingsmånedUtenforStønadsperiode(
            februar(2021),
            søknadsbehandling.periode.tilPerioder(),
        ).left()
    }

    @Test
    fun `maks antall planlagte stønadsperiode`() {
        val clock = TikkendeKlokke()
        val (sak, _) = vedtakSøknadsbehandlingIverksattInnvilget()

        sak.opprettKontrollsamtale(
            command = OpprettKontrollsamtaleCommand(
                sakId = sak.id,
                saksbehandler = NavIdentBruker.Saksbehandler("Z999999"),
                innkallingsmåned = oktober(2021),
            ),
            clock = clock,
            eksisterendeKontrollsamtalerForSak = Kontrollsamtaler(
                sak.id,
                listOf(
                    planlagtKontrollsamtale(
                        sakId = sak.id,
                        innkallingsdato = 1.februar(2021),
                        frist = 28.februar(2021),
                    ),
                    planlagtKontrollsamtale(
                        sakId = sak.id,
                        innkallingsdato = 1.mai(2021),
                        frist = 31.mai(2021),
                    ),
                    planlagtKontrollsamtale(
                        sakId = sak.id,
                        innkallingsdato = 1.juli(2021),
                        frist = 31.juli(2021),
                    ),
                ),
            ),
        ) shouldBe KanIkkeOppretteKontrollsamtale.MaksAntallPlanlagteKontrollsamtaler(
            antallPlanlagteKontrollsamtaler = 3,
        ).left()
    }
}
