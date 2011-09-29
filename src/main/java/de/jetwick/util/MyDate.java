/**
 * Copyright (C) 2010 Peter Karich <jetwick_@_pannous_._info>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.jetwick.util;

import java.io.Serializable;
import java.util.Date;

/**
 * I know I could use jodatime but this is smaller
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class MyDate implements Cloneable, Serializable {

    private long time;
    public static final long ONE_SECOND = 1000L;
    public static final long ONE_MINUTE = 60 * ONE_SECOND;
    public static final long ONE_HOUR = 60 * ONE_MINUTE;
    public static final long ONE_DAY = 24 * ONE_HOUR;
    public static final long ONE_WEEK = 7 * ONE_DAY;

    public MyDate() {
        this(new Date());
    }

    public MyDate(Date date) {
        if (date == null)
            throw new NullPointerException("date mustn't be null!");
        time = date.getTime();
    }

    public MyDate(long time) {
        this.time = time;
    }

    public MyDate(MyDate date) {
        if (date == null)
            throw new NullPointerException("date mustn't be null!");
        time = date.getTime();
    }

    public long getTime() {
        return time;
    }

    public MyDate plusMillis(long ms) {
        time += ms;
        return this;
    }

    public MyDate plusSeconds(long sec) {
        time += sec * ONE_SECOND;
        return this;
    }

    public MyDate plusMinutes(int minutes) {
        time += minutes * ONE_MINUTE;
        return this;
    }

    public MyDate plusHours(int hours) {
        time += hours * ONE_HOUR;
        return this;
    }

    public MyDate plusDays(int days) {
        time += days * ONE_DAY;
        return this;
    }

    public MyDate minusMinutes(int minutes) {
        return plusMinutes(-minutes);
    }

    public MyDate minusHours(int hours) {
        return plusHours(-hours);
    }

    public MyDate minusDays(int days) {
        return plusDays(-days);
    }

    public MyDate minus(MyDate date) {
        time -= date.getTime();
        return this;
    }

    public MyDate minus(long time) {
        this.time -= time;
        return this;
    }

    public MyDate castToHour() {
        time = (time / ONE_HOUR) * ONE_HOUR;
        return this;
    }

    public MyDate castToHours(int hours) {
        time = (time / (hours * ONE_HOUR)) * hours * ONE_HOUR;
        return this;
    }

    public MyDate castToMinute() {
        time = (time / ONE_MINUTE) * ONE_MINUTE;
        return this;
    }

    public MyDate castToDay() {
        time = (time / ONE_DAY) * ONE_DAY;
        return this;
    }

    public long toDays() {
        return time / ONE_DAY;
    }

    public Date toDate() {
        return new Date(time);
    }

    @Override
    public MyDate clone() {
        return new MyDate(this);
    }

    @Override
    public String toString() {
        return new Date(time).toString();
    }

    public String toLocalString() {
        return Helper.toLocalDateTime(new Date(time));
    }

    public long getHours() {
        return time / ONE_HOUR;
    }
    
    public long getMinutes() {
        return time / ONE_MINUTE;
    }

    public long getDays() {
        return time / ONE_DAY;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final MyDate other = (MyDate) obj;
        if (this.time != other.time)
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 71 * hash + (int) (this.time ^ (this.time >>> 32));
        return hash;
    }

    /**
     * @return hours of day for UTC time
     */
    public int _getHoursOfDay() {
        return (int) ((time - new MyDate().castToDay().getTime()) / ONE_HOUR);
    }

    public String getTimes() {
        StringBuilder res = new StringBuilder();
        long duration = System.currentTimeMillis() - time;
        long temp = 0;
        if (duration <= ONE_SECOND)
            return "0 second";

        temp = duration / ONE_DAY;
        if (temp == 1)
            return "day";
        else if (temp > 0)
            return res.append(temp).append(" day").append(temp > 1 ? "s" : "").toString();

        temp = duration / ONE_HOUR;
        if (temp == 1)
            return "hour";
        else if (temp > 0)
            return res.append(temp).append(" hour").append(temp > 1 ? "s" : "").toString();

        temp = duration / ONE_MINUTE;
        if (temp == 1)
            return "minute";
        else if (temp > 0)
            return res.append(temp).append(" minute").append(temp > 1 ? "s" : "").toString();

        temp = duration / ONE_SECOND;
        if (temp == 1)
            return "second";
        else if (temp > 0)
            return res.append(temp).append(" second").append(temp > 1 ? "s" : "").toString();
        else
            return "";
    }

    /**
     * taken from http://stackoverflow.com/questions/3859288/how-to-calculate-time-ago-in-java
     * @return a string containing the most important date information
     */
    public String getTimesAgo() {
        StringBuilder res = new StringBuilder();
        long duration = System.currentTimeMillis() - time;
        long temp = 0;
        if (duration <= ONE_SECOND)
            return "0 second ago";

        temp = duration / ONE_DAY;
        if (temp > 0)
            return Helper.toSimpleDateTime(new Date(time));

        temp = duration / ONE_HOUR;
        if (temp > 0)
            res.append(temp).append(" hour").append(temp > 1 ? "s" : "").append(" ago");
        else {
            temp = duration / ONE_MINUTE;
            if (temp > 0)
                res.append(temp).append(" minute").append(temp > 1 ? "s" : "").append(" ago");
            else {
                temp = duration / ONE_SECOND;
                if (temp > 0)
                    res.append(temp).append(" second").append(temp > 1 ? "s" : "").append(" ago");
            }
        }

        return res.toString();
    }
}
