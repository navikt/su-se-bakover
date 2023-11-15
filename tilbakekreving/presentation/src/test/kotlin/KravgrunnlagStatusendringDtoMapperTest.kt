package tilbakekreving.presentation.consumer

import arrow.core.right
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.correlationId
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.kravgrunnlag.kravgrunnlagStatusendringSomRåttKravgrunnlagHendelse
import no.nav.su.se.bakover.test.kravgrunnlag.kravgrunnlagStatusendringXml
import no.nav.su.se.bakover.test.nySakUføre
import org.junit.jupiter.api.Test
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagPåSakHendelse
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagStatusendringPåSakHendelse
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlagstatus

internal class KravgrunnlagStatusendringDtoMapperTest {
    @Test
    fun `mapper jms tidspunkt riktig`() {
        val clock = TikkendeKlokke()
        val sak = nySakUføre(
            clock = clock,
        ).first
        val correlationId = correlationId()
        val hendelseId = HendelseId.generer()
        val eksternVedtakId = "123456"
        val råttKravgrunnlagHendelse = kravgrunnlagStatusendringSomRåttKravgrunnlagHendelse(
            correlationId = correlationId,
            saksnummer = sak.saksnummer.toString(),
            fnr = sak.fnr.toString(),
            hendelseId = hendelseId,
            eksternVedtakId = eksternVedtakId,
        )
        KravgrunnlagDtoMapper.toKravgrunnlagPåSakHendelse(
            råttKravgrunnlagHendelse = råttKravgrunnlagHendelse,
            hentSak = { sak.right() },
            correlationId = correlationId,
            clock = clock,
        ).getOrFail().second.shouldBeEqualToIgnoringFields(
            KravgrunnlagStatusendringPåSakHendelse(
                // Denne ignoreres.
                hendelseId = HendelseId.generer(),
                versjon = sak.versjon.inc(),
                sakId = sak.id,
                hendelsestidspunkt = Tidspunkt.parse("2021-01-01T01:02:05.456789Z"),
                meta = DefaultHendelseMetadata.fraCorrelationId(correlationId),
                tidligereHendelseId = råttKravgrunnlagHendelse.hendelseId,
                saksnummer = sak.saksnummer,
                eksternVedtakId = eksternVedtakId,
                status = Kravgrunnlagstatus.Sperret,
                // Tue Nov 14 2023 20:16:54 GMT+0100
                eksternTidspunkt = Tidspunkt.parse("2023-11-14T19:16:54.620Z"),
            ),
            KravgrunnlagPåSakHendelse::hendelseId,
        )
    }

    @Test
    fun `mapper melding om statusendring på åpent kravgrunnlag`() {
        val expected = KravgrunnlagStatusendringRootDto(
            endringKravOgVedtakstatus = KravgrunnlagStatusendringDto(
                vedtakId = "436206",
                kodeStatusKrav = "SPER",
                kodeFagområde = "SUUFORE",
                fagsystemId = "2463",
                vedtakGjelderId = "18108619852",
                idTypeGjelder = "PERSON",
            ),
        )
        KravgrunnlagDtoMapper.toDto(kravgrunnlagStatusendringXml()).getOrFail() shouldBe expected
    }
}
