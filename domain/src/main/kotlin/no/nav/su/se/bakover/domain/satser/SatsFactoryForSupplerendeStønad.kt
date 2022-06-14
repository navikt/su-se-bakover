package no.nav.su.se.bakover.domain.satser

import arrow.core.Nel
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.grunnbeløp.GrunnbeløpFactory
import no.nav.su.se.bakover.domain.grunnbeløp.Grunnbeløpsendring
import no.nav.su.se.bakover.domain.vilkår.FormuegrenserFactory
import java.time.LocalDate
import java.time.ZoneId

/**
 * For alle hensyn er dette en factory of factories knyttet til en gitt dato man ønsker å regne seg ut fra.
 * Denne vil i de fleste tilfeller være nå.
 * Men for ting som allerede er vedtatt, må vi mulighet til å gå tilbake til vedtakstidspunktet og gjøre beregningen på nytt.
 */
class SatsFactoryForSupplerendeStønad(
    private val datoTilFactory: MutableMap<LocalDate, SatsFactoryForSupplerendeStønadPåDato> = mutableMapOf(),
    /** Se kommentarer på garantipensjon lav for lovreferanser. */
    private val grunnbeløpsendringer: List<Grunnbeløpsendring> = listOf(
        Grunnbeløpsendring(1.mai(2005), 1.mai(2005), 60699),
        Grunnbeløpsendring(1.mai(2006), 1.mai(2006), 62892),
        Grunnbeløpsendring(1.mai(2007), 1.mai(2007), 66812),
        Grunnbeløpsendring(1.mai(2008), 1.mai(2008), 70256),
        Grunnbeløpsendring(1.mai(2009), 1.mai(2009), 72881),
        Grunnbeløpsendring(1.mai(2010), 1.mai(2010), 75641),
        Grunnbeløpsendring(1.mai(2011), 1.mai(2011), 79216),
        Grunnbeløpsendring(1.mai(2012), 1.mai(2012), 82122),
        Grunnbeløpsendring(1.mai(2013), 1.mai(2013), 85245),
        Grunnbeløpsendring(1.mai(2014), 1.mai(2014), 88370),
        Grunnbeløpsendring(1.mai(2015), 1.mai(2015), 90068),
        Grunnbeløpsendring(1.mai(2016), 1.mai(2016), 92576),
        Grunnbeløpsendring(1.mai(2017), 1.mai(2017), 93634),
        Grunnbeløpsendring(1.mai(2018), 1.mai(2018), 96883),
        Grunnbeløpsendring(1.mai(2019), 1.mai(2019), 99858),
        Grunnbeløpsendring(1.mai(2020), 4.september(2020), 101351),
        Grunnbeløpsendring(1.mai(2021), 21.mai(2021), 106399),
        Grunnbeløpsendring(1.mai(2022), 20.mai(2022), 111477),
    ),
    /**
     * Garantipensjon ble innført som konsept 2016-01-01: https://lovdata.no/forskrift/2015-11-20-1335/§1.
     * Satsene endres ofte sammen med grunnbeløpet.
     */
    private val garantipensjonsendringerOrdinær: List<GarantipensjonFactory.Garantipensjonsendring> = listOf(
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
    ),
    /** Se kommentarer på garantipensjon lav for lovreferanser. */
    private val garantipensjonsendringerHøy: List<GarantipensjonFactory.Garantipensjonsendring> = listOf(
        GarantipensjonFactory.Garantipensjonsendring(1.januar(2016), 1.januar(2016), 175739),
        GarantipensjonFactory.Garantipensjonsendring(1.mai(2016), 1.mai(2016), 179748),
        GarantipensjonFactory.Garantipensjonsendring(1.mai(2017), 1.mai(2017), 180744),
        GarantipensjonFactory.Garantipensjonsendring(1.mai(2018), 1.mai(2018), 185939),
        GarantipensjonFactory.Garantipensjonsendring(1.mai(2019), 1.mai(2019), 190368),
        GarantipensjonFactory.Garantipensjonsendring(1.mai(2020), 4.september(2020), 192125),
        GarantipensjonFactory.Garantipensjonsendring(1.mai(2021), 21.mai(2021), 202425),
        GarantipensjonFactory.Garantipensjonsendring(1.mai(2022), 20.mai(2022), 209571),
    ),
    private val minsteÅrligYtelseForUføretrygdedeOrdinær: List<MinsteÅrligYtelseForUføretrygdedeFactory.MinsteÅrligYtelseForUføretrygdedeEndring> = listOf(
        // https://lovdata.no/dokument/LTI/lov/2011-12-16-59 kunngjort 16.12.2011 kl. 15.40. Trådde i kraft 2015-01-01: https://lovdata.no/dokument/LTI/forskrift/2014-06-20-797 (kunngjort 23.06.2014 kl. 16.00)
        MinsteÅrligYtelseForUføretrygdedeFactory.MinsteÅrligYtelseForUføretrygdedeEndring(
            virkningstidspunkt = 1.januar(2015),
            ikrafttredelse = 1.januar(2015),
            faktor = Faktor(2.28),
        ),
    ),
    private val minsteÅrligYtelseForUføretrygdedeHøy: List<MinsteÅrligYtelseForUføretrygdedeFactory.MinsteÅrligYtelseForUføretrygdedeEndring> = listOf(
        // https://lovdata.no/dokument/LTI/lov/2011-12-16-59 kunngjort 16.12.2011 kl. 15.40 Trådde i kraft 2015-01-01: https://lovdata.no/dokument/LTI/forskrift/2014-06-20-797 (kunngjort 23.06.2014 kl. 16.00)
        MinsteÅrligYtelseForUføretrygdedeFactory.MinsteÅrligYtelseForUføretrygdedeEndring(
            virkningstidspunkt = 1.januar(2015),
            ikrafttredelse = 1.januar(2015),
            faktor = Faktor(2.48),
        ),
    ),
) {
    private fun getOrAdd(påDato: LocalDate): SatsFactoryForSupplerendeStønadPåDato {
        val grunnbeløpFactoryPåDato = GrunnbeløpFactory(
            påDato = påDato,
            grunnbeløpsendringer = grunnbeløpsendringer,
        )
        val minsteÅrligYtelseForUføretrygdedeFactoryPåDato =
            MinsteÅrligYtelseForUføretrygdedeFactory.createFromFaktorer(
                ordinær = minsteÅrligYtelseForUføretrygdedeOrdinær,
                høy = minsteÅrligYtelseForUføretrygdedeHøy,
                påDato = påDato,

            )
        return datoTilFactory.getOrPut(påDato) {
            SatsFactoryForSupplerendeStønadPåDato(
                grunnbeløpFactory = grunnbeløpFactoryPåDato,
                formuegrenserFactory = FormuegrenserFactory.createFromGrunnbeløp(
                    Nel.fromListUnsafe(
                        grunnbeløpFactoryPåDato.alle(),
                    ),
                ),
                uføreOrdinær = FullSupplerendeStønadFactory.Ordinær.Ufør(
                    grunnbeløpFactory = grunnbeløpFactoryPåDato,
                    minsteÅrligYtelseForUføretrygdedeFactory = minsteÅrligYtelseForUføretrygdedeFactoryPåDato,
                ),
                uføreHøy = FullSupplerendeStønadFactory.Høy.Ufør(
                    grunnbeløpFactory = grunnbeløpFactoryPåDato,
                    minsteÅrligYtelseForUføretrygdedeFactory = minsteÅrligYtelseForUføretrygdedeFactoryPåDato,
                ),
                alderOrdinær = FullSupplerendeStønadFactory.Ordinær.Alder(
                    garantipensjonFactory = GarantipensjonFactory.createFromSatser(
                        ordinær = garantipensjonsendringerOrdinær,
                        høy = garantipensjonsendringerHøy,
                        påDato = påDato,
                    ),
                ),
                alderHøy = FullSupplerendeStønadFactory.Høy.Alder(
                    garantipensjonFactory = GarantipensjonFactory.createFromSatser(
                        ordinær = garantipensjonsendringerOrdinær,
                        høy = garantipensjonsendringerHøy,
                        påDato = påDato,
                    ),
                ),
                gjeldendePåDato = påDato,
            )
        }
    }

    fun gjeldende(
        tidspunkt: Tidspunkt,
        zoneId: ZoneId = zoneIdOslo,
    ): SatsFactory {
        return gjeldende(tidspunkt.toLocalDate(zoneId))
    }

    /**
     * Gir et [SatsFactory] som gjelder på en gitt dato.
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
            påDato = maxOf(
                minsteÅrligYtelseForUføretrygdedeOrdinær.gjeldende(påDato).ikrafttredelse,
                minsteÅrligYtelseForUføretrygdedeHøy.gjeldende(påDato).ikrafttredelse,
                grunnbeløpsendringer.gjeldende(påDato).ikrafttredelse,
                garantipensjonsendringerOrdinær.gjeldende(påDato).ikrafttredelse,
                garantipensjonsendringerHøy.gjeldende(påDato).ikrafttredelse,
            ),
        )
    }

    private fun List<Grunnbeløpsendring>.gjeldende(påDato: LocalDate): Grunnbeløpsendring {
        return sortedBy { it.ikrafttredelse }.last { it.ikrafttredelse <= påDato }
    }

    private fun List<GarantipensjonFactory.Garantipensjonsendring>.gjeldende(påDato: LocalDate): GarantipensjonFactory.Garantipensjonsendring {
        return sortedBy { it.ikrafttredelse }.last { it.ikrafttredelse <= påDato }
    }

    private fun List<MinsteÅrligYtelseForUføretrygdedeFactory.MinsteÅrligYtelseForUføretrygdedeEndring>.gjeldende(påDato: LocalDate): MinsteÅrligYtelseForUføretrygdedeFactory.MinsteÅrligYtelseForUføretrygdedeEndring {
        return sortedBy { it.ikrafttredelse }.last { it.ikrafttredelse <= påDato }
    }
}
