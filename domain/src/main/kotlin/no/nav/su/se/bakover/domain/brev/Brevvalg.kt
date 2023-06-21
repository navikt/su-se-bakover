package no.nav.su.se.bakover.domain.brev

import no.nav.su.se.bakover.domain.dokument.Dokumenttilstand

/**
 * Ved vedtak, lukking av behandling, forhåndsvarsling og andre handlinger ønsker vi 3 muligheter for brevsending:
 *  1. Send alltid brev.
 *  2. Saksbehandler velger om det skal sendes brev.
 *  3. Aldri send brev.
 *
 *  Tidligere har dette vært mer eller mindre hardkodet eller evt. blitt spesifisert per tilfelle.
 *  Dette er et forsøk på å ha det litt mer enhetlig.
 */
sealed interface Brevvalg {

    fun skalSendeBrev(): Boolean

    /**
     * Har kun mulighet å gi ut [Dokumenttilstand.SKAL_IKKE_GENERERE] & [Dokumenttilstand.IKKE_GENERERT_ENDA]
     */
    fun tilDokumenttilstand(): Dokumenttilstand

    /** null for de variantene som ikke skal sende brev og eventuelle brev som ikke har fritekst. */
    val fritekst: String?

    val begrunnelse: String?

    /**
     * Tilfeller der vi aldri skal sende brev, f.eks. dersom man har avsluttet en behandling som ikke skulle vært startet.
     * Kan også brukes i forbindelse ved migrering.
     *
     * @param begrunnelse Systemet sin begrunnelse for etterlevelse og evt. migrering.
     */
    data class SkalIkkeSendeBrev(
        override val begrunnelse: String,
    ) : Brevvalg {
        override fun skalSendeBrev() = false
        override val fritekst = null
        override fun tilDokumenttilstand() = Dokumenttilstand.SKAL_IKKE_GENERERE
        override fun toString(): String {
            // Vi ønsker ikke logge fritekst eller begrunnelse i tilfelle den inneholder sensitiv informasjon.
            return "SkalIkkeSendeBrev(begrunnelse='***')"
        }
    }

    /**
     * Tilfeller der vi alltid skal sende brev.
     * Kan også brukes i forbindelse med migrering.
     */
    sealed interface SkalSendeBrev : Brevvalg {
        override fun skalSendeBrev() = true
        override fun tilDokumenttilstand() = Dokumenttilstand.IKKE_GENERERT_ENDA

        /**
         * Tilfeller der vi alltid skal sende brev med fritekst.
         * Kan også brukes i forbindelse med migrering.
         *
         * @param begrunnelse Systemet sin begrunnelse for etterlevelse og evt. migrering.
         */
        data class InformasjonsbrevMedFritekst(
            override val fritekst: String,
            override val begrunnelse: String,
        ) : SkalSendeBrev {
            override fun toString(): String {
                // Vi ønsker ikke logge fritekst eller begrunnelse i tilfelle den inneholder sensitiv informasjon.
                return "InformasjonsbrevMedFritekst(fritekst='***', begrunnelse='***')"
            }
        }

        data class InformasjonsbrevUtenFritekst(
            override val begrunnelse: String? = null,
        ) : SkalSendeBrev {
            override val fritekst = null

            override fun toString(): String {
                // Vi ønsker ikke logge fritekst eller begrunnelse i tilfelle den inneholder sensitiv informasjon.
                return "InformasjonsbrevUtenFritekst(begrunnelse='***')"
            }
        }
    }

    /**
     * Saksbehandler velger om det skal sendes brev.
     * Bør ikke brukes dersom man skal migrere fra brevutsendingstilfeller der saksbehandler ikke hadde et valg.
     */
    sealed interface SaksbehandlersValg : Brevvalg {
        /**
         * Saksbehandler har valgt at det skal sendes brev.
         */
        sealed interface SkalSendeBrev : SaksbehandlersValg {
            override fun tilDokumenttilstand() = Dokumenttilstand.IKKE_GENERERT_ENDA
            override fun skalSendeBrev() = true

            /**
             * Denne brevtypen har støtte for friktekst.
             * Her er det mulig å utvide med UtenFritekst, evt. gjøre feltet nullable i supertypen.
             */
            data class InformasjonsbrevMedFritekst(
                override val fritekst: String,
                // TODO jah: Spør John Are om vi trenger en begrunnelse i dette tilfellet.
            ) : SkalSendeBrev {
                override val begrunnelse = null

                override fun toString(): String {
                    // Vi ønsker ikke logge fritekst eller begrunnelse i tilfelle den inneholder sensitiv informasjon.
                    return "InformasjonsbrevMedFritekst(fritekst='***')"
                }
            }

            sealed class Vedtaksbrev {
                data class UtenFritekst(
                    override val begrunnelse: String? = null,
                ) : SkalSendeBrev {
                    override val fritekst = null

                    override fun toString(): String {
                        // Vi ønsker ikke logge fritekst eller begrunnelse i tilfelle den inneholder sensitiv informasjon.
                        return "VedtaksbrevUtenFritekst(begrunnelse='***')"
                    }
                }

                data class MedFritekst(
                    override val begrunnelse: String? = null,
                    override val fritekst: String,
                ) : SkalSendeBrev {

                    override fun toString(): String {
                        // Vi ønsker ikke logge fritekst eller begrunnelse i tilfelle den inneholder sensitiv informasjon.
                        return "VedtaksbrevMedFritekst(begrunnelse='***')"
                    }
                }
            }
        }

        /**
         * Saksbehandler har valgt at det ikke skal sendes brev.
         */
        data class SkalIkkeSendeBrev(
            override val begrunnelse: String? = null,
        ) : SaksbehandlersValg {
            override val fritekst = null
            override fun tilDokumenttilstand() = Dokumenttilstand.SKAL_IKKE_GENERERE
            override fun toString(): String {
                // Vi ønsker ikke logge fritekst eller begrunnelse i tilfelle den inneholder sensitiv informasjon.
                return "SkalIkkeSendeBrev(begrunnelse='***')"
            }
        }

        override fun skalSendeBrev() = false
    }
}
