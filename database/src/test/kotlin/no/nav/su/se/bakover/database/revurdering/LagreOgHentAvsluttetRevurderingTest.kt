package no.nav.su.se.bakover.database.revurdering

import arrow.core.getOrElse
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.su.se.bakover.domain.revurdering.AvsluttetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Omgjøringsgrunn
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.saksbehandler
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import javax.sql.DataSource

@ExtendWith(DbExtension::class)
internal class LagreOgHentAvsluttetRevurderingTest(private val dataSource: DataSource) {

    @Test
    fun opprettet() {
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

    @Test
    fun `Kan opprette omgjøringsrevurdering med grunn`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = testDataHelper.revurderingRepo

        val (_, revurdering) = testDataHelper.persisterRevurderingOpprettet(
            sakOgVedtak = testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling()
                .let { it.first to it.second },
            sakOgRevurdering = { (sak, vedtak) ->
                opprettetRevurdering(
                    sakOgVedtakSomKanRevurderes = Pair(sak, vedtak),
                    omgjøringsgrunn = Omgjøringsgrunn.NYE_OPPLYSNINGER,
                    revurderingsårsak = Revurderingsårsak(
                        Revurderingsårsak.Årsak.OMGJØRING_EGET_TILTAK,
                        Revurderingsårsak.Begrunnelse.create("revurderingsårsakBegrunnelse"),
                    ),
                )
            },

        )

        repo.lagre(revurdering)
        val hentetRevurderingomgjøring = repo.hent(revurdering.id)
        hentetRevurderingomgjøring.shouldBeInstanceOf<OpprettetRevurdering>().let {
            it.omgjøringsgrunn shouldBe Omgjøringsgrunn.NYE_OPPLYSNINGER
        }
    }

    @Test
    fun `beregnet innvilget`() {
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

    @Test
    fun `beregnet opphørt`() {
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

    @Test
    fun `simulert innvilget`() {
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

    @Test
    fun `simulert opphørt`() {
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

    @Test
    fun `til attestering - innvilget`() {
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
