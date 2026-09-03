package no.nav.su.se.bakover.service.historisk

import no.nav.su.se.bakover.domain.historisk.HistoriskRådataLeser
import no.nav.su.se.bakover.domain.historisk.InfotrygdTabeller
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskAldersberegning
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskAldersstønad
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskAldersvedtak
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBeløp
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBeløpstype
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBeslutning
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBosituasjon
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskDato
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskDelytelse
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskDelytelsestype
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskInntekt
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskKlassifiseringsnivå
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskKode
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskMånedsbeløp
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskOpphør
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskOpphørsgrunn
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskPeriode
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskResultat
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskRolle
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskSaksreferanse
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskSakstype
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskStønadId
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskStønadsklassifisering
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskSuDetalj
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskVedtakId
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * Konverterer Infotrygd-rådata fra et tapsfritt import-snapshot til vår typede aldersmodell.
 *
 * [konverterInfotrygdRådata] leser fra databasen batchvis slik at ikke hele datasettet lastes i minnet.
 * [konverterRådataBatch] brukes i tester og der hele datasettet allerede er tilgjengelig.
 */
class HistoriskAlderDataConverter {

    /**
     * Produksjonsmodus: leser stønader batchvis fra en fullført import via [leser].
     * Kodeverk-tabeller lastes én gang. Hver ferdig projisert batch sendes til [lagreBatch] og holdes ikke i minnet
     * etterpå, så minnebruken er begrenset til én batch om gangen. Returnerer kun et sammendrag
     * ([HistoriskAlderProjeksjonsresultat]) — selve stønadene persisteres av [lagreBatch].
     */
    fun konverterInfotrygdRådata(
        importId: UUID,
        leser: HistoriskRådataLeser,
        batchSize: Int = DEFAULT_BATCH_SIZE,
        lagreBatch: (List<HistoriskAldersstønad>) -> Unit,
    ): HistoriskAlderProjeksjonsresultat {
        leser.verifiserFullførtImport(importId)
        val avvik = mutableListOf<HistoriskAlderProjeksjonsavvik>()

        val kodeverk = lastKodeverk(importId, leser, avvik)
        var antallStønader = 0

        leser.hentStønaderBatchvis(importId, batchSize) { stønadsrader ->
            val normalisert = stønadsrader.map { it.normaliserKolonnenavn() }
            val stønadIder = normalisert.mapNotNull { it["STONAD_ID"]?.trim().takeUnless { v -> v.isNullOrEmpty() } }.toSet()

            val vedtakRader = leser.hentVedtakForStønader(importId, stønadIder)
                .map { it.normaliserKolonnenavn() }
                .medPåkrevdNøkkel(T_VEDTAK, "STONAD_ID", avvik)

            val vedtakPerStønad = vedtakRader.groupBy { it.getValue("STONAD_ID")!! }

            val vedtakIder = vedtakRader.mapNotNull { it["VEDTAK_ID"]?.trim().takeUnless { v -> v.isNullOrEmpty() } }.toSet()
            val raderPerVedtak = lastVedtaksdata(importId, leser, vedtakIder, avvik)

            val lopenummerFraStønader = normalisert.mapNotNull { it["PERSON_LOPENR"]?.trim().takeUnless { v -> v.isNullOrEmpty() } }.toSet()
            val lopenummerFraRoller = raderPerVedtak.roller.values.flatten()
                .mapNotNull { it["PERSON_LOPENR_R"]?.trim().takeUnless { v -> v.isNullOrEmpty() } }.toSet()
            val lopenummerFraDelytelser = raderPerVedtak.delytelser.values.flatten()
                .mapNotNull { it["MOTTAKER_LOPENR"]?.trim().takeUnless { v -> v.isNullOrEmpty() } }.toSet()
            val personer = leser.hentPersonerForLopenummer(
                importId,
                lopenummerFraStønader + lopenummerFraRoller + lopenummerFraDelytelser,
            )

            val batch = normalisert.mapNotNull { stønadsrad ->
                konverterRådataTilModell(stønadsrad, vedtakPerStønad, kodeverk, raderPerVedtak, personer, avvik)
            }
            if (batch.isNotEmpty()) {
                lagreBatch(batch)
                antallStønader += batch.size
            }
        }

        return HistoriskAlderProjeksjonsresultat(
            antallStønader = antallStønader,
            avvik = avvik,
            forbehold = HistoriskAlderForbehold.entries.toSet(),
        )
    }

