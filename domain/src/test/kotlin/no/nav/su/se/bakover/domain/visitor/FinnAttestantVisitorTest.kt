package no.nav.su.se.bakover.domain.visitor

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.beregnetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
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
import org.junit.jupiter.api.Test

internal class FinnAttestantVisitorTest {

    @Test
    fun `finner attestant for både søknadsbehandlinger og revurderinger`() {
        FinnAttestantVisitor().let {
            søknadsbehandlingVilkårsvurdertUavklart().second.accept(it)
            it.attestant shouldBe null
        }
        FinnAttestantVisitor().let {
            opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak().second.accept(it)
            it.attestant shouldBe null
        }
        FinnAttestantVisitor().let {
            søknadsbehandlingVilkårsvurdertInnvilget().second.accept(it)
            it.attestant shouldBe null
        }

        FinnAttestantVisitor().let {
            søknadsbehandlingVilkårsvurdertAvslag().second.accept(it)
            it.attestant shouldBe null
        }

        FinnAttestantVisitor().let {
            beregnetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().second.accept(it)
            it.attestant shouldBe null
        }

        FinnAttestantVisitor().let {
            søknadsbehandlingBeregnetAvslag().second.accept(it)
            it.attestant shouldBe null
        }

        FinnAttestantVisitor().let {
            søknadsbehandlingVilkårsvurdertInnvilget().second.accept(it)
            it.attestant shouldBe null
        }

        FinnAttestantVisitor().let {
            søknadsbehandlingSimulert().second.accept(it)
            it.attestant shouldBe null
        }

        FinnAttestantVisitor().let {
            simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().second.accept(it)
            it.attestant shouldBe null
        }

        FinnAttestantVisitor().let {
            søknadsbehandlingUnderkjentInnvilget().second.accept(it)
            it.attestant shouldBe attestant
        }

        FinnAttestantVisitor().let {
            søknadsbehandlingTilAttesteringInnvilget().second.accept(it)
            it.attestant shouldBe null
        }

        FinnAttestantVisitor().let {
            søknadsbehandlingTilAttesteringAvslagMedBeregning().second.accept(it)
            it.attestant shouldBe null
        }

        FinnAttestantVisitor().let {
            søknadsbehandlingTilAttesteringInnvilget().second
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
            søknadsbehandlingIverksattInnvilget().second.accept(it)
            it.attestant shouldBe attestant
        }

        FinnAttestantVisitor().let {
            søknadsbehandlingIverksattAvslagUtenBeregning().second.accept(it)
            it.attestant shouldBe attestant
        }
    }
}
