package dokument.domain.generer

/**
 * Oversikt over alle dokumentmaler og tittelen de journalføres med.
 *
 * Hver innholdsklasse ([PdfInnhold]) peker på sin mal. Den forseglede oversikten gjør det mulig å sjekke at
 * alle maler er dekket av tester (se `MalEgenskapTest`).
 *
 * @property tittel Tittel brukt til journalføring og visning i Gosys.
 */
sealed interface DokumentMal {
    val tittel: String

    // Dokumenter som sendes inn fra lokalkontor av veileder
    sealed interface Lokalkontor : DokumentMal {
        data object Søknad : Lokalkontor {
            override val tittel = "Søknad om supplerende stønad"
        }

        data object Kontrollnotat : Lokalkontor {
            override val tittel = "Kontrollnotat"
        }
    }

    // Brev som brukes i sammenheng med behandling av en søknad
    sealed interface Søknadsbehandling : DokumentMal {
        data object InnvilgetVedtak : Søknadsbehandling {
            override val tittel = "Vedtaksbrev for søknad om supplerende stønad"
        }

        data object AvslagsVedtak : Søknadsbehandling {
            override val tittel = "Avslag supplerende stønad"
        }

        data object TrukketSøknad : Søknadsbehandling {
            override val tittel = "Bekrefter at søknad er trukket"
        }

        // Brukes kun til avvisning uten behandlingsflyt pga manglende dokumentasjon fra bruker
        data object AvvistSøknadVedtak : Søknadsbehandling {
            override val tittel = "Søknaden din om supplerende stønad er avvist"
        }
    }

    // Brev som brukes i sammenheng med behandling av en endringer (revurderingslyt)
    sealed interface Revurdering : DokumentMal {
        data object Forhåndsvarsel : Revurdering {
            override val tittel = "Varsel om at vi vil ta opp din supplerende stønad til ny vurdering"
        }

        // TODO PdfBygger ryddejobb - endre navn fra Inntekt da det kan være andre årsaker, litt misvisende
        data object Inntekt : Revurdering {
            override val tittel = "Vi har vurdert den supplerende stønaden din på nytt"
        }

        data object Opphørsvedtak : Revurdering {
            override val tittel = "Vedtak om opphør av supplerende stønad"
        }

        data object Avslutt : Revurdering {
            override val tittel = "Ikke grunnlag for revurdering"
        }
    }

    sealed interface Tilbakekreving {
        data class Forhåndsvarsel(
            val dødsbo: Boolean,
        ) : DokumentMal {
            override val tittel =
                if (dødsbo) "Varsel om mulig tilbakekreving til dødsbo" else "Varsel om mulig tilbakekreving"
        }

        data class Vedtaksbrev(
            val dødsbo: Boolean,
        ) : DokumentMal {
            override val tittel = if (dødsbo) {
                "Tilbakekreving av Supplerende stønad til dødsbo"
            } else {
                "Tilbakekreving av Supplerende stønad"
            }
        }
    }

    sealed interface Klage : DokumentMal {
        data object Oppretthold : Klage {
            override val tittel = "Oversendelsesbrev til klager"
        }

        data object Avvist : Klage {
            override val tittel = "Avvist klage"
        }
    }

    // Brev som produseres og sendes ut av tidsstyrt jobb
    sealed interface Tidsstyrt : DokumentMal {
        /**
         *  Se [no.nav.su.se.bakover.kontrollsamtale.infrastructure.jobs.KontrollsamtaleinnkallingJob]
         */
        data object InnkallingTilKontrollsamtale : Tidsstyrt {
            override val tittel = "Supplerende stønad – innkalling til samtale"
        }

        /**
         *  Se [no.nav.su.se.bakover.web.services.SendPåminnelseNyStønadsperiodeJob]
         */
        data object PåminnelseNyStønadsperiode : Tidsstyrt {
            override val tittel = "Påminnelse om ny stønadsperiode"
        }
    }

    data class FritekstDokument(val nyTittel: String) : DokumentMal {
        override val tittel = nyTittel
    }

    data class Skattegrunnlag(val nyTittel: String? = null) : DokumentMal {
        override val tittel = nyTittel ?: "Skattegrunnlag"
    }

    data object VedtaksbrevReguleringAldersfradrag : DokumentMal {
        override val tittel = "Vedtak for regulering etter endret alderspensjon"
    }
}
