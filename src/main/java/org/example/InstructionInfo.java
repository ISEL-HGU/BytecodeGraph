package org.example;

public class InstructionInfo {
    public final int offset;      // bytecode offset
    public final int length;      // instruction length (bytes)
    public final String mnemonic; // e.g., ILOAD, IFNE, GOTO
    public final String operands; // printable operands
    public final String hexBytes; // e.g., "1A 9A 00 05"

    public InstructionInfo(int offset, int length, String mnemonic, String operands, String hexBytes) {
        this.offset = offset;
        this.length = length;
        this.mnemonic = mnemonic;
        this.operands = operands;
        this.hexBytes = hexBytes;
    }

    public String label() {
        return String.format("0x%04X: %s | %s %s",
                offset, hexBytes, mnemonic, operands == null ? "" : operands);
    }
}
