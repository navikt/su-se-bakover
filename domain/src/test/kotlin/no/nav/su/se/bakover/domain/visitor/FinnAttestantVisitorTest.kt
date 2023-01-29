package no.nav.su.se.bakover.domain.visitor

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.beregnetRevurdering
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.simulertRevurdering
import no.nav.su.se.bakover.test.søknadsbehandlingBeregnetAvslag
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattAvslagUtenBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingSimulert
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringAvslagMedBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingUnderkjentInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertAvslag
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import no.nav.su.se.bakover.test.tikkendeFixedClock
import org.junit.jupiter.api.Test

internal class FinnAttestantVisitorTest {

    @Test
    fun `finner attestant for både søknadsbehandlinger og revurderinger`() {
        val clock = tikkendeFixedClock()
        FinnAttestantVisitor().let {
            søknadsbehandlingVilkårsvurdertUavklart(
                clock = clock,
            ).second.accept(it)
            it.attestant shouldBe null
        }
        FinnAttestantVisitor().let {
            opprettetRevurdering(
                clock = clock,
            ).second.accept(it)
            it.attestant shouldBe null
        }
        FinnAttestantVisitor().let {
            søknadsbehandlingVilkårsvurdertInnvilget(
                clock = clock,
            ).second.accept(it)
            it.attestant shouldBe null
        }

        FinnAttestantVisitor().let {
            søknadsbehandlingVilkårsvurdertAvslag(
                clock = clock,
            ).second.accept(it)
            it.attestant shouldBe null
        }

        FinnAttestantVisitor().let {
            beregnetRevurdering(
                clock = clock,
            ).second.accept(it)
            it.attestant shouldBe null
        }

        FinnAttestantVisitor().let {
            søknadsbehandlingBeregnetAvslag(
                clock = clock,
            ).second.accept(it)
            it.attestant shouldBe null
        }

        FinnAttestantVisitor().let {
            søknadsbehandlingVilkårsvurdertInnvilget(
                clock = clock,
            ).second.accept(it)
            it.attestant shouldBe null
        }

        FinnAttestantVisitor().let {
            søknadsbehandlingSimulert(
                clock = clock,
            ).second.accept(it)
            it.attestant shouldBe null
        }

        FinnAttestantVisitor().let {
            simulertRevurdering(
                clock = clock,
            ).second.accept(it)
            it.attestant shouldBe null
        }

        FinnAttestantVisitor().let {
            søknadsbehandlingUnderkjentInnvilget(
                clock = clock,
            ).second.accept(it)
            it.attestant shouldBe attestant
        }

        FinnAttestantVisitor().let {
            søknadsbehandlingTilAttesteringInnvilget(
                clock = clock,
            ).second.accept(it)
            it.attestant shouldBe null
        }

        FinnAttestantVisitor().let {
            søknadsbehandlingTilAttesteringAvslagMedBeregning(
                clock = clock,
            ).second.accept(it)
            it.attestant shouldBe null
        }

        FinnAttestantVisitor().let {
            søknadsbehandlingTilAttesteringInnvilget(
                clock = clock,
            ).second
                .tilUnderkjent(
                    Attestering.Underkjent(
                        attestant = attestant,
                        grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                        kommentar = "",
                        opprettet = fixedTidspunkt,
                    ),
                ).accept(it)
            it.attestant shouldBe attestant
        }

        FinnAttestantVisitor().let {
            søknadsbehandlingIverksattInnvilget(
                clock = clock,
            ).second.accept(it)
            it.attestant shouldBe attestant
        }

        FinnAttestantVisitor().let {
            søknadsbehandlingIverksattAvslagUtenBeregning(
                clock = clock,
            ).second.accept(it)
            it.attestant shouldBe attestant
        }
    }
}
