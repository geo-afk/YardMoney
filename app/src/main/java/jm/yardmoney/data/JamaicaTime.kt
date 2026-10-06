package jm.yardmoney.data

import java.time.LocalDate
import java.time.ZoneId

/** The app's calendar follows Jamaica time (UTC-5, no daylight saving), wherever the phone is. */
internal val JamaicaZone: ZoneId = ZoneId.of("America/Jamaica")

internal fun jamaicaToday(): LocalDate = LocalDate.now(JamaicaZone)
