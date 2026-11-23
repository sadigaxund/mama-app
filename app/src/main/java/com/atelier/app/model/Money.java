package com.atelier.app.model;

import java.util.List;

/** Money math in minor units. MoneyTest holds the rules. */
public final class Money {
    private Money() {}

    /** work price + expenses - payments. Negative means overpaid. */
    public static long balanceDue(long workPrice, List<LedgerEntry> entries) {
        long balance = workPrice;
        for (LedgerEntry e : entries) {
            if (e.type == LedgerType.EXPENSE) {
                balance += e.amount;
            } else {
                balance -= e.amount;
            }
        }
        return balance;
    }

    /** Parses "250", "250.5" or "250,50" into minor units. Rejects negatives and garbage. */
    public static long parseMinor(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Amount is empty");
        }
        String s = text.trim();
        if (!s.matches("[0-9]+([.,][0-9]{1,2})?")) {
            throw new IllegalArgumentException("Invalid amount: " + text);
        }
        int sep = Math.max(s.indexOf('.'), s.indexOf(','));
        String whole = sep < 0 ? s : s.substring(0, sep);
        String frac = sep < 0 ? "" : s.substring(sep + 1);
        if (frac.length() == 1) {
            frac = frac + "0";
        }
        if (frac.isEmpty()) {
            frac = "00";
        }
        if (whole.length() > 15) {
            throw new IllegalArgumentException("Amount too large: " + text);
        }
        return Long.parseLong(whole) * 100 + Long.parseLong(frac);
    }

    /** "250" when whole, "250.50" otherwise. Negative values keep a leading minus. */
    public static String formatMinor(long minor) {
        boolean neg = minor < 0;
        long abs = Math.abs(minor);
        long whole = abs / 100;
        long frac = abs % 100;
        String body = frac == 0 ? Long.toString(whole)
                : whole + "." + (frac < 10 ? "0" : "") + frac;
        return neg ? "-" + body : body;
    }
}
