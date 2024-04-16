package dokument.domain.pdf

// TODO jah: Flytt disse til de respektive plassene (må fjerne sealed). Bytt til interface.
sealed interface PdfTemplateMedDokumentNavn {
    val pdfTemplate: PdfTemplate

    /** navnet vi bruker i gosys og journalføring */
    val dokumentNavn: String
    fun template() = pdfTemplate.name()
    fun tittel() = dokumentNavn

    data object Søknad : PdfTemplateMedDokumentNavn {
        override val pdfTemplate = PdfTemplate.Søknad
        override val dokumentNavn = "Søknad om supplerende stønad"
    }

    data object InnvilgetVedtak : PdfTemplateMedDokumentNavn {
        override val pdfTemplate = PdfTemplate.InnvilgetVedtak
        override val dokumentNavn = "Vedtaksbrev for søknad om supplerende stønad"
    }

    data object AvslagsVedtak : PdfTemplateMedDokumentNavn {
        override val pdfTemplate = PdfTemplate.AvslagsVedtak
        override val dokumentNavn = "Avslag supplerende stønad"
    }

    data object TrukketSøknad : PdfTemplateMedDokumentNavn {
        override val pdfTemplate = PdfTemplate.TrukketSøknad
        override val dokumentNavn = "Bekrefter at søknad er trukket"
    }

    data object AvvistSøknadVedtak : PdfTemplateMedDokumentNavn {
        override val pdfTemplate = PdfTemplate.AvvistSøknadVedtak
        override val dokumentNavn = "Søknaden din om supplerende stønad er avvist"
    }

    data object AvvistSøknadFritekst : PdfTemplateMedDokumentNavn {
        override val pdfTemplate = PdfTemplate.AvvistSøknadFritekst
        override val dokumentNavn = "Søknaden din om supplerende stønad er avvist"
    }

    sealed interface Opphør : PdfTemplateMedDokumentNavn {
        data object Opphørsvedtak : Opphør {
            override val pdfTemplate = PdfTemplate.Opphørsvedtak
            override val dokumentNavn = "Vedtak om opphør av supplerende stønad"
        }
    }

    data object Forhåndsvarsel : PdfTemplateMedDokumentNavn {
        override val pdfTemplate = PdfTemplate.Forhåndsvarsel
        override val dokumentNavn = "Varsel om at vi vil ta opp stønaden din til ny vurdering"
    }

    data object ForhåndsvarselTilbakekrevingsbehandling : PdfTemplateMedDokumentNavn {
        override val pdfTemplate = PdfTemplate.ForhåndsvarselTilbakekrevingsbehandling
        override val dokumentNavn = "Varsel om mulig tilbakekreving"
    }

    data object VedtaksbrevTilbakekrevingsbehandling : PdfTemplateMedDokumentNavn {
        override val pdfTemplate = PdfTemplate.VedtaksbrevTilbakekrevingsbehandling
        override val dokumentNavn = "Tilbakekreving av Supplerende stønad"
    }

    data object InnkallingTilKontrollsamtale : PdfTemplateMedDokumentNavn {
        override val pdfTemplate = PdfTemplate.InnkallingTilKontrollsamtale
        override val dokumentNavn = "Supplerende stønad ufør flyktning – innkalling til samtale"
    }

    data object PåminnelseNyStønadsperiode : PdfTemplateMedDokumentNavn {
        override val pdfTemplate = PdfTemplate.PåminnelseNyStønadsperiode
        override val dokumentNavn = "Påminnelse om ny stønadsperiode"
    }

    sealed interface Revurdering : PdfTemplateMedDokumentNavn {
        data object Inntekt : Revurdering {
            override val pdfTemplate = PdfTemplate.Revurdering.Inntekt
            override val dokumentNavn = "Vi har vurdert den supplerende stønaden din på nytt"
        }

        data object MedTilbakekreving : Revurdering {
            override val pdfTemplate = PdfTemplate.Revurdering.MedTilbakekreving
            override val dokumentNavn =
                "Vi har vurdert den supplerende stønaden din på nytt og vil kreve tilbake penger"
        }

        data object AvsluttRevurdering : Revurdering {
            override val pdfTemplate = PdfTemplate.Revurdering.Avslutt
            override val dokumentNavn = "Ikke grunnlag for revurdering"
        }
    }

    sealed interface Klage : PdfTemplateMedDokumentNavn {
        data object Oppretthold : Klage {
            override val pdfTemplate = PdfTemplate.Klage.Oppretthold
            override val dokumentNavn = "Oversendelsesbrev til klager"
        }

        data object Avvist : Klage {
            override val pdfTemplate = PdfTemplate.Klage.Avvist
            override val dokumentNavn = "Avvist klage"
        }
    }

    data class Fritekst(val tittel: String) : PdfTemplateMedDokumentNavn {
        override val pdfTemplate = PdfTemplate.FritekstDokument
        override val dokumentNavn = tittel
    }
}