    /**
     * Test-/in-memory-modus: konverterer et komplett datasett som allerede er lastet i minnet.
     */
    fun konverterRådataBatch(raderPerTabell: Map<String, List<Map<String, String?>>>): HistoriskAlderProjeksjon {
        val avvik = mutableListOf<HistoriskAlderProjeksjonsavvik>()
        val tabeller = raderPerTabell
            .mapValues { (_, rader) -> rader.map { it.normaliserKolonnenavn() } }

        PÅKREVDE_TABELLER.filterNot(tabeller::containsKey).forEach {
            avvik.add(HistoriskAlderProjeksjonsavvik.ManglendeTabell(it))
        }

        val kodeverk = byggKodeverk(tabeller, avvik)
        val personer = tabeller.indexerPå(T_LOPENR_FNR, "PERSON_LOPENR", avvik)

        val vedtakPerStønad = tabeller.rader(T_VEDTAK).medPåkrevdNøkkel(
            tabellnavn = T_VEDTAK,
            kolonne = "STONAD_ID",
            avvik = avvik,
        ).groupBy { it.getValue("STONAD_ID")!! }

        val raderPerVedtak = PerVedtak(
            stønadsKlasser = tabeller.grupperPåVedtak(T_STONADSKLASSE, avvik),
            roller = tabeller.grupperPåVedtak(T_ROLLE, avvik),
            suDetaljer = tabeller.grupperPåVedtak(T_SU, avvik),
            inntekter = tabeller.grupperPåVedtak(T_BEREGN_GRL, avvik),
            delytelser = tabeller.grupperPåVedtak(T_DELYTELSE, avvik),
            endringer = tabeller.grupperPåVedtak(T_ENDRING, avvik),
            beslutninger = tabeller.grupperPåVedtak(T_BESLUT, avvik),
        )

        val stønader = tabeller.rader(T_STONAD).mapNotNull { stønadsrad ->
            konverterRådataTilModell(stønadsrad, vedtakPerStønad, kodeverk, raderPerVedtak, personer, avvik)
        }

        val kjenteStønadIder = stønader.map { it.stønadId.value }.toSet()
        (vedtakPerStønad.keys - kjenteStønadIder).forEach {
            avvik.add(HistoriskAlderProjeksjonsavvik.ForeldreløsReferanse(T_VEDTAK, "STONAD_ID", it))
        }

        return HistoriskAlderProjeksjon(
            stønader = stønader.sortedBy { it.stønadId.value },
            avvik = avvik,
            forbehold = HistoriskAlderForbehold.entries.toSet(),
        )
    }

    private fun lastKodeverk(
        importId: UUID,
        leser: HistoriskRådataLeser,
        avvik: MutableList<HistoriskAlderProjeksjonsavvik>,
    ): Kodeverk {
        val beløpstyper = leser.hentReferansetabell(importId, InfotrygdTabeller.T_BELOPSTYPE)
            .map { it.normaliserKolonnenavn() }
            .indexerPåListe("TYPE", T_BELOPSTYPE, avvik)
        val delytelsestyper = leser.hentReferansetabell(importId, InfotrygdTabeller.T_DELYTELSESTYPE)
            .map { it.normaliserKolonnenavn() }
            .indexerPåListe("TYPE", T_DELYTELSESTYPE, avvik)
        val klassenivåer = leser.hentReferansetabell(importId, InfotrygdTabeller.T_KLASSENIVAA)
            .map { it.normaliserKolonnenavn() }
            .indexerPåListe("KODE", T_KLASSENIVAA, avvik)
        return Kodeverk(beløpstyper, delytelsestyper, klassenivåer)
    }

    private fun byggKodeverk(
        tabeller: Map<String, List<Rad>>,
        avvik: MutableList<HistoriskAlderProjeksjonsavvik>,
    ): Kodeverk {
        return Kodeverk(
            beløpstyper = tabeller.indexerPå(T_BELOPSTYPE, "TYPE", avvik),
            delytelsestyper = tabeller.indexerPå(T_DELYTELSESTYPE, "TYPE", avvik),
            klassenivåer = tabeller.indexerPå(T_KLASSENIVAA, "KODE", avvik),
        )
    }

