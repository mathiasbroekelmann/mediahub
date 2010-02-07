/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.util

import java.util.{Date, Calendar, Locale}

import org.joda.time.{DateTime, ReadableInstant}


/**
 * conversions of date types for millis (Long), java.util.Date, java.util.Calendar, org.joda.time.DateTime
 *
 * to use it simply import it:
 * <pre>
 * import org.mediahub.util.Dates._
 * </pre>
 */
object Dates {

  implicit def millisToDate(millis: Long) = new Date(millis)
  implicit def millisToDateTime(millis: Long) = new DateTime(millis)
  implicit def millisToCalendar(millis: Long) = {
    val cal = Calendar.getInstance
    cal.setTimeInMillis(millis)
    cal
  }

  implicit def dateToMillis(date: Date) = date.getTime
  implicit def dateToDateTime(date: Date) = new DateTime(date)
  implicit def dateToCalendar(date: Date) = {
    val cal = Calendar.getInstance
    cal.setTime(date)
    cal
  }

  implicit def readableInstantToDate(dateTime: ReadableInstant) = dateTime.toInstant.toDate
  implicit def readableInstantToMillis(dateTime: ReadableInstant) = dateTime.getMillis
  implicit def readableInstantToCalendar(dateTime: ReadableInstant) = dateTime.toInstant.toDateTime.toCalendar(Locale.getDefault)
}
