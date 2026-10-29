package dokument.domain.generer

import dokument.domain.generer.bygger.GenerellData
import dokument.domain.generer.bygger.PdfBestilling
import dokument.domain.generer.bygger.maler.forhåndsvarsel
import dokument.domain.generer.bygger.maler.soknad

sealed interface DokumentMal {
    val navn: String

    val pdfByggerDev: Boolean
    val pdfByggerProd: Boolean

    val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)?
    val lagInnhold: ((PdfInnhold) -> PdfBestilling)?

    data object Søknad : DokumentMal {
        override val navn = "soknad"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling) = soknad
        override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object Kontrollnotat : DokumentMal {
        override val navn = "kontrollnotat"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object InnvilgetVedtak : DokumentMal {
        override val navn = "vedtakInnvilgelse"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object AvslagsVedtak : DokumentMal {
        override val navn = "vedtakAvslag"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object TrukketSøknad : DokumentMal {
        override val navn = "søknadTrukket"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object AvvistSøknadVedtak : DokumentMal {
        override val navn = "avvistSøknadVedtak"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object AvvistSøknadFritekst : DokumentMal {
        override val navn = "avvistSøknadFritekst"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object Opphørsvedtak : DokumentMal {
        override val navn = "opphørsvedtak"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object Forhåndsvarsel : DokumentMal {
        override val navn = "forhåndsvarsel"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val lagInnhold: ((PdfInnhold) -> PdfBestilling) = forhåndsvarsel
    }

    data object ForhåndsvarselTilbakekrevingsbehandling : DokumentMal {
        override val navn = "forhåndsvarselTilbakekrevingsbehandling"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object VedtaksbrevTilbakekrevingsbehandling : DokumentMal {
        override val navn = "vedtaksbrevTilbakekrevingsbehandling"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object ForhåndsvarselTilbakekrevingsbehandlingDødsbo : DokumentMal {
        override val navn = "forhåndsvarselTilbakekrevingsbehandlingDødsbo"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object VedtaksbrevTilbakekrevingsbehandlingDødsbo : DokumentMal {
        override val navn = "vedtaksbrevTilbakekrevingsbehandlingDødsbo"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object InnkallingTilKontrollsamtale : DokumentMal {
        override val navn = "innkallingKontrollsamtale"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object PåminnelseNyStønadsperiode : DokumentMal {
        override val navn = "påminnelseOmNyStønadsperiode"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    sealed interface Revurdering : DokumentMal {
        data object Inntekt : Revurdering {
            override val navn = "revurderingAvInntekt"
            override val pdfByggerDev: Boolean = false
            override val pdfByggerProd: Boolean = false
            override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
            override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
        }

        data object Avslutt : Revurdering {
            override val navn = "avsluttRevurdering"
            override val pdfByggerDev: Boolean = false
            override val pdfByggerProd: Boolean = false
            override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
            override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
        }
    }

    data object VedtaksbrevReguleringAldersfradrag : DokumentMal {
        override val navn = "vedtaksbrevReguleringAldersfradrag"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    sealed interface Klage : DokumentMal {
        data object Oppretthold : Klage {
            override val navn = "sendtTilKlageinstans"
            override val pdfByggerDev: Boolean = false
            override val pdfByggerProd: Boolean = false
            override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
            override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
        }

        data object Avvist : Klage {
            override val navn = "avvistKlage"
            override val pdfByggerDev: Boolean = false
            override val pdfByggerProd: Boolean = false
            override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
            override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
        }
    }

    data object FritekstDokument : DokumentMal {
        override val navn = "fritekstDokument"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object Skattegrunnlag : DokumentMal {
        override val navn = "skattegrunnlag"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }
}
