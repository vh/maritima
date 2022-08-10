package com.github.vh.maritima;

import java.util.Set;

public class SelectorUtils {

    public static void apply(final Set<String> selectorSet, final String selector) throws Exception {
        String sel = selector.replace(".", "\\.");

        int wcIndex = sel.indexOf("*"),
                len = sel.length();
        if ((wcIndex != -1 && wcIndex != len - 1) || len == 0) {
            throw new Exception("Invalid selector: " + sel);
        }

        if (wcIndex == len - 1 && len == 1) {
            selectorSet.add(".*");
            return;
        }

        if (wcIndex == len - 1) {
            if (".*".equals(sel.substring(len - 2))) {
                selectorSet.add(sel.substring(0, len - 1) + ".*");
            } else {
                throw new Exception("Invalid selector: " + sel);
            }
        }

        selectorSet.add(sel);
    }
}
