package no.nav.su.se.bakover.statistikk.behandling.klage

import behandling.klage.domain.VilkårsvurderingerTilKlage
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

internal class ResultatBegrunnelseAvvisningMapperTest {

    @Test
    fun `en avslagsgrunn`() {
        VilkårsvurderingerTilKlage.create(
            vedtakId = UUID.randomUUID(),
            innenforFristen = VilkårsvurderingerTilKlage.Svarord.JA,
            klagesDetPåKonkreteElementerIVedtaket = true,
            erUnderskrevet = VilkårsvurderingerTilKlage.Svarord.NEI,
            begrunnelse = "",
        ).toResultatBegrunnelse() shouldBe "IKKE_UNDERSKREVET"
    }

    @Test
    fun `tre avslagsgrunner`() {
        VilkårsvurderingerTilKlage.create(
            vedtakId = UUID.randomUUID(),
            innenforFristen = VilkårsvurderingerTilKlage.Svarord.NEI,
            klagesDetPåKonkreteElementerIVedtaket = false,
            erUnderskrevet = VilkårsvurderingerTilKlage.Svarord.NEI,
            begrunnelse = "",
        ).toResultatBegrunnelse() shouldBe
            "IKKE_INNENFOR_FRISTEN," +
            "KLAGES_IKKE_PÅ_KONKRETE_ELEMENTER_I_VEDTAKET," +
            "IKKE_UNDERSKREVET"
    }

    @Test
    fun `alle 'ja' bør bli null`() {
        VilkårsvurderingerTilKlage.create(
            vedtakId = UUID.randomUUID(),
            innenforFristen = VilkårsvurderingerTilKlage.Svarord.JA,
            klagesDetPåKonkreteElementerIVedtaket = true,
            erUnderskrevet = VilkårsvurderingerTilKlage.Svarord.JA,
            begrunnelse = "",
        ).toResultatBegrunnelse() shouldBe null
    }
}
