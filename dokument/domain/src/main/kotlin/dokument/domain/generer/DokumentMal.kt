package dokument.domain.generer

/**
 * En dokumentmal som brukes til lage innhold til dokumenter.
 * Brukes til generering av pdfer og for journalføring.
 *
 * Hver mal tilsvarer en template definert i pdf-generatoren frem til det erstattes av PdfBygger.
 *
 * @property navn Navn på malen slik den identifiseres mot pdf-generator. Vil kunne fjernes når vi er helt over på PdfBygger.
 *
 * @property tittel Tittel brukt til journalføring og visning i Gosys.
 *
 * Felter for å styre tilgjengeligjøring i ulike miljløer når maler er under utvikling. Vil fjernes når alle er klare for produksjon.
 * @property pdfByggerDev Om malen kan brukes mot pdf-bygger i dev-miljø.
 * @property pdfByggerProd Om malen kan brukes mot pdf-bygger i prod-miljø.
 */
sealed interface DokumentMal {
    val navn: String
    val tittel: String
    val pdfByggerDev: Boolean
    val pdfByggerProd: Boolean

    // Dokumenter som sendes inn fra lokalkontor av veileder
    sealed interface Lokalkontor : DokumentMal {
        data object Søknad : Lokalkontor {
            override val navn = "soknad"
            override val tittel = "Søknad om supplerende stønad"
            override val pdfByggerDev: Boolean = true
            override val pdfByggerProd: Boolean = true
        }

        data object Kontrollnotat : Lokalkontor {
            override val navn = "kontrollnotat"
            override val tittel = "Kontrollnotat"
            override val pdfByggerDev: Boolean = true
            override val pdfByggerProd: Boolean = true
        }
    }

    // Brev som brukes i sammenheng med behandling av en søknad
    sealed interface Søknadsbehandling : DokumentMal {
        data object InnvilgetVedtak : Søknadsbehandling {
            override val navn = "vedtakInnvilgelse"
            override val tittel = "Vedtaksbrev for søknad om supplerende stønad"
            override val pdfByggerDev: Boolean = true
            override val pdfByggerProd: Boolean = true
        }

        data object AvslagsVedtak : Søknadsbehandling {
            override val navn = "vedtakAvslag"
            override val tittel = "Avslag supplerende stønad"
            override val pdfByggerDev: Boolean = true
            override val pdfByggerProd: Boolean = true
        }

        data object TrukketSøknad : Søknadsbehandling {
            override val navn = "søknadTrukket"
            override val tittel = "Bekrefter at søknad er trukket"
            override val pdfByggerDev: Boolean = true
            override val pdfByggerProd: Boolean = true
        }

        // Brukes kun til avvisning uten behandlingsflyt pga manglende dokumentasjon fra bruker
        data object AvvistSøknadVedtak : Søknadsbehandling {
            override val navn = "avvistSøknadVedtak"
            override val tittel = "Søknaden din om supplerende stønad er avvist"
            override val pdfByggerDev: Boolean = true
            override val pdfByggerProd: Boolean = true
        }
    }

    // Brev som brukes i sammenheng med behandling av en endringer (revurderingslyt)
    sealed interface Revurdering : DokumentMal {
        data object Forhåndsvarsel : Revurdering {
            override val navn = "forhåndsvarsel"
            override val tittel = "Varsel om at vi vil ta opp din supplerende stønad til ny vurdering"
            override val pdfByggerDev: Boolean = true
            override val pdfByggerProd: Boolean = true
        }

        // TODO PdfBygger ryddejobb - endre navn fra revurderingAvInntekt da det kan være andre årsaker, litt misvisende
        data object Inntekt : Revurdering {
            override val navn = "revurderingAvInntekt"
            override val tittel = "Vi har vurdert den supplerende stønaden din på nytt"
            override val pdfByggerDev: Boolean = true
            override val pdfByggerProd: Boolean = true
        }

        data object Opphørsvedtak : Revurdering {
            override val navn = "opphørsvedtak"
            override val tittel = "Vedtak om opphør av supplerende stønad"
            override val pdfByggerDev: Boolean = true
            override val pdfByggerProd: Boolean = true
        }