    private fun lastVedtaksdata(
        importId: UUID,
        leser: HistoriskRådataLeser,
        vedtakIder: Set<String>,
        avvik: MutableList<HistoriskAlderProjeksjonsavvik>,
    ): PerVedtak {
        fun hentOgGrupperPåVedtak(tabellnavn: String): Map<String, List<Rad>> =
            leser.hentRaderForVedtak(importId, tabellnavn, vedtakIder)
                .map { it.normaliserKolonnenavn() }
                .medPåkrevdNøkkel(tabellnavn, "VEDTAK_ID", avvik)
                .groupBy { it.getValue("VEDTAK_ID")!! }
        return PerVedtak(
            stønadsKlasser = hentOgGrupperPåVedtak(InfotrygdTabeller.T_STONADSKLASSE),
            roller = hentOgGrupperPåVedtak(InfotrygdTabeller.T_ROLLE),
            suDetaljer = hentOgGrupperPåVedtak(InfotrygdTabeller.T_SU),
            inntekter = hentOgGrupperPåVedtak(InfotrygdTabeller.T_BEREGN_GRL),
            delytelser = hentOgGrupperPåVedtak(InfotrygdTabeller.T_DELYTELSE),
            endringer = hentOgGrupperPåVedtak(InfotrygdTabeller.T_ENDRING),
            beslutninger = hentOgGrupperPåVedtak(InfotrygdTabeller.T_BESLUT),
        )
    }

    private fun konverterRådataTilModell(
        stønadsrad: Rad,
        vedtakPerStønad: Map<String, List<Rad>>,
        kodeverk: Kodeverk,
        raderPerVedtak: PerVedtak,
        personer: Map<String, Map<String, String?>>,
        avvik: MutableList<HistoriskAlderProjeksjonsavvik>,
    ): HistoriskAldersstønad? {
        val stønadId = stønadsrad["STONAD_ID"]?.trim().takeUnless { it.isNullOrEmpty() }
        val personLøpenummer = stønadsrad["PERSON_LOPENR"]?.trim().takeUnless { it.isNullOrEmpty() }
        if (stønadId == null || personLøpenummer == null) {
            avvik.add(
                HistoriskAlderProjeksjonsavvik.ManglendeNøkkel(
                    tabell = T_STONAD,
                    kolonne = if (stønadId == null) "STONAD_ID" else "PERSON_LOPENR",
                ),
            )
            return null
        }

        val personident = personer[personLøpenummer]?.get("PERSONNR")?.trim()
        val opphørskode = stønadsrad["KODE_OPPHOR"]?.trim().takeUnless { it.isNullOrEmpty() }
        val opphørsdato = stønadsrad.historiskDato("DATO_OPPHOR", T_STONAD, stønadId, avvik)
        val vedtak = vedtakPerStønad[stønadId].orEmpty().mapNotNull {
            it.tilHistoriskVedtak(
                stønadId = HistoriskStønadId(stønadId),
                kodeverk = kodeverk,
                raderPerVedtak = raderPerVedtak,
                personer = personer,
                avvik = avvik,
            )
        }.sortedWith(compareBy({ it.periode.fraOgMed?.dato }, { it.vedtakId.value }))

        return HistoriskAldersstønad(
            stønadId = HistoriskStønadId(stønadId),
            personLøpenummer = personLøpenummer,
            personident = personident,
            startdato = stønadsrad.historiskDato("DATO_START", T_STONAD, stønadId, avvik),
            oppdragId = stønadsrad["OPPDRAG_ID"]?.trim(),
            opphør = if (opphørskode != null || opphørsdato != null) {
                HistoriskOpphør(
                    kode = kode(opphørskode.orEmpty(), ::tolkOpphørsgrunn, T_STONAD, "KODE_OPPHOR", avvik),
                    dato = opphørsdato,
                    registrertTidspunkt = stønadsrad["TIDSPUNKT_OPPHORT"],
                )
            } else {
                null
            },
            vedtak = vedtak,
        )
    }

