package org.callbridge.relay;

import java.util.Locale;

/** Minimal translation helper: French when the system language is French, English otherwise. */
final class L {
    private L() { }

    static boolean french() {
        return "fr".equals(Locale.getDefault().getLanguage());
    }

    static String t(String en, String fr) {
        return french() ? fr : en;
    }
}
