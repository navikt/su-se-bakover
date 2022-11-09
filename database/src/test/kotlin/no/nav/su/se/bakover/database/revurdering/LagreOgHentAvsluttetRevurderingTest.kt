package no.nav.su.se.bakover.database.revurdering

import arrow.core.getOrHandle
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.revurdering.AvsluttetRevurdering
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test

internal class LagreOgHentAvsluttetRevurderingTest {

    @Test
    fun `opprettet`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo

            val (_, revurdering) = testDataHelper.persisterRevurderingOpprettet(
                sakOgVedtak = testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().let { it.first to it.second },
            )

            val avsluttetRevurdering = AvsluttetRevurdering.tryCreate(
                underliggendeRevurdering = revurdering,
                begrunnelse = "avslutter denne revurderingen",
                brevvalg = null,
                tidspunktAvsluttet = fixedTidspunkt,
            ).getOrHandle { fail("$it") }

            repo.lagre(avsluttetRevurdering)
            repo.hent(avsluttetRevurdering.id) shouldBe avsluttetRevurdering.copy(
                underliggendeRevurdering = revurdering.copy(
                    avkorting = AvkortingVedRevurdering.Uhåndtert.KanIkkeHåndtere(
                        uhåndtert = AvkortingVedRevurdering.Uhåndtert.IngenUtestående,
                    ),
                ),
            )
        }
    }

    @Test
    fun `beregnet innvilget`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo

            val (_, revurdering) = testDataHelper.persisterBeregnetRevurdering()

            val avsluttetRevurdering = AvsluttetRevurdering.tryCreate(
                underliggendeRevurdering = revurdering,
                begrunnelse = "avslutter denne revurderingen",
                brevvalg = null,
                tidspunktAvsluttet = fixedTidspunkt,
            ).getOrHandle { fail("$it") }

            repo.lagre(avsluttetRevurdering)
            repo.hent(avsluttetRevurdering.id) shouldBe avsluttetRevurdering.copy(
                underliggendeRevurdering = (revurdering as BeregnetRevurdering.Innvilget).copy(
                    avkorting = AvkortingVedRevurdering.DelvisHåndtert.KanIkkeHåndtere(
                        delvisHåndtert = AvkortingVedRevurdering.DelvisHåndtert.IngenUtestående,
                    ),
                ),
            )
        }
    }

    @Test
    fun `beregnet opphørt`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo

            val (_, revurdering) = testDataHelper.persisterRevurderingBeregnetOpphørt()
            repo.lagre(revurdering)

            val avsluttetRevurdering = AvsluttetRevurdering.tryCreate(
                underliggendeRevurdering = revurdering,
                begrunnelse = "avslutter denne revurderingen",
                brevvalg = null,
                tidspunktAvsluttet = fixedTidspunkt,
            ).getOrHandle { fail("$it") }

            repo.lagre(avsluttetRevurdering)
            repo.hent(avsluttetRevurdering.id) shouldBe avsluttetRevurdering.copy(
                underliggendeRevurdering = revurdering.copy(
                    avkorting = AvkortingVedRevurdering.DelvisHåndtert.KanIkkeHåndtere(
                        delvisHåndtert = AvkortingVedRevurdering.DelvisHåndtert.IngenUtestående,
                    ),
                ),
            )
        }
    }

    @Test
    fun `simulert innvilget`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo

            val (_, revurdering) = testDataHelper.persisterRevurderingSimulertInnvilget()

            val avsluttetRevurdering = AvsluttetRevurdering.tryCreate(
                underliggendeRevurdering = revurdering,
                begrunnelse = "avslutter denne revurderingen",
                brevvalg = null,
                tidspunktAvsluttet = fixedTidspunkt,
            ).getOrHandle { fail("$it") }

            repo.lagre(avsluttetRevurdering)
            repo.hent(avsluttetRevurdering.id) shouldBe avsluttetRevurdering.copy(
                underliggendeRevurdering = revurdering.copy(
                    avkorting = AvkortingVedRevurdering.Håndtert.KanIkkeHåndteres(
                        håndtert = AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
                    ),
                ),
            )
        }
    }

    @Test
    fun `simulert opphørt`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo

            val (_, revurdering) = testDataHelper.persisterRevurderingSimulertInnvilget()

            val avsluttetRevurdering = AvsluttetRevurdering.tryCreate(
                underliggendeRevurdering = revurdering,
                begrunnelse = "avslutter denne revurderingen",
                brevvalg = null,
                tidspunktAvsluttet = fixedTidspunkt,
            ).getOrHandle { fail("$it") }

            repo.lagre(avsluttetRevurdering)
            repo.hent(avsluttetRevurdering.id) shouldBe avsluttetRevurdering.copy(
                underliggendeRevurdering = revurdering.copy(
                    avkorting = AvkortingVedRevurdering.Håndtert.KanIkkeHåndteres(
                        håndtert = AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
                    ),
                ),
            )
        }
    }

    @Test
    fun `til attestering - innvilget`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo

            val (_, revurdering) = testDataHelper.persisterRevurderingUnderkjentInnvilget()

            val avsluttetRevurdering = AvsluttetRevurdering.tryCreate(
                underliggendeRevurdering = revurdering,
                begrunnelse = "avslutter denne revurderingen",
                brevvalg = null,
                tidspunktAvsluttet = fixedTidspunkt,
            ).getOrHandle { fail("$it") }

            repo.lagre(avsluttetRevurdering)
            repo.hent(avsluttetRevurdering.id) shouldBe avsluttetRevurdering.copy(
                underliggendeRevurdering = revurdering.copy(
                    avkorting = AvkortingVedRevurdering.Håndtert.KanIkkeHåndteres(
                        håndtert = AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
                    ),
                ),
            )
        }
    }
}
