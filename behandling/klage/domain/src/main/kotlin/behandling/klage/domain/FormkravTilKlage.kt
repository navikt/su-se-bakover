package behandling.klage.domain

import java.util.UUID

/**
 * Inneholder kun selve vilkårsvurderingene/formkravene som er gjort i forbindelse med en klage.
 * For selve klagen se [VilkårsvurdertKlage]
 */

/*
Versjonhistorikk - egentlig for hele klage men vi får se hvor vi putter den
1. Formkrav uten fremsattrettsligklageinteresse
2. Formkrav med fremsattrettsligklageinteresse

Settes ved opprettelse av klager.
 */
const val VERSJON = 2
sealed interface FormkravTilKlage {

    val vedtakId: UUID?
    val innenforFristen: SvarMedBegrunnelse?
    val klagesDetPåKonkreteElementerIVedtaket: BooleanMedBegrunnelse?
    val erUnderskrevet: SvarMedBegrunnelse?
    val fremsattRettsligKlageinteresse: SvarMedBegrunnelse?
    val infotrygdSakId: String?

    enum class Svarord {
        JA,
        NEI_MEN_SKAL_VURDERES,
        NEI,
    }

    data class SvarMedBegrunnelse(
        val svar: Svarord,
        val begrunnelse: String? = null,
    ) {
        override fun toString() = this.svar.toString()
    }

    data class BooleanMedBegrunnelse(
        val svar: Boolean,
        val begrunnelse: String? = null,
    ) {
        override fun toString() = this.svar.toString()
    }

    /**
     * Denne styrer om vi kommer til avvisningbildet i klageflyten og baserer seg på alle formkravene
     */
    fun erAvvist(): Boolean {
        return this.klagesDetPåKonkreteElementerIVedtaket?.svar == false ||
            this.innenforFristen?.svar == Svarord.NEI ||
            this.erUnderskrevet?.svar == Svarord.NEI ||
            this.fremsattRettsligKlageinteresse?.svar == Svarord.NEI
    }

    /**
     * Bruk [FormkravTilKlage.create] som returnerer [FormkravTilKlage.Utfylt] dersom alle feltene er utfylt, ellers [FormkravTilKlage.Påbegynt]
     */
    data class Påbegynt private constructor(
        override val vedtakId: UUID?,
        override val innenforFristen: SvarMedBegrunnelse?,
        override val klagesDetPåKonkreteElementerIVedtaket: BooleanMedBegrunnelse?,
        override val erUnderskrevet: SvarMedBegrunnelse?,
        override val fremsattRettsligKlageinteresse: SvarMedBegrunnelse?,
        override val infotrygdSakId: String?,
    ) : FormkravTilKlage {
        companion object {

            fun empty(): Påbegynt {
                // Går via create for å verifisere at vi bruker de samme reglene.
                return create(
                    vedtakId = null,
                    innenforFristen = null,
                    klagesDetPåKonkreteElementerIVedtaket = null,
                    erUnderskrevet = null,
                    fremsattRettsligKlageinteresse = null,
                    infotrygdSakId = null,
                ) as Påbegynt
            }

            /**
             * Denne styrer hvilken klagetype vi får se [VilkårsvurdertKlage] når man går videre i klageflyten.
             * @return [FormkravTilKlage.Utfylt] dersom alle feltene er utfylt, ellers [FormkravTilKlage.Påbegynt]
             */
            internal fun create(
                vedtakId: UUID?,
                innenforFristen: SvarMedBegrunnelse?,
                klagesDetPåKonkreteElementerIVedtaket: BooleanMedBegrunnelse?,
                erUnderskrevet: SvarMedBegrunnelse?,
                fremsattRettsligKlageinteresse: SvarMedBegrunnelse?,
                versjon: Int = VERSJON,
                infotrygdSakId: String?,
            ): FormkravTilKlage {
                val manglerVedtak = vedtakId == null && infotrygdSakId == null
                val erIkkeFerdigutfylt = if (versjon < VERSJON) {
                    manglerVedtak ||
                        innenforFristen == null ||
                        klagesDetPåKonkreteElementerIVedtaket == null ||
                        erUnderskrevet == null
                } else {
                    manglerVedtak ||
                        innenforFristen == null ||
                        klagesDetPåKonkreteElementerIVedtaket == null ||
                        erUnderskrevet == null ||
                        fremsattRettsligKlageinteresse == null
                }

                return if (!erIkkeFerdigutfylt) {
                    createUtfyltOnly(
                        vedtakId = vedtakId,
                        innenforFristen = innenforFristen!!,
                        klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket!!,
                        erUnderskrevet = erUnderskrevet!!,
                        fremsattRettsligKlageinteresse = fremsattRettsligKlageinteresse,
                        infotrygdSakId = infotrygdSakId,
                    )
                } else {
                    Påbegynt(
                        vedtakId = vedtakId,
                        innenforFristen = innenforFristen,
                        klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
                        erUnderskrevet = erUnderskrevet,
                        fremsattRettsligKlageinteresse = fremsattRettsligKlageinteresse,
                        infotrygdSakId = infotrygdSakId,
                    )
                }
            }
        }
    }

    data class Utfylt(
        override val vedtakId: UUID?,
        override val innenforFristen: SvarMedBegrunnelse,
        override val klagesDetPåKonkreteElementerIVedtaket: BooleanMedBegrunnelse,
        override val erUnderskrevet: SvarMedBegrunnelse,
        override val fremsattRettsligKlageinteresse: SvarMedBegrunnelse?,
        override val infotrygdSakId: String?,
    ) : FormkravTilKlage

    companion object {

        fun empty(): Påbegynt {
            return Påbegynt.empty()
        }

        private fun createUtfyltOnly(
            vedtakId: UUID?,
            innenforFristen: SvarMedBegrunnelse,
            klagesDetPåKonkreteElementerIVedtaket: BooleanMedBegrunnelse,
            erUnderskrevet: SvarMedBegrunnelse,
            fremsattRettsligKlageinteresse: SvarMedBegrunnelse?,
            infotrygdSakId: String?,
        ): FormkravTilKlage {
            return Utfylt(
                vedtakId = vedtakId,
                innenforFristen = innenforFristen,
                klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
                erUnderskrevet = erUnderskrevet,
                fremsattRettsligKlageinteresse = fremsattRettsligKlageinteresse,
                infotrygdSakId = infotrygdSakId,
            )
        }

        /**
         * @return [FormkravTilKlage.Utfylt] dersom alle feltene er utfylt, ellers [FormkravTilKlage.Påbegynt]
         */
        fun create(
            vedtakId: UUID?,
            innenforFristen: SvarMedBegrunnelse?,
            klagesDetPåKonkreteElementerIVedtaket: BooleanMedBegrunnelse?,
            erUnderskrevet: SvarMedBegrunnelse?,
            fremsattRettsligKlageinteresse: SvarMedBegrunnelse?,
            versjon: Int = VERSJON, // Skal kun benyttes settes fra KlagePostgresRepo
            infotrygdSakId: String? = null,
        ): FormkravTilKlage {
            return Påbegynt.create(
                vedtakId = vedtakId,
                innenforFristen = innenforFristen,
                klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
                erUnderskrevet = erUnderskrevet,
                fremsattRettsligKlageinteresse = fremsattRettsligKlageinteresse,
                versjon = versjon,
                infotrygdSakId = infotrygdSakId,
            )
        }
    }
}