    private fun Rad.tilHistoriskVedtak(
        stønadId: HistoriskStønadId,
        kodeverk: Kodeverk,
        raderPerVedtak: PerVedtak,
        personer: Map<String, Map<String, String?>>,
        avvik: MutableList<HistoriskAlderProjeksjonsavvik>,
    ): HistoriskAldersvedtak? {
        val vedtakId = this["VEDTAK_ID"]?.trim().takeUnless { it.isNullOrEmpty() }
        if (vedtakId == null) {
            avvik.add(HistoriskAlderProjeksjonsavvik.ManglendeNøkkel(T_VEDTAK, "VEDTAK_ID"))
            return null
        }

        val sakstype = this["TYPE_SAK"]?.trim().orEmpty()
        val resultat = this["KODE_RESULTAT"]?.trim().orEmpty()
        val delytelser = raderPerVedtak.delytelser[vedtakId].orEmpty().map { rad ->
            val typekode = rad["TYPE_DELYTELSE"]?.trim().orEmpty()
            val typerad = kodeverk.delytelsestyper[typekode]
            if (typerad == null) {
                avvik.add(HistoriskAlderProjeksjonsavvik.ManglerKodeverk(T_DELYTELSESTYPE, typekode))
            }
            val mottakerLøpenummer = rad["MOTTAKER_LOPENR"]?.trim()
            HistoriskDelytelse(
                type = HistoriskDelytelsestype(
                    kode = typekode,
                    tekst = typerad?.get("TEKST")?.trim(),
                    fradragEllerTillegg = typerad?.get("FRADRAG_TILLEGG")?.trim(),
                ),
                periode = rad.historiskPeriode("FOM", "TOM", T_DELYTELSE, vedtakId, avvik),
                beløp = rad.historiskBeløp("BELOP", T_DELYTELSE, vedtakId, avvik),
                mottakerLøpenummer = mottakerLøpenummer,
                mottakerPersonident = mottakerLøpenummer?.let { personer[it]?.get("PERSONNR")?.trim() },
                oppgjørsordning = rad["OPPGJORSORDNING"]?.trim(),
                satstype = rad["TYPE_SATS"]?.trim(),
                utbetalingstype = rad["TYPE_UTBETALING"]?.trim(),
                linjeId = rad["LINJE_ID"]?.trim(),
            )
        }
        return HistoriskAldersvedtak(
            vedtakId = HistoriskVedtakId(vedtakId),
            stønadId = stønadId,
            sakstype = kode(sakstype, ::tolkSakstype, T_VEDTAK, "TYPE_SAK", avvik),
            resultat = kode(resultat, ::tolkResultat, T_VEDTAK, "KODE_RESULTAT", avvik),
            periode = historiskPeriode("DATO_INNV_FOM", "DATO_INNV_TOM", T_VEDTAK, vedtakId, avvik),
            mottattDato = historiskDato("DATO_MOTTATT_SAK", T_VEDTAK, vedtakId, avvik),
            registrertTidspunkt = this["TIDSPUNKT_REG"],
            registrertAv = this["BRUKERID"]?.trim(),
            saksreferanse = HistoriskSaksreferanse(
                kontornummer = this["TKNR"]?.trim(),
                saksblokk = this["SAKSBLOKK"]?.trim(),
                saksnummer = this["SAKSNR"]?.trim(),
                behandlendeKontor = this["TKNR_BEH"]?.trim(),
            ),
            beregningstype = this["TYPE_BEREGNING"]?.trim(),
            nøkkelDl1 = this["NOKKEL_DL1"]?.trim(),
            klassifiseringer = raderPerVedtak.stønadsKlasser[vedtakId].orEmpty().map { rad ->
                val kode = rad["KODE_KLASSE"]?.trim().orEmpty()
                val nivå = rad["KODE_NIVAA"]?.trim().takeUnless { it.isNullOrEmpty() }
                HistoriskStønadsklassifisering(
                    nivå = nivå?.let {
                        HistoriskKlassifiseringsnivå(
                            kode = it,
                            tekst = kodeverk.klassenivåer[it]?.get("TEKST")?.trim(),
                        )
                    },
                    kode = kode,
                    bosituasjon =
                    if (nivå == "02") {
                        kode(
                            råverdi = kode,
                            tolk = ::tolkBosituasjon,
                            tabell = T_STONADSKLASSE,
                            kolonne = "KODE_KLASSE",
                            avvik = avvik,
                        ).tolketVerdi
                    } else {
                        null
                    },
                )
            },
            roller = raderPerVedtak.roller[vedtakId].orEmpty().map { rad ->
                val relatertLøpenummer = rad["PERSON_LOPENR_R"]?.trim()
                HistoriskRolle(
                    type = rad["TYPE"]?.trim().orEmpty(),
                    periode = rad.historiskPeriode("FOM", "TOM", T_ROLLE, vedtakId, avvik),
                    relatertPersonLøpenummer = relatertLøpenummer,
                    relatertPersonident = relatertLøpenummer?.let { personer[it]?.get("PERSONNR")?.trim() },
                    borSammenMed = rad["BOR_SAMMEN_MED"]?.trim(),
                )
            },
            beregning = HistoriskAldersberegning(
                suDetaljer = raderPerVedtak.suDetaljer[vedtakId].orEmpty().map { rad ->
                    HistoriskSuDetalj(
                        årligYtelsesbeløp = rad.historiskBeløp(
                            "BELOP_BER_GRUNNLAG",
                            T_SU,
                            vedtakId,
                            avvik,
                        ),
                        revurderingsdato = rad.historiskDato("REVURDERING_DATO", T_SU, vedtakId, avvik),
                        registrertTidspunkt = rad["TIDSPUNKT_REG"],
                    )
                },
                inntekter = raderPerVedtak.inntekter[vedtakId].orEmpty().map { rad ->
                    val typekode = rad["TYPE_BELOP"]?.trim().orEmpty()
                    val typerad = kodeverk.beløpstyper[typekode]
                    if (typerad == null) {
                        avvik.add(HistoriskAlderProjeksjonsavvik.ManglerKodeverk(T_BELOPSTYPE, typekode))
                    }
                    HistoriskInntekt(
                        type = HistoriskBeløpstype(
                            kode = typekode,
                            tekst = typerad?.get("TEKST")?.trim(),
                            behandling = typerad?.get("BEHANDLING")?.trim(),
                        ),
                        periode = rad.historiskPeriode("FOM", "TOM", T_BEREGN_GRL, vedtakId, avvik),
                        årligBeløp = rad.historiskBeløp("BELOP", T_BEREGN_GRL, vedtakId, avvik),
                        registrertTidspunkt = rad["TIDSPUNKT_REG"],
                    )
                },
                delytelser = delytelser,
                månedsbeløp = delytelser.tilMånedsbeløp(vedtakId, avvik),
            ),
            endringskoder = raderPerVedtak.endringer[vedtakId].orEmpty().mapNotNull { it["KODE"]?.trim() },
            beslutninger = raderPerVedtak.beslutninger[vedtakId].orEmpty().mapNotNull { rad ->
                val beslutningId = rad["BESLUTNING_ID"]?.trim().takeUnless { it.isNullOrEmpty() }
                if (beslutningId == null) {
                    avvik.add(HistoriskAlderProjeksjonsavvik.ManglendeNøkkel(T_BESLUT, "BESLUTNING_ID"))
                    null
                } else {
                    HistoriskBeslutning(
                        beslutningId = beslutningId,
                        førsteSaksbehandler = rad["SAKSBEHANDLER1"]?.trim(),
                        førsteGodkjenning = rad["GODKJENT1"]?.trim(),
                        førsteRegistreringstidspunkt = rad["TIDSPUNKT_REG1"],
                        andreSaksbehandler = rad["SAKSBEHANDLER2"]?.trim(),
                        andreGodkjenning = rad["GODKJENT2"]?.trim(),
                        andreRegistreringstidspunkt = rad["TIDSPUNKT_REG2"],
                        sendtTilOs = rad["SENDT_TIL_OS"],
                        mottattFraOs = rad["MOTTATT_FRA_OS"],
                        godkjentAvOs = rad["GODKJENT_AV_OS"]?.trim(),
                    )
                }
            },
        )
    }

