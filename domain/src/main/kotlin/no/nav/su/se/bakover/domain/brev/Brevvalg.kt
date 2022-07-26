package no.nav.su.se.bakover.domain.brev

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
    }

    /**
     * Tilfeller der vi alltid skal sende brev.
     * Kan også brukes i forbindelse med migrering.
     */
    sealed interface SkalSendeBrev : Brevvalg {
        override fun skalSendeBrev() = true

        /**
         * Tilfeller der vi alltid skal sende brev med fritekst.
         * Kan også brukes i forbindelse med migrering.
         *
         * @param begrunnelse Systemet sin begrunnelse for etterlevelse og evt. migrering.
         */
        data class MedFritekst(
            override val fritekst: String,
            override val begrunnelse: String,
        ) : SkalSendeBrev
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

            override fun skalSendeBrev() = true

            /**
             * Denne brevtypen har støtte for friktekst.
             * Her er det mulig å utvide med UtenFritekst, evt. gjøre feltet nullable i supertypen.
             */
            data class MedFritekst(
                override val fritekst: String,
                // TODO jah: Spør John Are om vi trenger en begrunnelse i dette tilfellet.
            ) : SkalSendeBrev {
                override val begrunnelse = null
            }
        }

        /**
         * Saksbehandler har valgt at det ikke skal sendes brev.
         * I første iterasjon krever vi en begrunnelse.
         * Dersom det i senere iterasjoner ikke ønskes begrunnelse kan den gjøres nullable eller splittes i 2 typer.
         */
        data class SkalIkkeSendeBrev(
            override val begrunnelse: String,
        ) : SaksbehandlersValg {
            override val fritekst = null
        }

        override fun skalSendeBrev() = false
    }
}
