package satser.domain.supplerendestønad

import arrow.core.Nel
import arrow.core.nonEmptyListOf
import grunnbeløp.domain.GrunnbeløpFactory
import grunnbeløp.domain.Grunnbeløpsendring
import no.nav.su.se.bakover.common.domain.Faktor
import no.nav.su.se.bakover.common.domain.Knekkpunkt
import no.nav.su.se.bakover.common.domain.tid.erSortertOgUtenDuplikater
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.juli
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.domain.tid.september
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.toMåned
import satser.domain.SatsFactory
import satser.domain.garantipensjon.GarantipensjonFactory
import satser.domain.minsteårligytelseforuføretrygdede.MinsteÅrligYtelseForUføretrygdedeFactory
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * For alle hensyn er dette en factory of factories knyttet til en gitt dato man ønsker å regne seg ut fra.
 * Denne vil i de fleste tilfeller være nå.
 * Men for ting som allerede er vedtatt, må vi mulighet til å gå tilbake til vedtakstidspunktet og gjøre beregningen på nytt.
 *
 * Satsendringene må legges til i lista på en slik måte at neste endring, kansellerer forrige endring.
 * Per tidspunkt er alle virkningstidspunkt og ikrafttredelser i stigende rekkefølge, men det trenger ikke være tilfelle i framtiden.
 * Da må vi skrive om denne logikken. På sikt bør nok disse gjøres om til en lenket liste, som modellerer virkeligheten bedre.
 */

/**
 * kan finne litt info her https://www.nav.no/grunnbelopet
 */
val grunnbeløpsendringer = nonEmptyListOf(
    Grunnbeløpsendring(1.mai(2005), 1.mai(2005), 60699, BigDecimal(1.032682)),
    Grunnbeløpsendring(1.mai(2006), 1.mai(2006), 62892, BigDecimal(1.036129)),
    Grunnbeløpsendring(1.mai(2007), 1.mai(2007), 66812, BigDecimal(1.062329)),
    Grunnbeløpsendring(1.mai(2008), 1.mai(2008), 70256, BigDecimal(1.051548)),
    Grunnbeløpsendring(1.mai(2009), 1.mai(2009), 72881, BigDecimal(1.037363)),
    Grunnbeløpsendring(1.mai(2010), 1.mai(2010), 75641, BigDecimal(1.037870)),
    Grunnbeløpsendring(1.mai(2011), 1.mai(2011), 79216, BigDecimal(1.047263)),
    Grunnbeløpsendring(1.mai(2012), 1.mai(2012), 82122, BigDecimal(1.036685)),
    Grunnbeløpsendring(1.mai(2013), 1.mai(2013), 85245, BigDecimal(1.038029)),
    Grunnbeløpsendring(1.mai(2014), 1.mai(2014), 88370, BigDecimal(1.036659)),
    Grunnbeløpsendring(1.mai(2015), 1.mai(2015), 90068, BigDecimal(1.019214)),
    Grunnbeløpsendring(1.mai(2016), 1.mai(2016), 92576, BigDecimal(1.027846)),
    Grunnbeløpsendring(1.mai(2017), 1.mai(2017), 93634, BigDecimal(1.011428)),
    Grunnbeløpsendring(1.mai(2018), 1.mai(2018), 96883, BigDecimal(1.034699)),
    Grunnbeløpsendring(1.mai(2019), 1.mai(2019), 99858, BigDecimal(1.030707)),
    Grunnbeløpsendring(1.mai(2020), 4.september(2020), 101351, BigDecimal(1.014951)),
    Grunnbeløpsendring(1.mai(2021), 21.mai(2021), 106399, BigDecimal(1.049807)),
    Grunnbeløpsendring(1.mai(2022), 20.mai(2022), 111477, BigDecimal(1.047726)),
    Grunnbeløpsendring(1.mai(2023), 26.mai(2023), 118620, BigDecimal(1.064076)),
    Grunnbeløpsendring(1.mai(2024), 24.mai(2024), 124028, BigDecimal(1.045591)),
)

