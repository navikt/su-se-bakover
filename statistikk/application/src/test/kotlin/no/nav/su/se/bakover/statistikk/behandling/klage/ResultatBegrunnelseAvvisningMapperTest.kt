package no.nav.su.se.bakover.statistikk.behandling.klage

import behandling.klage.domain.FormkravTilKlage
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

internal class ResultatBegrunnelseAvvisningMapperTest {

    @Test
    fun `en avslagsgrunn`() {
        FormkravTilKlage.create(
            vedtakId = UUID.randomUUID(),
            innenforFristen = FormkravTilKlage.Svarord.JA,
            klagesDetPåKonkreteElementerIVedtaket = true,
            erUnderskrevet = FormkravTilKlage.Svarord.NEI,
            fremsattRettsligKlageinteresse = FormkravTilKlage.Svarord.NEI,
        ).toResultatBegrunnelse() shouldBe "IKKE_UNDERSKREVET"
    }

    @Test
    fun `tre avslagsgrunner`() {
        FormkravTilKlage.create(
            vedtakId = UUID.randomUUID(),
            innenforFristen = FormkravTilKlage.Svarord.NEI,
            klagesDetPåKonkreteElementerIVedtaket = false,
            erUnderskrevet = FormkravTilKlage.Svarord.NEI,
            fremsattRettsligKlageinteresse = FormkravTilKlage.Svarord.NEI,
        ).toResultatBegrunnelse() shouldBe
            "IKKE_INNENFOR_FRISTEN," +
            "KLAGES_IKKE_PÅ_KONKRETE_ELEMENTER_I_VEDTAKET," +
            "IKKE_UNDERSKREVET"
    }

    @Test
    fun `alle 'ja' bør bli null`() {
        FormkravTilKlage.create(
            vedtakId = UUID.randomUUID(),
            innenforFristen = FormkravTilKlage.Svarord.JA,
            klagesDetPåKonkreteElementerIVedtaket = true,
            erUnderskrevet = FormkravTilKlage.Svarord.JA,
            fremsattRettsligKlageinteresse = FormkravTilKlage.Svarord.JA,
        ).toResultatBegrunnelse() shouldBe null
    }
}