    private fun List<HistoriskDelytelse>.tilMånedsbeløp(
        vedtakId: String,
        avvik: MutableList<HistoriskAlderProjeksjonsavvik>,
    ): List<HistoriskMånedsbeløp> =
        groupBy { Delytelsesgruppe(it.periode, it.linjeId) }.mapNotNull { (gruppe, delytelser) ->
            val månedsatser = delytelser.filter { it.type.kode == "MS" }
            val fradrag = delytelser.filter { it.type.kode == "FM" }
            val andreTyper = delytelser.map { it.type.kode }.filterNot { it == "MS" || it == "FM" }.toSet()
            val harForventetFormat = månedsatser.all { it.harFormat("T") } && fradrag.all { it.harFormat("F") }

            if (månedsatser.size != 1 || fradrag.size > 1 || andreTyper.isNotEmpty() || !harForventetFormat) {
                avvik.add(
                    HistoriskAlderProjeksjonsavvik.UgyldigDelytelsesgruppe(
                        vedtakId = vedtakId,
                        fraOgMed = gruppe.periode.fraOgMed?.råverdi,
                        tilOgMed = gruppe.periode.tilOgMed?.råverdi,
                        linjeId = gruppe.linjeId,
                        antallMånedsatser = månedsatser.size,
                        antallFradrag = fradrag.size,
                        andreTyper = andreTyper,
                    ),
                )
                return@mapNotNull null
            }

            val fraOgMed = gruppe.periode.fraOgMed?.dato
            val tilOgMed = gruppe.periode.tilOgMed?.dato
            if (fraOgMed == null || (tilOgMed != null && fraOgMed > tilOgMed)) {
                avvik.add(
                    HistoriskAlderProjeksjonsavvik.UgyldigDelytelsesperiode(
                        vedtakId = vedtakId,
                        fraOgMed = gruppe.periode.fraOgMed?.råverdi,
                        tilOgMed = gruppe.periode.tilOgMed?.råverdi,
                        linjeId = gruppe.linjeId,
                    ),
                )
                return@mapNotNull null
            }

            val sats = månedsatser.single().beløp?.beløp
            val fradragsbeløp =
                if (fradrag.isEmpty()) {
                    BigDecimal.ZERO
                } else {
                    fradrag.single().beløp?.beløp
                }
            if (
                sats == null ||
                fradragsbeløp == null ||
                sats.signum() < 0 ||
                fradragsbeløp.signum() < 0 ||
                sats < fradragsbeløp
            ) {
                avvik.add(
                    HistoriskAlderProjeksjonsavvik.UgyldigDelytelsesbeløp(
                        vedtakId = vedtakId,
                        fraOgMed = gruppe.periode.fraOgMed?.råverdi,
                        tilOgMed = gruppe.periode.tilOgMed?.råverdi,
                        linjeId = gruppe.linjeId,
                        sats = sats,
                        fradrag = fradragsbeløp,
                    ),
                )
                return@mapNotNull null
            }

            HistoriskMånedsbeløp(
                periode = gruppe.periode,
                sats = sats,
                fradrag = fradragsbeløp,
                linjeId = gruppe.linjeId,
            )
        }

