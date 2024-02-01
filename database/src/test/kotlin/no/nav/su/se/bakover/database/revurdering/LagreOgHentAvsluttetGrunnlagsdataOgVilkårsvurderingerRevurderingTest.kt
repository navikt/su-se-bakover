package no.nav.su.se.bakover.database.revurdering

import arrow.core.getOrElse
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.revurdering.AvsluttetRevurdering
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.saksbehandler
import org.junit.jupiter.api.Test

internal class LagreOgHentAvsluttetGrunnlagsdataOgVilkårsvurderingerRevurderingTest {

    @Test
    fun `opprettet`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo

            val (_, revurdering) = testDataHelper.persisterRevurderingOpprettet(
                sakOgVedtak = testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling()
                    .let { it.first to it.second },
            )

            val avsluttetRevurdering = AvsluttetRevurdering.tryCreate(
                underliggendeRevurdering = revurdering,
                begrunnelse = "avslutter denne revurderingen",
                brevvalg = null,
                tidspunktAvsluttet = fixedTidspunkt,
                avsluttetAv = saksbehandler,
            ).getOrElse { fail("$it") }

            repo.lagre(avsluttetRevurdering)
            repo.hent(avsluttetRevurdering.id) shouldBe avsluttetRevurdering
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
                avsluttetAv = saksbehandler,
            ).getOrElse { fail("$it") }

            repo.lagre(avsluttetRevurdering)
            repo.hent(avsluttetRevurdering.id) shouldBe avsluttetRevurdering
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
                avsluttetAv = saksbehandler,
            ).getOrElse { fail("$it") }

            repo.lagre(avsluttetRevurdering)
            repo.hent(avsluttetRevurdering.id) shouldBe avsluttetRevurdering
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
                avsluttetAv = saksbehandler,
            ).getOrElse { fail("$it") }

            repo.lagre(avsluttetRevurdering)
            repo.hent(avsluttetRevurdering.id) shouldBe avsluttetRevurdering
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
                avsluttetAv = saksbehandler,
            ).getOrElse { fail("$it") }

            repo.lagre(avsluttetRevurdering)
            repo.hent(avsluttetRevurdering.id) shouldBe avsluttetRevurdering
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
                avsluttetAv = saksbehandler,
            ).getOrElse { fail("$it") }

            repo.lagre(avsluttetRevurdering)
            repo.hent(avsluttetRevurdering.id) shouldBe avsluttetRevurdering
        }
    }
}