val garantipensjonsendringerOrdinær = nonEmptyListOf(
    // https://lovdata.no/forskrift/2015-11-20-1335/§1  kunngjort 23.11.2015 kl. 12.30
    GarantipensjonFactory.Garantipensjonsendring(1.januar(2016), 1.januar(2016), 162566),

    // https://lovdata.no/forskrift/2016-05-27-530/§5   kunngjort 30.05.2016 kl. 15.20
    GarantipensjonFactory.Garantipensjonsendring(1.mai(2016), 1.mai(2016), 166274),

    // https://lovdata.no/forskrift/2017-06-02-679/§5   kunngjort 02.06.2017 kl. 15.00
    GarantipensjonFactory.Garantipensjonsendring(1.mai(2017), 1.mai(2017), 167196),

    // https://lovdata.no/forskrift/2018-06-01-786/§5   kunngjort 01.06.2018 kl. 14.30
    GarantipensjonFactory.Garantipensjonsendring(1.mai(2018), 1.mai(2018), 172002),

    // https://lovdata.no/forskrift/2019-05-24-670/§5   kunngjort 24.05.2019 kl. 14.20
    GarantipensjonFactory.Garantipensjonsendring(1.mai(2019), 1.mai(2019), 176099),

    //  https://lovdata.no/forskrift/2020-09-04-1719/§5 kunngjort 04.09.2020 kl. 15.35
    GarantipensjonFactory.Garantipensjonsendring(1.mai(2020), 4.september(2020), 177724),

    // https://lovdata.no/forskrift/2021-05-21-1568/§5  kunngjort 21.05.2021 kl. 16.15
    GarantipensjonFactory.Garantipensjonsendring(1.mai(2021), 21.mai(2021), 187252),

    // https://lovdata.no/forskrift/2022-05-20-881/§5   kunngjort 20.05.2022 kl. 15.15
    GarantipensjonFactory.Garantipensjonsendring(1.mai(2022), 20.mai(2022), 193862),

    // https://lovdata.no/LTI/forskrift/2023-05-26-738/§5 kunngjort 26.05.2023 kl. 15.10
    GarantipensjonFactory.Garantipensjonsendring(1.mai(2023), 26.mai(2023), 210418),
)

val garantipensjonsendringerHøy = nonEmptyListOf(
    GarantipensjonFactory.Garantipensjonsendring(1.januar(2016), 1.januar(2016), 175739),
    GarantipensjonFactory.Garantipensjonsendring(1.mai(2016), 1.mai(2016), 179748),
    GarantipensjonFactory.Garantipensjonsendring(1.mai(2017), 1.mai(2017), 180744),
    GarantipensjonFactory.Garantipensjonsendring(1.mai(2018), 1.mai(2018), 185939),
    GarantipensjonFactory.Garantipensjonsendring(1.mai(2019), 1.mai(2019), 190368),
    GarantipensjonFactory.Garantipensjonsendring(1.mai(2020), 4.september(2020), 192125),
    GarantipensjonFactory.Garantipensjonsendring(1.mai(2021), 21.mai(2021), 202425),
    GarantipensjonFactory.Garantipensjonsendring(1.mai(2022), 20.mai(2022), 209571),
    GarantipensjonFactory.Garantipensjonsendring(1.mai(2023), 26.mai(2023), 227468),
)

// TODO - test
@JvmName("sisteVirkningstidspunktGarantipensjon")
fun Nel<GarantipensjonFactory.Garantipensjonsendring>.sisteVirkningstidspunkt(): Måned =
    this.maxByOrNull { it.virkningstidspunkt }!!.virkningstidspunkt.toMåned()

// TODO - test
@JvmName("sisteVirkningstidspunktMinsteÅrligYtelseForUføretrygdedeEndring")
fun Nel<MinsteÅrligYtelseForUføretrygdedeFactory.MinsteÅrligYtelseForUføretrygdedeEndring>.sisteVirkningstidspunkt(): Måned =
    this.maxByOrNull { it.virkningstidspunkt }!!.virkningstidspunkt.toMåned()

// TODO - test
@JvmName("sisteVirkningstidspunktGrunnbeløpsendring")
fun Nel<Grunnbeløpsendring>.sisteVirkningstidspunkt(): Måned =
    this.maxByOrNull { it.virkningstidspunkt }!!.virkningstidspunkt.toMåned()

