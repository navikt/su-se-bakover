package dokument.domain.generer

import dokument.domain.generer.bygger.GenerellData
import dokument.domain.generer.bygger.PdfBestilling
import dokument.domain.generer.bygger.maler.forhåndsvarsel
import dokument.domain.generer.bygger.maler.soknad

sealed interface DokumentMal {
    val navn: String

    // Tittel blir brukt til journaøføring og visning i Gosys
    val tittel: String

    val pdfByggerDev: Boolean
    val pdfByggerProd: Boolean

    val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)?
    val lagInnhold: ((PdfInnhold) -> PdfBestilling)?

    data object Søknad : DokumentMal {
        override val navn = "soknad"
        override val tittel = "Søknad om supplerende stønad"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling) = soknad
        override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object Kontrollnotat : DokumentMal {
        override val navn = "kontrollnotat"
        override val tittel = "Kontrollnotat"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object InnvilgetVedtak : DokumentMal {
        override val navn = "vedtakInnvilgelse"
        override val tittel = "Vedtaksbrev for søknad om supplerende stønad"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object AvslagsVedtak : DokumentMal {
        override val navn = "vedtakAvslag"
        override val tittel = "Avslag supplerende stønad"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object TrukketSøknad : DokumentMal {
        override val navn = "søknadTrukket"
        override val tittel = "Bekrefter at søknad er trukket"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object AvvistSøknadVedtak : DokumentMal {
        override val navn = "avvistSøknadVedtak"
        override val tittel = "Søknaden din om supplerende stønad er avvist"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object AvvistSøknadFritekst : DokumentMal {
        override val navn = "avvistSøknadFritekst"
        override val tittel = "Søknaden din om supplerende stønad er avvist"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object Opphørsvedtak : DokumentMal {
        override val navn = "opphørsvedtak"
        override val tittel = "Vedtak om opphør av supplerende stønad"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object Forhåndsvarsel : DokumentMal {
        override val navn = "forhåndsvarsel"
        override val tittel = "Varsel om at vi vil ta opp stønaden din til ny vurdering"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val lagInnhold: ((PdfInnhold) -> PdfBestilling) = forhåndsvarsel
    }

    data object ForhåndsvarselTilbakekrevingsbehandling : DokumentMal {
        override val navn = "forhåndsvarselTilbakekrevingsbehandling"
        override val tittel = "Varsel om mulig tilbakekreving"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object VedtaksbrevTilbakekrevingsbehandling : DokumentMal {
        override val navn = "vedtaksbrevTilbakekrevingsbehandling"
        override val tittel = "Tilbakekreving av Supplerende stønad"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object ForhåndsvarselTilbakekrevingsbehandlingDødsbo : DokumentMal {
        override val navn = "forhåndsvarselTilbakekrevingsbehandlingDødsbo"
        override val tittel = "Varsel om mulig tilbakekreving til dødsbo"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object VedtaksbrevTilbakekrevingsbehandlingDødsbo : DokumentMal {
        override val navn = "vedtaksbrevTilbakekrevingsbehandlingDødsbo"
        override val tittel = "Tilbakekreving av Supplerende stønad til dødsbo"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object InnkallingTilKontrollsamtale : DokumentMal {
        override val navn = "innkallingKontrollsamtale"
        override val tittel = "Supplerende stønad – innkalling til samtale"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data object PåminnelseNyStønadsperiode : DokumentMal {
        override val navn = "påminnelseOmNyStønadsperiode"
        override val tittel = "Påminnelse om ny stønadsperiode"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    sealed interface Revurdering : DokumentMal {
        data object Inntekt : Revurdering {
            override val navn = "revurderingAvInntekt"
            override val tittel = "Vi har vurdert den supplerende stønaden din på nytt"
            override val pdfByggerDev: Boolean = false
            override val pdfByggerProd: Boolean = false
            override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
            override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
        }

        data object Avslutt : Revurdering {
            override val navn = "avsluttRevurdering"
            override val tittel = "Ikke grunnlag for revurdering"
            override val pdfByggerDev: Boolean = false
            override val pdfByggerProd: Boolean = false
            override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
            override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
        }
    }

    data object VedtaksbrevReguleringAldersfradrag : DokumentMal {
        override val navn = "vedtaksbrevReguleringAldersfradrag"
        override val tittel = "Vedtak for regulering etter endret alderspensjon"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    sealed interface Klage : DokumentMal {
        data object Oppretthold : Klage {
            override val navn = "sendtTilKlageinstans"
            override val tittel = "Oversendelsesbrev til klager"
            override val pdfByggerDev: Boolean = false
            override val pdfByggerProd: Boolean = false
            override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
            override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
        }

        data object Avvist : Klage {
            override val navn = "avvistKlage"
            override val tittel = "Avvist klage"
            override val pdfByggerDev: Boolean = false
            override val pdfByggerProd: Boolean = false
            override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
            override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
        }
    }

    data class FritekstDokument(val nyTittel: String) : DokumentMal {
        override val navn = "fritekstDokument"
        override val tittel = nyTittel
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }

    data class Skattegrunnlag(val nyTittel: String? = null) : DokumentMal {
        override val navn = "skattegrunnlag"
        override val tittel = nyTittel ?: "Skattegrunnlag"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
        override val lagInnholdGenerell: ((GenerellData) -> PdfBestilling)? = null
        override val lagInnhold: ((PdfInnhold) -> PdfBestilling)? = null
    }
}
