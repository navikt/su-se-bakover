package no.nav.su.se.bakover.kontrollsamtale.domain

import arrow.core.left
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.tid.april
import no.nav.su.se.bakover.common.domain.tid.august
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.endOfMonth
import no.nav.su.se.bakover.common.domain.tid.februar
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.juli
import no.nav.su.se.bakover.common.domain.tid.juni
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.domain.tid.mars
import no.nav.su.se.bakover.common.domain.tid.november
import no.nav.su.se.bakover.common.domain.tid.oktober
import no.nav.su.se.bakover.common.domain.tid.september
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.august
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.juli
import no.nav.su.se.bakover.common.tid.periode.juni
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.common.tid.periode.november
import no.nav.su.se.bakover.common.tid.periode.oktober
import no.nav.su.se.bakover.common.tid.periode.september
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import org.junit.jupiter.api.Test
import java.util.UUID

class KontrollsamtaleTest {

    @Test
    fun `annullering av en planlagt kontrollsamtale er mulig`() {
        val kontrollsamtale = Kontrollsamtale.opprettNyKontrollsamtale(
            sakId = UUID.randomUUID(),
            innkallingsdato = 1.januar(2022),
            clock = fixedClock,
        )
        kontrollsamtale.annuller() shouldBeRight kontrollsamtale.copy(status = Kontrollsamtalestatus.ANNULLERT)
    }

    @Test
    fun `endre innkallingsdato på kontrollsamtale skal også endre frist`() {
        val innkallingsdato = 1.februar(2022)
        val kontrollsamtale = Kontrollsamtale.opprettNyKontrollsamtale(
            sakId = UUID.randomUUID(),
            innkallingsdato = 1.januar(2022),
            clock = fixedClock,
        )
        kontrollsamtale.endreDato(innkallingsdato).getOrFail() shouldBe kontrollsamtale.copy(
            innkallingsdato = innkallingsdato,
            frist = innkallingsdato.endOfMonth(),
        )
    }

    @Test
    fun `endre innkallingsdato - må være første i måned`() {
        val innkallingsdato = 15.februar(2022)
        val kontrollsamtale = Kontrollsamtale.opprettNyKontrollsamtale(
            sakId = UUID.randomUUID(),
            innkallingsdato = 1.januar(2022),
            clock = fixedClock,
        )
        kontrollsamtale.endreDato(innkallingsdato) shouldBe Kontrollsamtale.KunneIkkeEndreDato.DatoErIkkeFørsteIMåned.left()
    }

    @Test
    fun `frist for alle måneder er slutten av måneden, bortsett fra utløp i november`() {
        val kontrollsamtale = Kontrollsamtale.opprettNyKontrollsamtale(
            sakId = UUID.randomUUID(),
            innkallingsdato = 1.januar(2022),
            clock = fixedClock,
        )
        (januar(2022)..desember(2022)).måneder().map { måned ->
            måned to kontrollsamtale.endreDato(måned.fraOgMed).getOrFail().let {
                it.innkallingsdato to it.frist
            }
        } shouldBe listOf(
            januar(2022) to (1.januar(2022) to 31.januar(2022)),
            februar(2022) to (1.februar(2022) to 28.februar(2022)),
            mars(2022) to (1.mars(2022) to 31.mars(2022)),
            april(2022) to (1.april(2022) to 30.april(2022)),
            mai(2022) to (1.mai(2022) to 31.mai(2022)),
            juni(2022) to (1.juni(2022) to 30.juni(2022)),
            juli(2022) to (1.juli(2022) to 31.juli(2022)),
            august(2022) to (1.august(2022) to 31.august(2022)),
            september(2022) to (1.september(2022) to 30.september(2022)),
            oktober(2022) to (1.oktober(2022) to 31.oktober(2022)),
            november(2022) to (1.november(2022) to 25.november(2022)),
            desember(2022) to (1.desember(2022) to 31.desember(2022)),
        )
    }
}
