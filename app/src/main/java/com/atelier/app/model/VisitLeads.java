package com.atelier.app.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Lists of "minutes before a visit". An empty list means visit reminders are off. VisitLeadsTest holds the rules. */
public final class VisitLeads {
    /** Every lead the app offers, largest first. */
    public static final int[] OPTIONS = {24 * 60, 120, 60, 30, 15};

    private VisitLeads() {}

    public static boolean isOption(int minutes) {
        for (int o : OPTIONS) {
            if (o == minutes) {
                return true;
            }
        }
        return false;
    }

    /** Offered values only, unique, largest first. */
    public static List<Integer> normalize(Collection<Integer> minutes) {
        List<Integer> out = new ArrayList<>();
        for (int o : OPTIONS) {
            if (minutes.contains(o)) {
                out.add(o);
            }
        }
        return out;
    }

    /** Parses "1440,60". Tokens that are not offered values are dropped. */
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

    /** "1440,60" for a list, the form stored in the preferences. Empty for off. */
    public static String toCsv(Collection<Integer> minutes) {
        StringBuilder sb = new StringBuilder();
        for (Integer m : normalize(minutes)) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(m);
        }
        return sb.toString();
    }

    /** The list a single lead from an older version maps to: off for 0 or an unknown value. */
    public static List<Integer> fromSingle(int minutes) {
        List<Integer> out = new ArrayList<>();
        if (isOption(minutes)) {
            out.add(minutes);
        }
        return out;
    }
}
