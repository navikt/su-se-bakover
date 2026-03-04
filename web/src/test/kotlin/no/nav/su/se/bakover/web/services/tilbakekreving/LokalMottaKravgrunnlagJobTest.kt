package no.nav.su.se.bakover.web.services.tilbakekreving

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.tid.periode.oktober
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import org.junit.jupiter.api.Test
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlagstatus
import tilbakekreving.presentation.consumer.KravgrunnlagDtoMapper
import tilbakekreving.presentation.consumer.KravgrunnlagRootDto
import økonomi.domain.Fagområde
import økonomi.domain.KlasseKode
import økonomi.domain.KlasseType
import java.math.BigDecimal

internal class LokalMottaKravgrunnlagJobTest {
    @Test
    fun `lager klassekoder for alder`() {
        val xml = lagKravgrunnlagDetaljerXml(
            sakstype = Sakstype.ALDER,
            kravgrunnlag = kravgrunnlag(),
            fnr = "18108619852",
        )

        val dto = KravgrunnlagDtoMapper.toDto(xml).getOrFail() as KravgrunnlagRootDto
        val tilbakekrevingsbeløp = dto.kravgrunnlagDto.tilbakekrevingsperioder.single().tilbakekrevingsbeløp

        dto.kravgrunnlagDto.kodeFagområde shouldBe Fagområde.SUALDER.name
        tilbakekrevingsbeløp.single { it.typeKlasse == KlasseType.YTEL.name }.kodeKlasse shouldBe KlasseKode.SUALDER.name
        tilbakekrevingsbeløp.single { it.typeKlasse == KlasseType.FEIL.name }.kodeKlasse shouldBe KlasseKode.KL_KODE_FEIL.name
    }

    @Test
    fun `lager klassekoder for ufore`() {
        val xml = lagKravgrunnlagDetaljerXml(
            sakstype = Sakstype.UFØRE,
            kravgrunnlag = kravgrunnlag(),
            fnr = "18108619852",
        )

        val dto = KravgrunnlagDtoMapper.toDto(xml).getOrFail() as KravgrunnlagRootDto
        val tilbakekrevingsbeløp = dto.kravgrunnlagDto.tilbakekrevingsperioder.single().tilbakekrevingsbeløp

        dto.kravgrunnlagDto.kodeFagområde shouldBe Fagområde.SUUFORE.name
        tilbakekrevingsbeløp.single { it.typeKlasse == KlasseType.YTEL.name }.kodeKlasse shouldBe KlasseKode.SUUFORE.name
        tilbakekrevingsbeløp.single { it.typeKlasse == KlasseType.FEIL.name }.kodeKlasse shouldBe KlasseKode.KL_KODE_FEIL_INNT.name
    }

    private fun kravgrunnlag(): Kravgrunnlag {
        return Kravgrunnlag(
            hendelseId = HendelseId.generer(),
            saksnummer = Saksnummer(2463),
            eksternKravgrunnlagId = "298606",
            eksternVedtakId = "436206",
            eksternKontrollfelt = "2021-01-01-02.02.03.456789",
            status = Kravgrunnlagstatus.Nytt,
            behandler = "K231B433",
            utbetalingId = UUID30.fromString("268e62fb-3079-4e8d-ab32-ff9fb9"),
            eksternTidspunkt = fixedTidspunkt,
            grunnlagsperioder = listOf(
                Kravgrunnlag.Grunnlagsperiode(
                    periode = oktober(2021),
                    betaltSkattForYtelsesgruppen = 5280,
                    bruttoTidligereUtbetalt = 21989,
                    bruttoNyUtbetaling = 9989,
                    bruttoFeilutbetaling = 12000,
                    skatteProsent = BigDecimal("43.9992"),
                ),
            ),
        )
    }
}