    private fun HistoriskDelytelse.harFormat(fortegn: String): Boolean =
        type.fradragEllerTillegg == fortegn && satstype == "M" && utbetalingstype == "L"

    private data class Delytelsesgruppe(
        val periode: HistoriskPeriode,
        val linjeId: String?,
    )

    private data class Kodeverk(
        val beløpstyper: Map<String, Rad>,
        val delytelsestyper: Map<String, Rad>,
        val klassenivåer: Map<String, Rad>,
    )

    private data class PerVedtak(
        val stønadsKlasser: Map<String, List<Rad>>,
        val roller: Map<String, List<Rad>>,
        val suDetaljer: Map<String, List<Rad>>,
        val inntekter: Map<String, List<Rad>>,
        val delytelser: Map<String, List<Rad>>,
        val endringer: Map<String, List<Rad>>,
        val beslutninger: Map<String, List<Rad>>,
    )

    companion object {
        const val DEFAULT_BATCH_SIZE = 10000

        private val T_BELOPSTYPE = InfotrygdTabeller.T_BELOPSTYPE
        private val T_BEREGN_GRL = InfotrygdTabeller.T_BEREGN_GRL
        private val T_BESLUT = InfotrygdTabeller.T_BESLUT
        private val T_DELYTELSE = InfotrygdTabeller.T_DELYTELSE
        private val T_DELYTELSESTYPE = InfotrygdTabeller.T_DELYTELSESTYPE
        private val T_ENDRING = InfotrygdTabeller.T_ENDRING
        private val T_KLASSENIVAA = InfotrygdTabeller.T_KLASSENIVAA
        private val T_LOPENR_FNR = InfotrygdTabeller.T_LOPENR_FNR
        private val T_ROLLE = InfotrygdTabeller.T_ROLLE
        private val T_STONAD = InfotrygdTabeller.T_STONAD
        private val T_STONADSKLASSE = InfotrygdTabeller.T_STONADSKLASSE
        private val T_SU = InfotrygdTabeller.T_SU
        private val T_VEDTAK = InfotrygdTabeller.T_VEDTAK

        private val PÅKREVDE_TABELLER = setOf(
            T_BELOPSTYPE,
            T_BEREGN_GRL,
            T_BESLUT,
            T_DELYTELSE,
            T_DELYTELSESTYPE,
            T_ENDRING,
            T_KLASSENIVAA,
            T_LOPENR_FNR,
            T_ROLLE,
            T_STONAD,
            T_STONADSKLASSE,
            T_SU,
            T_VEDTAK,
        )
    }
}

data class HistoriskAlderProjeksjon(
    val stønader: List<HistoriskAldersstønad>,
    val avvik: List<HistoriskAlderProjeksjonsavvik>,
    val forbehold: Set<HistoriskAlderForbehold>,
)

/**
 * Sammendrag fra batchvis produksjonskonvertering. Selve stønadene persisteres underveis via lagreBatch-konsumenten,
 * så resultatet inneholder kun antall projiserte stønader pluss avvik og forbehold.
 */
data class HistoriskAlderProjeksjonsresultat(
    val antallStønader: Int,
    val avvik: List<HistoriskAlderProjeksjonsavvik>,
    val forbehold: Set<HistoriskAlderForbehold>,
)

enum class HistoriskAlderForbehold {
    FAKTISK_UTBETALING_MÅ_EVENTUELT_HENTES_FRA_OS_ELLER_UR,
}

