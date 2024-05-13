package dokument.domain.pdf

/**
 * 1-1 mapping to templates defined by pdf-generator.
 */
sealed interface PdfTemplate {
    val templateName: String
    fun name() = templateName

    data object Søknad : PdfTemplate {
        override val templateName = "soknad"
    }

    data object InnvilgetVedtak : PdfTemplate {
        override val templateName = "vedtakInnvilgelse"
    }

    data object AvslagsVedtak : PdfTemplate {
        override val templateName = "vedtakAvslag"
    }

    data object TrukketSøknad : PdfTemplate {
        override val templateName = "søknadTrukket"
    }

    data object AvvistSøknadVedtak : PdfTemplate {
        override val templateName = "avvistSøknadVedtak"
    }

    data object AvvistSøknadFritekst : PdfTemplate {
        override val templateName = "avvistSøknadFritekst"
    }

    data object Opphørsvedtak : PdfTemplate {
        override val templateName = "opphørsvedtak"
    }

    data object Forhåndsvarsel : PdfTemplate {
        override val templateName = "forhåndsvarsel"
    }

    data object ForhåndsvarselTilbakekrevingsbehandling : PdfTemplate {
        override val templateName = "forhåndsvarselTilbakekrevingsbehandling"
    }

    data object VedtaksbrevTilbakekrevingsbehandling : PdfTemplate {
        override val templateName = "vedtaksbrevTilbakekrevingsbehandling"
    }

    data object InnkallingTilKontrollsamtale : PdfTemplate {
        override val templateName = "innkallingKontrollsamtale"
    }

    data object PåminnelseNyStønadsperiode : PdfTemplate {
        override val templateName = "påminnelseOmNyStønadsperiode"
    }

    sealed interface Revurdering : PdfTemplate {
        data object Inntekt : Revurdering {
            override val templateName = "revurderingAvInntekt"
        }

        data object Avslutt : Revurdering {
            override val templateName = "avsluttRevurdering"
        }
    }

    sealed interface Klage : PdfTemplate {
        data object Oppretthold : Klage {
            override val templateName = "sendtTilKlageinstans"
        }

        data object Avvist : Klage {
            override val templateName = "avvistKlage"
        }
    }

    data object FritekstDokument : PdfTemplate {
        override val templateName = "fritekstDokument"
    }

    data object Skattegrunnlag : PdfTemplate {
        override val templateName = "skattegrunnlag"
    }
}
