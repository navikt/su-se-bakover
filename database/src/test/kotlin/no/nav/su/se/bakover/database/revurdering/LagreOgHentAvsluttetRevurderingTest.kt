package no.nav.su.se.bakover.database.revurdering

import arrow.core.getOrHandle
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.persistertVariant
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.revurdering.AvsluttetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.periode2021
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

internal class LagreOgHentAvsluttetRevurderingTest {

    @Test
    fun `opprettet`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo

            val revurdering = testDataHelper.nyRevurdering(
                innvilget = testDataHelper.vedtakMedInnvilgetSøknadsbehandling(periode2021).first,
                periode = periode2021,
            ).persistertVariant()

            val avsluttetRevurdering = AvsluttetRevurdering.tryCreate(
                underliggendeRevurdering = revurdering,
                begrunnelse = "avslutter denne revurderingen",
                fritekst = null,
                tidspunktAvsluttet = fixedTidspunkt,
            ).getOrHandle { fail("$it") }

            repo.lagre(avsluttetRevurdering)
            repo.hent(avsluttetRevurdering.id) shouldBe avsluttetRevurdering.copy(
                underliggendeRevurdering = revurdering.copy(
                    avkorting = AvkortingVedRevurdering.Uhåndtert.KanIkkeHåndtere(
                        uhåndtert = AvkortingVedRevurdering.Uhåndtert.IngenUtestående
                    )
                )
            )
        }
    }

    @Test
    fun `beregnet innvilget`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo

            val revurdering = testDataHelper.beregnetInnvilgetRevurdering().persistertVariant()

            val avsluttetRevurdering = AvsluttetRevurdering.tryCreate(
                underliggendeRevurdering = revurdering,
                begrunnelse = "avslutter denne revurderingen",
                fritekst = null,
                tidspunktAvsluttet = fixedTidspunkt,
            ).getOrHandle { fail("$it") }

            repo.lagre(avsluttetRevurdering)
            repo.hent(avsluttetRevurdering.id) shouldBe avsluttetRevurdering.copy(
                underliggendeRevurdering = revurdering.copy(
                    avkorting = AvkortingVedRevurdering.DelvisHåndtert.KanIkkeHåndtere(
                        delvisHåndtert = AvkortingVedRevurdering.DelvisHåndtert.IngenUtestående
                    )
                )
            )
        }
    }

    @Test
    fun `beregnet opphørt`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo

            val revurdering = testDataHelper.beregnetOpphørtRevurdering().persistertVariant()

            val avsluttetRevurdering = AvsluttetRevurdering.tryCreate(
                underliggendeRevurdering = revurdering,
                begrunnelse = "avslutter denne revurderingen",
                fritekst = null,
                tidspunktAvsluttet = fixedTidspunkt,
            ).getOrHandle { fail("$it") }

            repo.lagre(avsluttetRevurdering)
            repo.hent(avsluttetRevurdering.id) shouldBe avsluttetRevurdering.copy(
                underliggendeRevurdering = revurdering.copy(
                    avkorting = AvkortingVedRevurdering.DelvisHåndtert.KanIkkeHåndtere(
                        delvisHåndtert = AvkortingVedRevurdering.DelvisHåndtert.IngenUtestående
                    )
                )
            )
        }
    }

    @Test
    @Disabled("https://trello.com/c/5iblmYP9/1090-endre-sperre-for-10-endring-til-%C3%A5-v%C3%A6re-en-advarsel")
    fun `beregnet ingen endring`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo

            val revurdering = testDataHelper.beregnetIngenEndringRevurdering().persistertVariant()

            val avsluttetRevurdering = AvsluttetRevurdering.tryCreate(
                underliggendeRevurdering = revurdering,
                begrunnelse = "avslutter denne revurderingen",
                fritekst = null,
                tidspunktAvsluttet = fixedTidspunkt,
            ).getOrHandle { fail("$it") }

            repo.lagre(avsluttetRevurdering)
            repo.hent(avsluttetRevurdering.id) shouldBe avsluttetRevurdering.copy(
                underliggendeRevurdering = revurdering.copy(
                    avkorting = AvkortingVedRevurdering.DelvisHåndtert.KanIkkeHåndtere(
                        delvisHåndtert = AvkortingVedRevurdering.DelvisHåndtert.IngenUtestående
                    )
                )
            )
        }
    }

    @Test
    fun `simulert innvilget`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo

            val revurdering = testDataHelper.simulertInnvilgetRevurdering().persistertVariant()

            val avsluttetRevurdering = AvsluttetRevurdering.tryCreate(
                underliggendeRevurdering = revurdering,
                begrunnelse = "avslutter denne revurderingen",
                fritekst = null,
                tidspunktAvsluttet = fixedTidspunkt,
            ).getOrHandle { fail("$it") }

            repo.lagre(avsluttetRevurdering)
            repo.hent(avsluttetRevurdering.id) shouldBe avsluttetRevurdering.copy(
                underliggendeRevurdering = revurdering.copy(
                    avkorting = AvkortingVedRevurdering.Håndtert.KanIkkeHåndteres(
                        håndtert = AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående
                    )
                )
            )
        }
    }

    @Test
    fun `simulert innvilget med skal ikke fohåndsvarsle`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val simulert = testDataHelper.simulertInnvilgetRevurdering()
            val simulertIngenForhåndsvarsel =
                simulert.ikkeSendForhåndsvarsel().orNull()!!.also {
                    repo.lagre(it)
                }
            (repo.hent(simulert.id) as Revurdering) shouldBe simulertIngenForhåndsvarsel.persistertVariant()
        }
    }

    @Test
    fun `simulert innvilget med sendt forhåndsvarsel`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val simulert = testDataHelper.simulertInnvilgetRevurdering()
            val simulertIngenForhåndsvarsel =
                simulert.markerForhåndsvarselSomSendt().orNull()!!.also {
                    repo.lagre(it)
                }
            (repo.hent(simulert.id) as Revurdering) shouldBe simulertIngenForhåndsvarsel.persistertVariant()
        }
    }

    @Test
    fun `simulert innvilget med avsluttet forhåndsvarsel`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val simulert = testDataHelper.simulertInnvilgetRevurdering()
            val simulertIngenForhåndsvarsel =
                simulert.markerForhåndsvarselSomSendt().orNull()!!.prøvOvergangTilAvsluttet("").orNull()!!.also {
                    repo.lagre(it)
                }
            (repo.hent(simulert.id) as Revurdering) shouldBe simulertIngenForhåndsvarsel.persistertVariant()
        }
    }

    @Test
    fun `simulert opphørt`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo

            val revurdering = testDataHelper.simulertInnvilgetRevurdering().persistertVariant()

            val avsluttetRevurdering = AvsluttetRevurdering.tryCreate(
                underliggendeRevurdering = revurdering,
                begrunnelse = "avslutter denne revurderingen",
                fritekst = null,
                tidspunktAvsluttet = fixedTidspunkt,
            ).getOrHandle { fail("$it") }

            repo.lagre(avsluttetRevurdering)
            repo.hent(avsluttetRevurdering.id) shouldBe avsluttetRevurdering.copy(
                underliggendeRevurdering = revurdering.copy(
                    avkorting = AvkortingVedRevurdering.Håndtert.KanIkkeHåndteres(
                        håndtert = AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående
                    )
                )
            )
        }
    }

    @Test
    fun `til attestering - innvilget`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo

            val revurdering = testDataHelper.underkjentRevurderingFraInnvilget().persistertVariant()

            val avsluttetRevurdering = AvsluttetRevurdering.tryCreate(
                underliggendeRevurdering = revurdering,
                begrunnelse = "avslutter denne revurderingen",
                fritekst = null,
                tidspunktAvsluttet = fixedTidspunkt,
            ).getOrHandle { fail("$it") }

            repo.lagre(avsluttetRevurdering)
            repo.hent(avsluttetRevurdering.id) shouldBe avsluttetRevurdering.copy(
                underliggendeRevurdering = revurdering.copy(
                    avkorting = AvkortingVedRevurdering.Håndtert.KanIkkeHåndteres(
                        håndtert = AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående
                    )
                )
            )
        }
    }
}
