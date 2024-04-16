package no.nav.su.se.bakover.database.tilbakekreving

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.tid.februar
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.tilbakekreving.HistoriskSendtTilbakekrevingsvedtak
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.kravgrunnlag.kravgrunnlagEndringXml
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test
import tilbakekreving.domain.kravgrunnlag.rått.RåTilbakekrevingsvedtakForsendelse
import tilbakekreving.presentation.consumer.KravgrunnlagDtoMapper
import java.util.UUID

internal class TilbakekrevingUnderRevurderingPostgresRepoTest {

    @Test
    fun `kan lagre og hente uten behov for tilbakekrevingsbehandling`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)

            val (_, vedtak, _) = testDataHelper.persisterVedtakMedInnvilgetRevurderingOgOversendtUtbetalingMedKvittering()
            val revurdering = vedtak.behandling
            (testDataHelper.revurderingRepo.hent(revurdering.id) as IverksattRevurdering.Innvilget).sendtTilbakekrevingsvedtak shouldBe null
        }
    }

    @Test
    fun `kan hente tilbakekrevingsbehandlinger`() {
        withMigratedDb { dataSource ->
            // Får da feilutbetalingen for januar
            val clock = TikkendeKlokke(fixedClockAt(1.februar(2021)))
            val testDataHelper = TestDataHelper(dataSource, clock = clock)
            val (_, revurdering) = testDataHelper.persisterRevurderingSimulertOpphørt(
                revurderingsperiode = januar(2021),
            )

            testDataHelper.revurderingRepo.lagre(revurdering)

            val tilbakekrevingRepo = TilbakekrevingUnderRevurderingPostgresRepo(
                råttKravgrunnlagMapper = KravgrunnlagDtoMapper::toKravgrunnlag,
            )
            val id = UUID.randomUUID()
            testDataHelper.sessionFactory.withSession { session ->
                //language=PostgreSQL
                """INSERT INTO revurdering_tilbakekreving
                   (id, opprettet, sakId, revurderingId, fraOgMed, tilOgMed, avgjørelse, tilstand, kravgrunnlag, kravgrunnlagMottatt, tilbakekrevingsvedtakForsendelse)
                   VALUES (:id, :opprettet, :sakId, :revurderingId, :fraOgMed, :tilOgMed, 'tilbakekrev', 'sendt_tilbakekrevingsvedtak', :kravgrunnlag, :kravgrunnlagMottatt, :tilbakekrevingsvedtakForsendelse)
                """.trimIndent().insert(
                    mapOf(
                        "id" to id,
                        "opprettet" to fixedTidspunkt,
                        "sakId" to revurdering.sakId,
                        "revurderingId" to revurdering.id.value,
                        "fraOgMed" to revurdering.periode.fraOgMed,
                        "tilOgMed" to revurdering.periode.tilOgMed,
                        "kravgrunnlag" to kravgrunnlagEndringXml,
                        "kravgrunnlagMottatt" to fixedTidspunkt,
                        "tilbakekrevingsvedtakForsendelse" to """
                        {
                            "requestXml": "requestXml",
                            "requestSendt": "$fixedTidspunkt",
                            "responseXml": "responseXml"
                        }
                        """.trimIndent(),

                    ),
                    session,
                )
                val actual = tilbakekrevingRepo.hentTilbakekrevingsbehandling(
                    revurderingId = revurdering.id,
                    session = session,
                )!!
                actual shouldBe HistoriskSendtTilbakekrevingsvedtak(
                    id = id,
                    opprettet = fixedTidspunkt,
                    sakId = revurdering.sakId,
                    revurderingId = revurdering.id,
                    periode = revurdering.periode,
                    kravgrunnlag = actual.kravgrunnlag,
                    kravgrunnlagMottatt = fixedTidspunkt,
                    tilbakekrevingsvedtakForsendelse = RåTilbakekrevingsvedtakForsendelse(
                        requestXml = "requestXml",
                        responseXml = "responseXml",
                        tidspunkt = fixedTidspunkt,
                    ),
                    avgjørelse = HistoriskSendtTilbakekrevingsvedtak.AvgjørelseTilbakekrevingUnderRevurdering.Tilbakekrev,
                )
            }
        }
    }
}
