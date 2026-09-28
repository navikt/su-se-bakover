package tilbakekreving.infrastructure.repo.vurdering

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import no.nav.su.se.bakover.test.kravgrunnlag.grunnlagsperiode
import no.nav.su.se.bakover.test.kravgrunnlag.kravgrunnlag
import no.nav.su.se.bakover.test.nyVurdertTilbakekrevingsbehandlingHendelse
import no.nav.su.se.bakover.test.vurderingerMedKrav
import org.junit.jupiter.api.Test
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import økonomi.domain.KlasseKode
import java.math.BigDecimal

internal class MånedsvurderingTilbakekrevingsbehandlingDbJsonTest {

    @Test
    fun `bevarer trekk ved serialisering og deserialisering`() {
        val kravgrunnlagPåSakHendelseId = HendelseId.generer()
        val trekk = Kravgrunnlag.Grunnlagsperiode.Trekk(
            kodeKlasse = KlasseKode.KREDKRED.name,
            beløpOpprinnelig = -530,
            beløpNytt = 0,
            beløpTilbakekreves = 0,
            beløpUinnkrevd = 0,
            skatteProsent = BigDecimal.ZERO,
        )
        val kravgrunnlag = kravgrunnlag(
            kravgrunnlagPåSakHendelseId = kravgrunnlagPåSakHendelseId,
            grunnlagsperioder = nonEmptyListOf(
                grunnlagsperiode(
                    bruttoTidligereUtbetalt = 2000,
                    bruttoNyUtbetaling = 470,
                    bruttoFeilutbetaling = 1000,
                    trekk = listOf(trekk),
                ),
            ),
        )
        val expected = nyVurdertTilbakekrevingsbehandlingHendelse(
            kravgrunnlagPåSakHendelseId = kravgrunnlagPåSakHendelseId,
            vurderingerMedKrav = vurderingerMedKrav(kravgrunnlag = kravgrunnlag),
        )
        val persistertHendelse = PersistertHendelse(
            data = expected.toJson(),
            hendelsestidspunkt = expected.hendelsestidspunkt,
            versjon = expected.versjon,
            type = Hendelsestype("irrelevant-for-mapping"),
            sakId = expected.sakId,
            hendelseId = expected.hendelseId,
            tidligereHendelseId = expected.tidligereHendelseId,
            entitetId = expected.entitetId,
        )

        persistertHendelse.mapToVurdertTilbakekrevingsbehandlingHendelse() shouldBe expected
    }
}