sealed interface HistoriskAlderProjeksjonsavvik {
    data class ManglendeTabell(val tabell: String) : HistoriskAlderProjeksjonsavvik
    data class ManglendeNøkkel(val tabell: String, val kolonne: String) : HistoriskAlderProjeksjonsavvik
    data class DuplikatNøkkel(val tabell: String, val kolonne: String, val verdi: String) : HistoriskAlderProjeksjonsavvik

    data class ForeldreløsReferanse(val tabell: String, val kolonne: String, val verdi: String) : HistoriskAlderProjeksjonsavvik

    data class UkjentKode(val tabell: String, val kolonne: String, val verdi: String) : HistoriskAlderProjeksjonsavvik

    data class ManglerKodeverk(val tabell: String, val kode: String) : HistoriskAlderProjeksjonsavvik
    data class UgyldigDato(val tabell: String, val kolonne: String, val referanse: String, val verdi: String) : HistoriskAlderProjeksjonsavvik

    data class UgyldigBeløp(val tabell: String, val kolonne: String, val referanse: String, val verdi: String) : HistoriskAlderProjeksjonsavvik

    data class UgyldigDelytelsesgruppe(
        val vedtakId: String,
        val fraOgMed: String?,
        val tilOgMed: String?,
        val linjeId: String?,
        val antallMånedsatser: Int,
        val antallFradrag: Int,
        val andreTyper: Set<String>,
    ) : HistoriskAlderProjeksjonsavvik

    data class UgyldigDelytelsesperiode(
        val vedtakId: String,
        val fraOgMed: String?,
        val tilOgMed: String?,
        val linjeId: String?,
    ) : HistoriskAlderProjeksjonsavvik

    data class UgyldigDelytelsesbeløp(
        val vedtakId: String,
        val fraOgMed: String?,
        val tilOgMed: String?,
        val linjeId: String?,
        val sats: BigDecimal?,
        val fradrag: BigDecimal?,
    ) : HistoriskAlderProjeksjonsavvik
}

private typealias Rad = Map<String, String?>

private fun Rad.normaliserKolonnenavn(): Rad = entries.associate { it.key.uppercase() to it.value }

private fun Map<String, List<Rad>>.rader(tabellnavn: String): List<Rad> = this[tabellnavn].orEmpty()

private fun Map<String, List<Rad>>.indexerPå(
    tabellnavn: String,
    kolonne: String,
    avvik: MutableList<HistoriskAlderProjeksjonsavvik>,
): Map<String, Rad> {
    return rader(tabellnavn).indexerPåListe(kolonne, tabellnavn, avvik)
}

private fun List<Rad>.indexerPåListe(
    kolonne: String,
    tabellnavn: String,
    avvik: MutableList<HistoriskAlderProjeksjonsavvik>,
): Map<String, Rad> {
    return medPåkrevdNøkkel(tabellnavn, kolonne, avvik).groupBy { it.getValue(kolonne)!! }
        .mapValues { (nøkkel, rader) ->
            if (rader.size > 1) {
                avvik.add(HistoriskAlderProjeksjonsavvik.DuplikatNøkkel(tabellnavn, kolonne, nøkkel))
            }
            rader.first()
        }
}

private fun Map<String, List<Rad>>.grupperPåVedtak(
    tabellnavn: String,
    avvik: MutableList<HistoriskAlderProjeksjonsavvik>,
): Map<String, List<Rad>> = rader(tabellnavn).medPåkrevdNøkkel(tabellnavn, "VEDTAK_ID", avvik)
    .groupBy { it.getValue("VEDTAK_ID")!! }

private fun List<Rad>.medPåkrevdNøkkel(
    tabellnavn: String,
    kolonne: String,
    avvik: MutableList<HistoriskAlderProjeksjonsavvik>,
): List<Rad> = mapNotNull { rad ->
    val verdi = rad[kolonne]?.trim().takeUnless { it.isNullOrEmpty() }
    if (verdi == null) {
        avvik.add(HistoriskAlderProjeksjonsavvik.ManglendeNøkkel(tabellnavn, kolonne))
        null
    } else {
        rad + (kolonne to verdi)
    }
}

private fun Rad.historiskPeriode(
    fraOgMedKolonne: String,
    tilOgMedKolonne: String,
    tabell: String,
    referanse: String,
    avvik: MutableList<HistoriskAlderProjeksjonsavvik>,
) = HistoriskPeriode(
    fraOgMed = historiskDato(fraOgMedKolonne, tabell, referanse, avvik),
    tilOgMed = historiskDato(tilOgMedKolonne, tabell, referanse, avvik),
)

