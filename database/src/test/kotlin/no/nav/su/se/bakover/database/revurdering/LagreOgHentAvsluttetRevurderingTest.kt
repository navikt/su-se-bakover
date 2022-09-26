package no.nav.su.se.bakover.database.revurdering

import arrow.core.getOrHandle
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.revurdering.AvsluttetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.stønadsperiode2021
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

internal class LagreOgHentAvsluttetRevurderingTest {

    @Test
    fun `opprettet`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo

            val (_, revurdering) = testDataHelper.persisterRevurderingOpprettet(
                sakOgVedtak = testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering(
                    stønadsperiode = stønadsperiode2021,
                ).let { it.first to it.second },
                periode = år(2021),
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

            val (_, revurdering) = testDataHelper.persisterRevurderingBeregnetInnvilget()

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
    fun `beregnet opphørt`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo

            val (_, revurdering) = testDataHelper.persisterRevurderingBeregnetOpphørt()

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
    @Disabled("https://trello.com/c/5iblmYP9/1090-endre-sperre-for-10-endring-til-%C3%A5-v%C3%A6re-en-advarsel")
    fun `beregnet ingen endring`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo

            val (_, revurdering) = testDataHelper.persisterRevurderingBeregningIngenEndring()

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
    fun `simulert innvilget med skal ikke fohåndsvarsle`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val (_, simulert) = testDataHelper.persisterRevurderingSimulertInnvilget()
            val simulertIngenForhåndsvarsel =
                simulert.ikkeSendForhåndsvarsel().getOrFail().also {
                    repo.lagre(it)
                }
            (repo.hent(simulert.id) as Revurdering) shouldBe simulertIngenForhåndsvarsel
        }
    }

    @Test
    fun `simulert innvilget med sendt forhåndsvarsel`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val (_, simulert) = testDataHelper.persisterRevurderingSimulertInnvilget()
            val simulertIngenForhåndsvarsel =
                simulert.markerForhåndsvarselSomSendt().getOrFail().also {
                    repo.lagre(it)
                }
            (repo.hent(simulert.id) as Revurdering) shouldBe simulertIngenForhåndsvarsel
        }
    }

    @Test
    fun `simulert innvilget med avsluttet forhåndsvarsel`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val (_, simulert) = testDataHelper.persisterRevurderingSimulertInnvilget()
            val simulertIngenForhåndsvarsel =
                simulert.markerForhåndsvarselSomSendt().getOrFail().copy(
                    // Vi har fjernet muligheten for å endre til denne tilstanden, men vi må støtte ikke-migrerte verdier i databasen.
                    forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.Avsluttet(""),
                ).also {
                    repo.lagre(it)
                }
            (repo.hent(simulert.id) as Revurdering) shouldBe simulertIngenForhåndsvarsel
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

            val revurdering = testDataHelper.persisterRevurderingUnderkjentInnvilget()

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
