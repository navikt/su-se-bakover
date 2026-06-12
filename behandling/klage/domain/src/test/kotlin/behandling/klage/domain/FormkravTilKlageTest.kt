package behandling.klage.domain

import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

internal class FormkravTilKlageTest {
    @Test
    fun `blir påbegynt når både vedtakid og infotrygdSakId mangler`() {
        val formkrav = FormkravTilKlage.create(
            vedtakId = null,
            innenforFristen = FormkravTilKlage.SvarMedBegrunnelse(FormkravTilKlage.Svarord.JA, "begrunnelse"),
            klagesDetPåKonkreteElementerIVedtaket = FormkravTilKlage.BooleanMedBegrunnelse(true, "begrunnelse"),
            erUnderskrevet = FormkravTilKlage.SvarMedBegrunnelse(FormkravTilKlage.Svarord.JA, "begrunnelse"),
            fremsattRettsligKlageinteresse = FormkravTilKlage.SvarMedBegrunnelse(
                FormkravTilKlage.Svarord.JA,
                "begrunnelse",
            ),
            infotrygdSakId = null,
        )

        formkrav.shouldBeInstanceOf<FormkravTilKlage.Påbegynt>()
    }

    @Test
    fun `blir utfylt når vedtakId mangler og  infotrygdSakId finnes`() {
        val formkrav = FormkravTilKlage.create(
            vedtakId = null,
            innenforFristen = FormkravTilKlage.SvarMedBegrunnelse(FormkravTilKlage.Svarord.JA, "begrunnelse"),
            klagesDetPåKonkreteElementerIVedtaket = FormkravTilKlage.BooleanMedBegrunnelse(true, "begrunnelse"),
            erUnderskrevet = FormkravTilKlage.SvarMedBegrunnelse(FormkravTilKlage.Svarord.JA, "begrunnelse"),
            fremsattRettsligKlageinteresse = FormkravTilKlage.SvarMedBegrunnelse(
                FormkravTilKlage.Svarord.JA,
                "begrunnelse",
            ),
            infotrygdSakId = "12345678910",
        )

        formkrav.shouldBeInstanceOf<FormkravTilKlage.Utfylt>()
    }

    @Test
    fun `blir påbegynt når vedtakId finnes`() {
        val formkrav = FormkravTilKlage.create(
            vedtakId = java.util.UUID.randomUUID(),
            innenforFristen = FormkravTilKlage.SvarMedBegrunnelse(FormkravTilKlage.Svarord.JA, "begrunnelse"),
            klagesDetPåKonkreteElementerIVedtaket = FormkravTilKlage.BooleanMedBegrunnelse(true, "begrunnelse"),
            erUnderskrevet = FormkravTilKlage.SvarMedBegrunnelse(FormkravTilKlage.Svarord.JA, "begrunnelse"),
            fremsattRettsligKlageinteresse = FormkravTilKlage.SvarMedBegrunnelse(
                FormkravTilKlage.Svarord.JA,
                "begrunnelse",
            ),
            infotrygdSakId = null,
        )

        formkrav.shouldBeInstanceOf<FormkravTilKlage.Utfylt>()
    }
}
