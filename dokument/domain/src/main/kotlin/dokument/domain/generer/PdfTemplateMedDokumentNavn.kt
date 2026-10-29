package dokument.domain.generer

// TODO jah: Flytt disse til de respektive plassene (må fjerne sealed). Bytt til interface.
sealed interface PdfTemplateMedDokumentNavn {
    val dokumentMal: DokumentMal

    /** navnet vi bruker i gosys og journalføring */
    val dokumentNavn: String

    data object Søknad : PdfTemplateMedDokumentNavn {
        override val dokumentMal = DokumentMal.Søknad
        override val dokumentNavn = "Søknad om supplerende stønad"
    }

    data object InnvilgetVedtak : PdfTemplateMedDokumentNavn {
        override val dokumentMal = DokumentMal.InnvilgetVedtak
        override val dokumentNavn = "Vedtaksbrev for søknad om supplerende stønad"
    }

    data object AvslagsVedtak : PdfTemplateMedDokumentNavn {
        override val dokumentMal = DokumentMal.AvslagsVedtak
        override val dokumentNavn = "Avslag supplerende stønad"
    }

    data object TrukketSøknad : PdfTemplateMedDokumentNavn {
        override val dokumentMal = DokumentMal.TrukketSøknad
        override val dokumentNavn = "Bekrefter at søknad er trukket"
    }

    data object AvvistSøknadVedtak : PdfTemplateMedDokumentNavn {
        override val dokumentMal = DokumentMal.AvvistSøknadVedtak
        override val dokumentNavn = "Søknaden din om supplerende stønad er avvist"
    }

    data object AvvistSøknadFritekst : PdfTemplateMedDokumentNavn {
        override val dokumentMal = DokumentMal.AvvistSøknadFritekst
        override val dokumentNavn = "Søknaden din om supplerende stønad er avvist"
    }

    sealed interface Opphør : PdfTemplateMedDokumentNavn {
        data object Opphørsvedtak : Opphør {
            override val dokumentMal = DokumentMal.Opphørsvedtak
            override val dokumentNavn = "Vedtak om opphør av supplerende stønad"
        }
    }

    data object Forhåndsvarsel : PdfTemplateMedDokumentNavn {
        override val dokumentMal = DokumentMal.Forhåndsvarsel
        override val dokumentNavn = "Varsel om at vi vil ta opp stønaden din til ny vurdering"
    }

    data object ForhåndsvarselTilbakekrevingsbehandling : PdfTemplateMedDokumentNavn {
        override val dokumentMal = DokumentMal.ForhåndsvarselTilbakekrevingsbehandling
        override val dokumentNavn = "Varsel om mulig tilbakekreving"
    }

    data object VedtaksbrevTilbakekrevingsbehandling : PdfTemplateMedDokumentNavn {
        override val dokumentMal = DokumentMal.VedtaksbrevTilbakekrevingsbehandling
        override val dokumentNavn = "Tilbakekreving av Supplerende stønad"
    }

    data object ForhåndsvarselTilbakekrevingsbehandlingDødsbo : PdfTemplateMedDokumentNavn {
        override val dokumentMal = DokumentMal.ForhåndsvarselTilbakekrevingsbehandlingDødsbo
        override val dokumentNavn = "Varsel om mulig tilbakekreving til dødsbo"
    }

    data object VedtaksbrevTilbakekrevingsbehandlingDødsbo : PdfTemplateMedDokumentNavn {
        override val dokumentMal = DokumentMal.VedtaksbrevTilbakekrevingsbehandlingDødsbo
        override val dokumentNavn = "Tilbakekreving av Supplerende stønad til dødsbo"
    }

    data object InnkallingTilKontrollsamtale : PdfTemplateMedDokumentNavn {
        override val dokumentMal = DokumentMal.InnkallingTilKontrollsamtale
        override val dokumentNavn = "Supplerende stønad – innkalling til samtale"
    }

    data object PåminnelseNyStønadsperiode : PdfTemplateMedDokumentNavn {
        override val dokumentMal = DokumentMal.PåminnelseNyStønadsperiode
        override val dokumentNavn = "Påminnelse om ny stønadsperiode"
    }

    sealed interface Revurdering : PdfTemplateMedDokumentNavn {
        data object Inntekt : Revurdering {
            override val dokumentMal = DokumentMal.Revurdering.Inntekt
            override val dokumentNavn = "Vi har vurdert den supplerende stønaden din på nytt"
        }

        data object AvsluttRevurdering : Revurdering {
            override val dokumentMal = DokumentMal.Revurdering.Avslutt
            override val dokumentNavn = "Ikke grunnlag for revurdering"
        }
    }

    data object VedtakReguleringAldersfradrag : PdfTemplateMedDokumentNavn {
        override val dokumentMal = DokumentMal.VedtaksbrevReguleringAldersfradrag
        override val dokumentNavn = "Vedtak for regulering etter endret alderspensjon"
    }

    sealed interface Klage : PdfTemplateMedDokumentNavn {
        data object Oppretthold : Klage {
            override val dokumentMal = DokumentMal.Klage.Oppretthold
            override val dokumentNavn = "Oversendelsesbrev til klager"
        }

        data object Avvist : Klage {
            override val dokumentMal = DokumentMal.Klage.Avvist
            override val dokumentNavn = "Avvist klage"
        }
    }

    data class Fritekst(val tittel: String) : PdfTemplateMedDokumentNavn {
        override val dokumentMal = DokumentMal.FritekstDokument
        override val dokumentNavn = tittel
    }

    data object Kontrollnotat : PdfTemplateMedDokumentNavn {
        override val dokumentMal = DokumentMal.Kontrollnotat
        override val dokumentNavn = "Kontrollnotat"
    }
}
