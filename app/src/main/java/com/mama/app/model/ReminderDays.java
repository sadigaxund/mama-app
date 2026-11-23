package com.mama.app.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/** Lists of "days before the deadline". ReminderDaysTest holds the rules. */
public final class ReminderDays {
    public static final int MIN = 1;
    public static final int MAX = 365;

    private ReminderDays() {}

    public static boolean isValid(int days) {
        return days >= MIN && days <= MAX;
    }

    /** Valid, unique values, largest first. */
    public static List<Integer> normalize(Collection<Integer> days) {
        TreeSet<Integer> set = new TreeSet<>(Collections.reverseOrder());
        for (Integer d : days) {
            if (d != null && isValid(d)) {
                set.add(d);
            }
        }
        return new ArrayList<>(set);
    }

    /** Parses "3,1" or "7, 2". Tokens that are not whole numbers in 1..365 are dropped. */
    public static List<Integer> parse(String csv) {
        List<Integer> raw = new ArrayList<>();
        if (csv != null) {
            for (String token : csv.split(",")) {
                String t = token.trim();
                if (t.matches("[0-9]{1,9}")) {
                    raw.add(Integer.parseInt(t));
                }
            }
        }
        return normalize(raw);
    }

    /** "3,1" for a list, the form stored in the preferences. */
    public static String toCsv(Collection<Integer> days) {
        StringBuilder sb = new StringBuilder();
        for (Integer d : normalize(days)) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(d);
        }
        return sb.toString();
    }
}
