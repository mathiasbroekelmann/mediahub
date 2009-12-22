package de.osxp.dali.time

import org.joda.time.{Interval, DateTime, DateTimeFieldType, Period}
import Period._
import DateTimeFieldType._

/**
 * Defines time related objects.
 * 
 * @author Mathias Broekelmann
 *
 * @since 20.12.2009
 *
 */
object Time {
    
    /**
     * a second of time
     */
    object Second extends PointInTime(intervalBy(seconds(1), millisFields))
    /**
     * a minute of time
     */
    object Minute extends PointInTime(intervalBy(minutes(1), secondsFields))
    /**
     * a hour of time
     */
    object Hour extends PointInTime(intervalBy(hours(1), minutesFields))
    /**
     * a day of time
     */
    object Day extends PointInTime(intervalBy(days(1), hourFields))
    /**
     * a week of time
     */
    object Week extends PointInTime(intervalBy(weeks(1), weekDayFields))
    /**
     * a month of time
     */
    object Month extends PointInTime(intervalBy(months(1), dayFields))
    /**
     * a year of time
     */
    object Year extends PointInTime(intervalBy(years(1), monthFields))

    private val millisFields = millisOfSecond :: Nil
    private val secondsFields = secondOfMinute +: millisFields
    private val minutesFields = minuteOfHour +: secondsFields
    private val hourFields = hourOfDay +: minutesFields
    private val dayFields = dayOfMonth +: hourFields
    private val weekDayFields = dayOfWeek +: hourFields
    private val monthFields = monthOfYear +: dayFields
    
    private def intervalBy(period: Period, zeroFields: List[DateTimeFieldType]) = new (DateTime => Interval) {
        def apply(date: DateTime): Interval = {
            val base = zeroFields.foldLeft(date) { (dt, field) => 
                dt.property(field).withMinimumValue
            }
            new Interval(base, period)
        }
    }
}

case class PointInTime(of: (DateTime => Interval))



