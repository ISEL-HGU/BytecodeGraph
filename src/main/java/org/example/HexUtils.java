package org.example;

public final class HexUtils {
    private HexUtils() {}
    public static String sliceToHex(byte[] code, int off, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = off; i < off + len; i++) {
            sb.append(String.format("%02X", code[i]));
            if (i < off + len - 1) sb.append(' ');
        }
        return sb.toString();
    }
}
