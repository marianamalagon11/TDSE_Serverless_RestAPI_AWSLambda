package com.escuelaing.orders.lambda;

/**
 * The little bit of JSON writing the AWS layer needs.
 *
 * <p>The project declares no JSON library: the Lambda runtime serializes handler results on its
 * own, so the only places that build JSON by hand are the ones that must produce a string which is
 * itself JSON. Escaping it correctly is the whole job of this class.
 */
final class JsonText {

    private JsonText() {
    }

    /** Escapes a value so it can be placed inside a JSON string literal. */
    static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