class SatsFactoryForSupplerendeStønad(
    // TODO(satsfactory_alder) jah: I lov om supplerende stønad ble satsen for alder endret fra minste pensjonsnivå til garantipensjon fra og med 2021-01-01.
    //  Vi må legge inn minste pensjonsnivå og ta høyde for det før vi skal revurdere tilbake til før 2021-01-01.
    //  På grunn av testene må vi sette sperren til 2020 (TODO jah: fiks testene)
    private val tidligsteTilgjengeligeMåned: Måned = januar(2020),
    private val datoTilFactory: MutableMap<Knekkpunkt, SatsFactoryForSupplerendeStønadPåKnekkpunkt> = mutableMapOf(),
    /** Se kommentarer på garantipensjon lav for lovreferanser. */
    private val grunnbeløpsendringer: Nel<Grunnbeløpsendring> = satser.domain.supplerendestønad.grunnbeløpsendringer,
    /**
     * Garantipensjon ble innført som konsept 2016-01-01: https://lovdata.no/forskrift/2015-11-20-1335/§1.
     * Satsene endres ofte sammen med grunnbeløpet.
     */
    private val garantipensjonsendringerOrdinær: Nel<GarantipensjonFactory.Garantipensjonsendring> = satser.domain.supplerendestønad.garantipensjonsendringerOrdinær,
    /** Se kommentarer på garantipensjon lav for lovreferanser. */
    private val garantipensjonsendringerHøy: Nel<GarantipensjonFactory.Garantipensjonsendring> = satser.domain.supplerendestønad.garantipensjonsendringerHøy,
    private val minsteÅrligYtelseForUføretrygdedeOrdinær: Nel<MinsteÅrligYtelseForUføretrygdedeFactory.MinsteÅrligYtelseForUføretrygdedeEndring> = nonEmptyListOf(
        // https://lovdata.no/dokument/LTI/lov/2011-12-16-59 kunngjort 16.12.2011 kl. 15.40. Trådde i kraft 2015-01-01: https://lovdata.no/dokument/LTI/forskrift/2014-06-20-797 (kunngjort 23.06.2014 kl. 16.00)
        MinsteÅrligYtelseForUføretrygdedeFactory.MinsteÅrligYtelseForUføretrygdedeEndring(
            virkningstidspunkt = 1.januar(2015),
            ikrafttredelse = 1.januar(2015),
            faktor = Faktor(2.28),
        ),
        MinsteÅrligYtelseForUføretrygdedeFactory.MinsteÅrligYtelseForUføretrygdedeEndring(
            virkningstidspunkt = 1.juli(2024),
            ikrafttredelse = 1.juli(2024),
            faktor = Faktor(2.329),
        ),
    ),
    private val minsteÅrligYtelseForUføretrygdedeHøy: Nel<MinsteÅrligYtelseForUføretrygdedeFactory.MinsteÅrligYtelseForUføretrygdedeEndring> = nonEmptyListOf(
        // https://lovdata.no/dokument/LTI/lov/2011-12-16-59 kunngjort 16.12.2011 kl. 15.40 Trådde i kraft 2015-01-01: https://lovdata.no/dokument/LTI/forskrift/2014-06-20-797 (kunngjort 23.06.2014 kl. 16.00)
        MinsteÅrligYtelseForUføretrygdedeFactory.MinsteÅrligYtelseForUføretrygdedeEndring(
            virkningstidspunkt = 1.januar(2015),
            ikrafttredelse = 1.januar(2015),
            faktor = Faktor(2.48),
        ),
        MinsteÅrligYtelseForUføretrygdedeFactory.MinsteÅrligYtelseForUføretrygdedeEndring(
            virkningstidspunkt = 1.juli(2024),
            ikrafttredelse = 1.juli(2024),
            faktor = Faktor(2.529),
        ),
    ),
) {

    init {
        // Dersom vi får duplikate virkningstidspunkter i loven, vil vi måtte skrive om blant annet periodiser-funksjonen.
        grunnbeløpsendringer.map { it.ikrafttredelse }.erSortertOgUtenDuplikater()
        grunnbeløpsendringer.map { it.virkningstidspunkt }.erSortertOgUtenDuplikater()

        garantipensjonsendringerOrdinær.map { it.ikrafttredelse }.erSortertOgUtenDuplikater()
        garantipensjonsendringerOrdinær.map { it.virkningstidspunkt }.erSortertOgUtenDuplikater()

        garantipensjonsendringerHøy.map { it.ikrafttredelse }.erSortertOgUtenDuplikater()
        garantipensjonsendringerHøy.map { it.virkningstidspunkt }.erSortertOgUtenDuplikater()

        minsteÅrligYtelseForUføretrygdedeOrdinær.map { it.ikrafttredelse }.erSortertOgUtenDuplikater()
        minsteÅrligYtelseForUføretrygdedeOrdinær.map { it.virkningstidspunkt }.erSortertOgUtenDuplikater()

        minsteÅrligYtelseForUføretrygdedeHøy.map { it.ikrafttredelse }.erSortertOgUtenDuplikater()
        minsteÅrligYtelseForUføretrygdedeHøy.map { it.virkningstidspunkt }.erSortertOgUtenDuplikater()
    }

    private fun getOrAdd(knekkpunkt: Knekkpunkt): SatsFactoryForSupplerendeStønadPåKnekkpunkt {
        val måneder = Periode.create(tidligsteTilgjengeligeMåned.fraOgMed, hentSisteVirkningstidspunkt().tilOgMed).måneder()
        return datoTilFactory.getOrPut(knekkpunkt) {
            val grunnbeløpFactoryPåKnekkpunkt = GrunnbeløpFactory(
                grunnbeløpsendringer = grunnbeløpsendringer,
                knekkpunkt = knekkpunkt,
                tidligsteTilgjengeligeMåned = tidligsteTilgjengeligeMåned,
            )
            val minsteÅrligYtelseForUføretrygdedeFactoryPåKnekkpunkt =
                MinsteÅrligYtelseForUføretrygdedeFactory.createFromFaktorer(
                    ordinær = minsteÅrligYtelseForUføretrygdedeOrdinær,
                    høy = minsteÅrligYtelseForUføretrygdedeHøy,
                    knekkpunkt = knekkpunkt,
                    tidligsteTilgjengeligeMåned = tidligsteTilgjengeligeMåned,
                )
            SatsFactoryForSupplerendeStønadPåKnekkpunkt(
                grunnbeløpFactory = grunnbeløpFactoryPåKnekkpunkt,
                uføreOrdinær = FullSupplerendeStønadFactory.Ordinær.Ufør(
                    grunnbeløpFactory = grunnbeløpFactoryPåKnekkpunkt,
                    minsteÅrligYtelseForUføretrygdedeFactory = minsteÅrligYtelseForUføretrygdedeFactoryPåKnekkpunkt,
                    knekkpunkt = knekkpunkt,
                    tidligsteTilgjengeligeMåned = tidligsteTilgjengeligeMåned,
                    måneder = måneder,
                ),
                uføreHøy = FullSupplerendeStønadFactory.Høy.Ufør(
                    grunnbeløpFactory = grunnbeløpFactoryPåKnekkpunkt,
                    minsteÅrligYtelseForUføretrygdedeFactory = minsteÅrligYtelseForUføretrygdedeFactoryPåKnekkpunkt,
                    knekkpunkt = knekkpunkt,
                    tidligsteTilgjengeligeMåned = tidligsteTilgjengeligeMåned,
                    måneder = måneder,
                ),
                alderOrdinær = FullSupplerendeStønadFactory.Ordinær.Alder(
                    garantipensjonFactory = GarantipensjonFactory.createFromSatser(
                        ordinær = garantipensjonsendringerOrdinær,
                        høy = garantipensjonsendringerHøy,
                        knekkpunkt = knekkpunkt,
                        tidligsteTilgjengeligeMåned = tidligsteTilgjengeligeMåned,
                    ),
                    knekkpunkt = knekkpunkt,
                    tidligsteTilgjengeligeMåned = tidligsteTilgjengeligeMåned,
                    måneder = måneder,
                ),
                alderHøy = FullSupplerendeStønadFactory.Høy.Alder(
                    garantipensjonFactory = GarantipensjonFactory.createFromSatser(
                        ordinær = garantipensjonsendringerOrdinær,
                        høy = garantipensjonsendringerHøy,
                        knekkpunkt = knekkpunkt,
                        tidligsteTilgjengeligeMåned = tidligsteTilgjengeligeMåned,
                    ),
                    knekkpunkt = knekkpunkt,
                    tidligsteTilgjengeligeMåned = tidligsteTilgjengeligeMåned,
                    måneder = måneder,
                ),
                knekkpunkt = knekkpunkt,
                tidligsteTilgjengeligeMåned = tidligsteTilgjengeligeMåned,
            )
        }
    }

    fun gjeldende(
        clock: Clock,
        zoneId: ZoneId = zoneIdOslo,
    ): SatsFactory {
        return gjeldende(Tidspunkt.now(clock), zoneId)
    }

    fun gjeldende(
        tidspunkt: Tidspunkt,
        zoneId: ZoneId = zoneIdOslo,
    ): SatsFactory {
        return gjeldende(tidspunkt.toLocalDate(zoneId))
    }

    // TODO - test
    fun hentSisteVirkningstidspunkt(): Måned = maxOf(
        minsteÅrligYtelseForUføretrygdedeOrdinær.sisteVirkningstidspunkt(),
        minsteÅrligYtelseForUføretrygdedeHøy.sisteVirkningstidspunkt(),
        grunnbeløpsendringer.sisteVirkningstidspunkt(),
        garantipensjonsendringerOrdinær.sisteVirkningstidspunkt(),
        garantipensjonsendringerHøy.sisteVirkningstidspunkt(),
    )

    /**
     * Gir et [SatsFactory] som gjelder på en gitt dato (regnes om til nærmeste knekkpunkt).
     * Dette baserer seg på lovenes ikrafttredelsesdato.
     * Dette gir oss mulighet til å finne ut hvilke satser på som gjaldt på en gitt dato (f.eks. opprettettidspunktet på beregningen)
     */
    fun gjeldende(påDato: LocalDate): SatsFactory {
        return getOrAdd(
            // Dette vil være den nyeste ikrafttredelsesdatoen basert på de satsene som gjaldt på denne datoen.
            // Hver unike ikrafttredelsesdato er et knekkpunkt.
            // F.eks. sats for uføre er satt sammen av grunnbeløp og minste årlig ytelse for uføretrygdede den 2021-01-01.
            // Disse vil i teorien kunne ha forskjellige ikrafttredelsedatoen. F.eks. 2020-05-01 og 20220-10-01.
            // Da vil den nyeste av disse datoene være ikrafttredelsen til full supplerende stønad.
            knekkpunkt = Knekkpunkt(
                maxOf(
                    minsteÅrligYtelseForUføretrygdedeOrdinær.gjeldende(påDato).ikrafttredelse,
                    minsteÅrligYtelseForUføretrygdedeHøy.gjeldende(påDato).ikrafttredelse,
                    grunnbeløpsendringer.gjeldende(påDato).ikrafttredelse,
                    garantipensjonsendringerOrdinær.gjeldende(påDato).ikrafttredelse,
                    garantipensjonsendringerHøy.gjeldende(påDato).ikrafttredelse,
                ),
            ),
        )
    }

    private fun List<Grunnbeløpsendring>.gjeldende(påDato: LocalDate): Grunnbeløpsendring {
        return this.last { it.ikrafttredelse <= påDato }
    }

    private fun List<GarantipensjonFactory.Garantipensjonsendring>.gjeldende(påDato: LocalDate): GarantipensjonFactory.Garantipensjonsendring {
        return this.last { it.ikrafttredelse <= påDato }
    }

    private fun List<MinsteÅrligYtelseForUføretrygdedeFactory.MinsteÅrligYtelseForUføretrygdedeEndring>.gjeldende(påDato: LocalDate): MinsteÅrligYtelseForUføretrygdedeFactory.MinsteÅrligYtelseForUføretrygdedeEndring {
        return this.last { it.ikrafttredelse <= påDato }
    }
}
