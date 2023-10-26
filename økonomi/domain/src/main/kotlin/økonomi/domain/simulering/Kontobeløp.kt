//package økonomi.domain.simulering
//
//import kotlin.math.abs
//
//sealed interface Kontobeløp {
//    val beløp: Int
//
//    fun sum() = beløp
//
//    companion object {
//        operator fun invoke(int: Int): Kontobeløp {
//            return if (int < 0) {
//                Kredit(int)
//            } else {
//                Debet(int)
//            }
//        }
//
//        operator fun invoke(vararg kontobeløp: Kontobeløp): Kontobeløp {
//            return Kontobeløp(Summert(kontobeløp.sumOf { it.sum() }).beløp)
//        }
//    }
//    data class Debet private constructor(
//        override val beløp: Int,
//    ) : Kontobeløp {
//
//        init {
//            require(beløp >= 0)
//        }
//        companion object {
//            operator fun invoke(int: Int): Debet {
//                return Debet(abs(int))
//            }
//        }
//    }
//
//    data class Kredit private constructor(
//        override val beløp: Int,
//    ) : Kontobeløp {
//        init {
//            require(beløp <= 0)
//        }
//        companion object {
//            operator fun invoke(int: Int): Kredit {
//                return Kredit(-abs(int))
//            }
//        }
//    }
//
//    data class Summert private constructor(
//        override val beløp: Int,
//    ) : Kontobeløp {
//        companion object {
//            operator fun invoke(vararg kontobeløp: Kontobeløp): Summert {
//                return Summert(kontobeløp.sumOf { it.sum() })
//            }
//
//            operator fun invoke(int: Int): Summert {
//                return Summert(int)
//            }
//        }
//    }
//}