        data object Avslutt : Revurdering {
            override val navn = "avsluttRevurdering"
            override val tittel = "Ikke grunnlag for revurdering"
            override val pdfByggerDev: Boolean = true
            override val pdfByggerProd: Boolean = true
        }
    }

    sealed interface Tilbakekreving {
        data class Forhåndsvarsel(
            val dødsbo: Boolean,
        ) : DokumentMal {
            // TODO PdfBygger ryddejobb - trenger ikke to navn etter su-pdfgen
            override val navn =
                if (dødsbo) "forhåndsvarselTilbakekrevingsbehandlingDødsbo" else "forhåndsvarselTilbakekrevingsbehandling"
            override val tittel =
                if (dødsbo) "Varsel om mulig tilbakekreving til dødsbo" else "Varsel om mulig tilbakekreving"
            override val pdfByggerDev: Boolean = true
            override val pdfByggerProd: Boolean = true
        }

        data class Vedtaksbrev(
            val dødsbo: Boolean,
        ) : DokumentMal {
            override val navn = if (dødsbo) {
                "vedtaksbrevTilbakekrevingsbehandlingDødsbo"
            } else {
                "vedtaksbrevTilbakekrevingsbehandling"
            }
            override val tittel = if (dødsbo) {
                "Tilbakekreving av Supplerende stønad til dødsbo"
            } else {
                "Tilbakekreving av Supplerende stønad"
            }
            override val pdfByggerDev: Boolean = true
            override val pdfByggerProd: Boolean = true
        }
    }

    sealed interface Klage : DokumentMal {
        data object Oppretthold : Klage {
            override val navn = "sendtTilKlageinstans"
            override val tittel = "Oversendelsesbrev til klager"
            override val pdfByggerDev: Boolean = true
            override val pdfByggerProd: Boolean = true
        }

        data object Avvist : Klage {
            override val navn = "avvistKlage"
            override val tittel = "Avvist klage"
            override val pdfByggerDev: Boolean = true
            override val pdfByggerProd: Boolean = true
        }
    }

    // Brev som produseres og sendes ut av tidsstyrt jobb
    sealed interface Tidsstyrt : DokumentMal {
        /**
         *  Se [no.nav.su.se.bakover.kontrollsamtale.infrastructure.jobs.KontrollsamtaleinnkallingJob]
         */
        data object InnkallingTilKontrollsamtale : Tidsstyrt {
            override val navn = "innkallingKontrollsamtale"
            override val tittel = "Supplerende stønad – innkalling til samtale"
            override val pdfByggerDev: Boolean = true
            override val pdfByggerProd: Boolean = true
        }

        /**
         *  Se [no.nav.su.se.bakover.web.services.SendPåminnelseNyStønadsperiodeJob]
         */
        data object PåminnelseNyStønadsperiode : Tidsstyrt {
            override val navn = "påminnelseOmNyStønadsperiode"
            override val tittel = "Påminnelse om ny stønadsperiode"
            override val pdfByggerDev: Boolean = true
            override val pdfByggerProd: Boolean = true
        }
    }

    data class FritekstDokument(val nyTittel: String) : DokumentMal {
        override val navn = "fritekstDokument"
        override val tittel = nyTittel
        override val pdfByggerDev: Boolean = true
        override val pdfByggerProd: Boolean = true
    }

    data class Skattegrunnlag(val nyTittel: String? = null) : DokumentMal {
        override val navn = "skattegrunnlag"
        override val tittel = nyTittel ?: "Skattegrunnlag"
        override val pdfByggerDev: Boolean = true
        override val pdfByggerProd: Boolean = true
    }

    data object VedtaksbrevReguleringAldersfradrag : DokumentMal {
        override val navn = "vedtaksbrevReguleringAldersfradrag"
        override val tittel = "Vedtak for regulering etter endret alderspensjon"
        override val pdfByggerDev: Boolean = false
        override val pdfByggerProd: Boolean = false
    }
}
