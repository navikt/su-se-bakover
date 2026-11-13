package no.nav.su.se.bakover.domain.historisk.revurdering.utbetaling

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.historisk.revurdering.GjeldendeHistoriskInfotrygdMånedsdata
import no.nav.su.se.bakover.domain.historisk.revurdering.GjeldendeHistoriskInfotrygdVedtaksdata
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdMånedskilde
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingsvedtak
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingsvedtakId
import no.nav.su.se.bakover.domain.historisk.revurdering.KunneIkkeVerifisereHistoriskeVedtakSomRevurderes
import no.nav.su.se.bakover.test.historisk.HistoriskInfotrygdOppdragTestdata
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID

internal class HistoriskInfotrygdOppdragsgrunnlagTest {
    private val data = HistoriskInfotrygdOppdragTestdata

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `bygger grunnlag for endret ytelse og rent opphør uten gammel Oppdrag-ID`(opphør: Boolean) {
        val gjeldende = data.gjeldende()
        val beregning = data.beregning(opphør = opphør)
        val revurdering = data.revurdering(gjeldende, beregning)

        val grunnlag = HistoriskInfotrygdOppdragsgrunnlag.bygg(revurdering, gjeldende).shouldBeRight()

        grunnlag.periode shouldBe data.periode
        grunnlag.månedsresultater shouldBe beregning.månedsresultater
        gjeldende shouldBe data.gjeldende()
        revurdering.beregning shouldBe beregning
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `bare første revurdering før SU overtar kan gi Infotrygd-opphør`(opphør: Boolean) {
        val original = data.original()
        val gjeldende = GjeldendeHistoriskInfotrygdVedtaksdata.bygg(original, emptyList())
        val førsteBeregning = data.beregning(opphør = opphør)
        val førsteRevurdering = data.revurdering(gjeldende, førsteBeregning)
        HistoriskInfotrygdOppdragsgrunnlag.bygg(førsteRevurdering, gjeldende).shouldBeRight()

        val vedtakId = HistoriskInfotrygdRevurderingsvedtakId(UUID.randomUUID())
        val vedtak = HistoriskInfotrygdRevurderingsvedtak(
            id = vedtakId,
            revurderingId = førsteRevurdering.id,
            sakId = data.sakId,
            utbetalingId = UUID30.randomUUID(),
            iverksatt = data.tidspunkt,
            attestant = NavIdentBruker.Attestant("A123456"),
            beregning = førsteBeregning,
        )
        val overtatt = GjeldendeHistoriskInfotrygdVedtaksdata.bygg(
            original,
            listOf(vedtak.tilIverksatteMånedsresultater()),
        )
        overtatt.tidslinje.values.map { it.kilde } shouldBe List(data.periode.måneder().size) {
            HistoriskInfotrygdMånedskilde.Revurderingsvedtak(vedtakId)
        }
        val nesteRevurdering = data.revurdering(overtatt)

        HistoriskInfotrygdOppdragsgrunnlag.bygg(nesteRevurdering, overtatt).shouldBeLeft() shouldBe
            KunneIkkeByggeHistoriskInfotrygdOppdragsgrunnlag.SuKildeIkkeStøttetIUtkast
    }

    @Test
    fun `avviser at SU har overtatt månedene etter beregning`() {
        val revurdering = data.revurdering()
        val nyttGjeldende = data.gjeldende(
            HistoriskInfotrygdMånedskilde.Revurderingsvedtak(HistoriskInfotrygdRevurderingsvedtakId(UUID.randomUUID())),
        )

        HistoriskInfotrygdOppdragsgrunnlag.bygg(revurdering, nyttGjeldende).shouldBeLeft() shouldBe
            KunneIkkeByggeHistoriskInfotrygdOppdragsgrunnlag.EndretVedtaksgrunnlag(
                KunneIkkeVerifisereHistoriskeVedtakSomRevurderes.DetHarKommetNyeOverlappendeVedtak,
            )
    }

    @Test
    fun `avviser uendret månedsbeløp selv om desimalskala er ulik`() {
        val revurdering = data.revurdering(beregning = data.beregning(beløp = data.gammeltBeløp.setScale(2)))

        HistoriskInfotrygdOppdragsgrunnlag.bygg(revurdering, data.gjeldende()).shouldBeLeft() shouldBe
            KunneIkkeByggeHistoriskInfotrygdOppdragsgrunnlag.UendretYtelse(data.januar)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `avviser uavklart håndtering når gammel ytelse allerede er opphørt`(opphør: Boolean) {
        val opprinnelig = data.gjeldende()
        val gjeldende = opprinnelig.copy(
            tidslinje = opprinnelig.tidslinje.mapValues { (måned, gammel) ->
                GjeldendeHistoriskInfotrygdMånedsdata.IngenYtelse(
                    måned = måned,
                    kilde = gammel.kilde,
                    opprinneligStønadId = data.stønadId,
                    opprinneligVedtakId = data.vedtakId,
                )
            },
        )
        val revurdering = data.revurdering(gjeldende, data.beregning(opphør = opphør))

        HistoriskInfotrygdOppdragsgrunnlag.bygg(revurdering, gjeldende).shouldBeLeft() shouldBe
            KunneIkkeByggeHistoriskInfotrygdOppdragsgrunnlag.IngenGjeldendeYtelse(data.januar)
    }

    @Test
    fun `avviser beregning som ikke dekker hele valgt periode`() {
        val fullBeregning = data.beregning()
        val beregning = fullBeregning.copy(
            månedsresultater = fullBeregning.månedsresultater.filterKeys { it == data.januar },
        )
        val revurdering = data.revurdering(beregning = beregning)

        HistoriskInfotrygdOppdragsgrunnlag.bygg(revurdering, data.gjeldende()).shouldBeLeft() shouldBe
            KunneIkkeByggeHistoriskInfotrygdOppdragsgrunnlag.BeregningDekkerIkkePerioden
    }
}
