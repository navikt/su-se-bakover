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
            innenforFristen = FormkravTilKlage.SvarMedBegrunnelse(FormkravTilKlage.Svarord.JA, "Innenfor fristen er JA"),
            klagesDetPåKonkreteElementerIVedtaket = FormkravTilKlage.BooleanMedBegrunnelse(true, "tekst"),
            erUnderskrevet = FormkravTilKlage.SvarMedBegrunnelse(FormkravTilKlage.Svarord.NEI, "Innenfor fristen er NEI"),
            fremsattRettsligKlageinteresse = FormkravTilKlage.SvarMedBegrunnelse(FormkravTilKlage.Svarord.JA, "Innenfor fristen er JA"),
        ).toResultatBegrunnelse() shouldBe "IKKE_UNDERSKREVET"
    }

    @Test
    fun `tre avslagsgrunner`() {
        FormkravTilKlage.create(
            vedtakId = UUID.randomUUID(),
            innenforFristen = FormkravTilKlage.SvarMedBegrunnelse(FormkravTilKlage.Svarord.NEI, "Innenfor fristen er NEI"),
            klagesDetPåKonkreteElementerIVedtaket = FormkravTilKlage.BooleanMedBegrunnelse(false, "tekst"),
            erUnderskrevet = FormkravTilKlage.SvarMedBegrunnelse(FormkravTilKlage.Svarord.NEI, "Innenfor fristen er NEI"),
            fremsattRettsligKlageinteresse = FormkravTilKlage.SvarMedBegrunnelse(FormkravTilKlage.Svarord.JA, "Innenfor fristen er JA"),
        ).toResultatBegrunnelse() shouldBe
            "IKKE_INNENFOR_FRISTEN," +
            "KLAGES_IKKE_PÅ_KONKRETE_ELEMENTER_I_VEDTAKET," +
            "IKKE_UNDERSKREVET"
    }

    @Test
    fun `alle 'ja' bør bli null`() {
        FormkravTilKlage.create(
            vedtakId = UUID.randomUUID(),
            innenforFristen = FormkravTilKlage.SvarMedBegrunnelse(FormkravTilKlage.Svarord.JA, "Innenfor fristen er JA"),
            klagesDetPåKonkreteElementerIVedtaket = FormkravTilKlage.BooleanMedBegrunnelse(true, "tekst"),
            erUnderskrevet = FormkravTilKlage.SvarMedBegrunnelse(FormkravTilKlage.Svarord.JA, "Innenfor fristen er JA"),
            fremsattRettsligKlageinteresse = FormkravTilKlage.SvarMedBegrunnelse(FormkravTilKlage.Svarord.JA, "Innenfor fristen er JA"),
        ).toResultatBegrunnelse() shouldBe null
    }
}
