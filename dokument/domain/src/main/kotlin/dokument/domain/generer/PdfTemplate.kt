package dokument.domain.generer

import dokument.domain.generer.bygger.GenerellData
import dokument.domain.generer.bygger.PdfBestilling
import dokument.domain.generer.bygger.maler.forhåndsvarsel
import dokument.domain.generer.bygger.maler.soknad

/**
 * 1-1 mapping to templates defined by pdf-generator.
 */
sealed interface PdfTemplate {
    val navn: String

    val pdfByggerDev: Boolean
    val pdfByggerProd: Boolean

    val malInnholdGenerell: ((GenerellData) -> PdfBestilling)?
    val malInnhold: ((PdfInnhold) -> PdfBestilling)?

    fun name() = navn

    data object Søknad : PdfTemplate {
        override val navn = "soknad"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val malInnholdGenerell: ((GenerellData) -> PdfBestilling) = soknad
        override val malInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object Kontrollnotat : PdfTemplate {
        override val navn = "kontrollnotat"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val malInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val malInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object InnvilgetVedtak : PdfTemplate {
        override val navn = "vedtakInnvilgelse"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val malInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val malInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object AvslagsVedtak : PdfTemplate {
        override val navn = "vedtakAvslag"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val malInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val malInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object TrukketSøknad : PdfTemplate {
        override val navn = "søknadTrukket"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val malInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val malInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object AvvistSøknadVedtak : PdfTemplate {
        override val navn = "avvistSøknadVedtak"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val malInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val malInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object AvvistSøknadFritekst : PdfTemplate {
        override val navn = "avvistSøknadFritekst"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val malInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val malInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object Opphørsvedtak : PdfTemplate {
        override val navn = "opphørsvedtak"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val malInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val malInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object Forhåndsvarsel : PdfTemplate {
        override val navn = "forhåndsvarsel"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val malInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val malInnhold: ((PdfInnhold) -> PdfBestilling) = forhåndsvarsel
    }

    data object ForhåndsvarselTilbakekrevingsbehandling : PdfTemplate {
        override val navn = "forhåndsvarselTilbakekrevingsbehandling"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val malInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val malInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object VedtaksbrevTilbakekrevingsbehandling : PdfTemplate {
        override val navn = "vedtaksbrevTilbakekrevingsbehandling"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val malInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val malInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object ForhåndsvarselTilbakekrevingsbehandlingDødsbo : PdfTemplate {
        override val navn = "forhåndsvarselTilbakekrevingsbehandlingDødsbo"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val malInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val malInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object VedtaksbrevTilbakekrevingsbehandlingDødsbo : PdfTemplate {
        override val navn = "vedtaksbrevTilbakekrevingsbehandlingDødsbo"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val malInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val malInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object InnkallingTilKontrollsamtale : PdfTemplate {
        override val navn = "innkallingKontrollsamtale"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val malInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val malInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object PåminnelseNyStønadsperiode : PdfTemplate {
        override val navn = "påminnelseOmNyStønadsperiode"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val malInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val malInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    sealed interface Revurdering : PdfTemplate {
        data object Inntekt : Revurdering {
            override val navn = "revurderingAvInntekt"
            override val pdfByggerDev: Boolean = false
            override val pdfByggerProd: Boolean = false
            override val malInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
            override val malInnhold: ((PdfInnhold) -> PdfBestilling)? = null
        }

        data object Avslutt : Revurdering {
            override val navn = "avsluttRevurdering"
            override val pdfByggerDev: Boolean = false
            override val pdfByggerProd: Boolean = false
            override val malInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
            override val malInnhold: ((PdfInnhold) -> PdfBestilling)? = null
        }
    }

    data object VedtaksbrevReguleringAldersfradrag : PdfTemplate {
        override val navn = "vedtaksbrevReguleringAldersfradrag"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val malInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val malInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    sealed interface Klage : PdfTemplate {
        data object Oppretthold : Klage {
            override val navn = "sendtTilKlageinstans"
            override val pdfByggerDev: Boolean = false
            override val pdfByggerProd: Boolean = false
            override val malInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
            override val malInnhold: ((PdfInnhold) -> PdfBestilling)? = null
        }

        data object Avvist : Klage {
            override val navn = "avvistKlage"
            override val pdfByggerDev: Boolean = false
            override val pdfByggerProd: Boolean = false
            override val malInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
            override val malInnhold: ((PdfInnhold) -> PdfBestilling)? = null
        }
    }

    data object FritekstDokument : PdfTemplate {
        override val navn = "fritekstDokument"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val malInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val malInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object Skattegrunnlag : PdfTemplate {
        override val navn = "skattegrunnlag"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val malInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val malInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }
}