private fun Rad.historiskDato(
    kolonne: String,
    tabell: String,
    referanse: String,
    avvik: MutableList<HistoriskAlderProjeksjonsavvik>,
): HistoriskDato? {
    val råverdi = this[kolonne]?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
    val dato = runCatching { LocalDate.parse(råverdi.take(10)) }.getOrNull()
    if (dato == null) {
        avvik.add(HistoriskAlderProjeksjonsavvik.UgyldigDato(tabell, kolonne, referanse, råverdi))
    }
    return HistoriskDato(råverdi = råverdi, dato = dato)
}

private fun Rad.historiskBeløp(
    kolonne: String,
    tabell: String,
    referanse: String,
    avvik: MutableList<HistoriskAlderProjeksjonsavvik>,
): HistoriskBeløp? {
    val råverdi = this[kolonne]?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
    val beløp = råverdi.toBigDecimalOrNull()
    if (beløp == null) {
        avvik.add(HistoriskAlderProjeksjonsavvik.UgyldigBeløp(tabell, kolonne, referanse, råverdi))
    }
    return HistoriskBeløp(råverdi = råverdi, beløp = beløp)
}

private fun <T> kode(
    råverdi: String,
    tolk: (String) -> T?,
    tabell: String,
    kolonne: String,
    avvik: MutableList<HistoriskAlderProjeksjonsavvik>,
): HistoriskKode<T> {
    val tolket = tolk(råverdi)
    if (råverdi.isNotBlank() && tolket == null) {
        avvik.add(HistoriskAlderProjeksjonsavvik.UkjentKode(tabell, kolonne, råverdi))
    }
    return HistoriskKode(råverdi = råverdi, tolketVerdi = tolket)
}

private fun tolkSakstype(kode: String): HistoriskSakstype? = when (kode) {
    "S" -> HistoriskSakstype.SØKNAD
    "R" -> HistoriskSakstype.REVURDERING
    "MG" -> HistoriskSakstype.MASKINELL_OMREGNING
    "MO" -> HistoriskSakstype.MANUELL_OMREGNING
    "GO" -> HistoriskSakstype.MANUELL_G_REGULERING
    "MS" -> HistoriskSakstype.MASKINELL_SATSOMREGNING
    "MB" -> HistoriskSakstype.MASKINELL_BEREGNING
    "FL" -> HistoriskSakstype.FLYTTESAK
    "K" -> HistoriskSakstype.KLAGE
    else -> null
}

private fun tolkResultat(kode: String): HistoriskResultat? = when (kode) {
    "I" -> HistoriskResultat.INNVILGET
    "DI" -> HistoriskResultat.DELVIS_INNVILGET
    "FI" -> HistoriskResultat.FORTSATT_INNVILGET
    "IN" -> HistoriskResultat.INNVILGET_NY_SITUASJON
    "Ø" -> HistoriskResultat.ØKNING
    "R" -> HistoriskResultat.REDUSERT
    "O" -> HistoriskResultat.OPPHØRT
    "U" -> HistoriskResultat.UENDRET
    "A" -> HistoriskResultat.AVSLÅTT
    "AN" -> HistoriskResultat.ANNULLERT
    else -> null
}

private fun tolkBosituasjon(kode: String): HistoriskBosituasjon? = when (kode) {
    "EN" -> HistoriskBosituasjon.ENSLIG
    "EO" -> HistoriskBosituasjon.EPS_OVER_67
    "EU" -> HistoriskBosituasjon.EPS_UNDER_67
    "EV" -> HistoriskBosituasjon.ENSLIG_MED_BOFELLESSKAP
    else -> null
}

private fun tolkOpphørsgrunn(kode: String): HistoriskOpphørsgrunn? = when (kode) {
    "AN" -> HistoriskOpphørsgrunn.ANNULLERT
    "AP" -> HistoriskOpphørsgrunn.ALDERSPENSJON
    "AÅ" -> HistoriskOpphørsgrunn.ANNEN_ÅRSAK
    "FL" -> HistoriskOpphørsgrunn.FLYTTET
    "HI" -> HistoriskOpphørsgrunn.HØY_INNTEKT
    "IN" -> HistoriskOpphørsgrunn.INSTITUSJON
    "LU" -> HistoriskOpphørsgrunn.LANGT_UTENLANDSOPPHOLD
    "SF" -> HistoriskOpphørsgrunn.STOR_FORMUE
    "UT" -> HistoriskOpphørsgrunn.FLYTTET_TIL_UTLANDET
    "DØ" -> HistoriskOpphørsgrunn.DØD
    "UA" -> HistoriskOpphørsgrunn.UTENLANDSK_ADRESSE_ELLER_GIRONUMMER
    else -> null
}
