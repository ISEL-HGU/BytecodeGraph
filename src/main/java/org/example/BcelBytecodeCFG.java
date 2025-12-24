package org.example;

import org.apache.bcel.classfile.*;
import org.apache.bcel.generic.*;

import java.io.FileInputStream;
import java.util.*;

/**
 * BCEL로 .class의 Code 바이트열에서 명령어 오프셋/길이/hex/mnemonic/operands를 즉시 추출하고,
 * instruction-level CFG(+예외 흐름)를 구성한다.
 */
public class BcelBytecodeCFG {

    /** 명령어 단위 그래프 */
    public static class Graph {
        public final Map<Integer, InstructionInfo> nodes = new LinkedHashMap<>(); // key = offset
        public final Map<Integer, Set<Integer>> cfgEdges = new LinkedHashMap<>(); // 정상 흐름 엣지
        public final Map<Integer, Set<Integer>> exEdges = new LinkedHashMap<>();  // 예외 핸들러 엣지
        public byte[] rawCode; // 라벨링/검증용
    }

    public Graph build(String classFilePath, String methodName, String methodDesc) throws Exception {
        ClassParser cp = new ClassParser(new FileInputStream(classFilePath), classFilePath);
        JavaClass jc = cp.parse();
        ConstantPoolGen cpg = new ConstantPoolGen(jc.getConstantPool());
        ClassGen cg = new ClassGen(jc);

        Method target = null;
        for (Method m : cg.getMethods()) {
            if (m.getName().equals(methodName) && m.getSignature().equals(methodDesc)) {
                target = m; break;
            }
        }
        if (target == null)
            throw new IllegalArgumentException("cannot find method : " + methodName + methodDesc);

        Code code = target.getCode();
        byte[] bytes = code.getCode();
        InstructionList il = new InstructionList(bytes);
        InstructionHandle[] ihs = il.getInstructionHandles();

        Graph g = new Graph();
        g.rawCode = bytes;

        // 1) 노드 생성
        for (InstructionHandle ih : ihs) {
            Instruction inst = ih.getInstruction();
            int offset = ih.getPosition();
            int len = inst.getLength();
            String mnem = inst.getName().toUpperCase(Locale.ROOT);
            String ops = operandsToString(inst, ih, cpg);
            String hex = HexUtils.sliceToHex(bytes, offset, len);

            InstructionInfo info = new InstructionInfo(offset, len, mnem, ops, hex);
            g.nodes.put(offset, info);
            g.cfgEdges.putIfAbsent(offset, new LinkedHashSet<>());
            g.exEdges.putIfAbsent(offset, new LinkedHashSet<>());
        }

        // 2) 정상 흐름 엣지(SEQUENCE/JUMP/IF*/SWITCH)
        for (InstructionHandle ih : ihs) {
            int off = ih.getPosition();
            Instruction inst = ih.getInstruction();
            InstructionHandle next = ih.getNext();

            if (next != null &&
                    !(inst instanceof GotoInstruction) &&
                    !(inst instanceof ReturnInstruction) &&
                    !(inst instanceof ATHROW) &&
                    !(inst instanceof Select) &&
                    !(inst instanceof IfInstruction)) {
                g.cfgEdges.get(off).add(next.getPosition());    // fall-through
            }
            if (inst instanceof GotoInstruction) {
                g.cfgEdges.get(off).add(((GotoInstruction) inst).getTarget().getPosition());
            }
            if (inst instanceof IfInstruction) {
                InstructionHandle tgt = ((IfInstruction) inst).getTarget();
                g.cfgEdges.get(off).add(tgt.getPosition());     // 참
                if (next != null) g.cfgEdges.get(off).add(next.getPosition()); // 거짓
            }
            if (inst instanceof Select) {
                Select sel = (Select) inst;
                for (InstructionHandle t : sel.getTargets())
                    g.cfgEdges.get(off).add(t.getPosition());     // case들
                g.cfgEdges.get(off).add(sel.getTarget().getPosition()); // default
            }
        }

        // 3) 예외 핸들러 엣지: 범위 [startPC, endPC) 내 → handlerPC
        CodeException[] handlers = code.getExceptionTable();
        if (handlers != null) {
            for (CodeException ce : handlers) {
                int handlerPC = ce.getHandlerPC();
                int startPC = ce.getStartPC();
                int endPC = ce.getEndPC();
                for (InstructionHandle ih : ihs) {
                    int off = ih.getPosition();
                    if (off >= startPC && off < endPC) {
                        g.exEdges.get(off).add(handlerPC);
                    }
                }
            }
        }
        return g;
    }

    private static String operandsToString(Instruction inst, InstructionHandle ih, ConstantPoolGen cpg) {
        try {
            if (inst instanceof InvokeInstruction) {
                InvokeInstruction ci = (InvokeInstruction) inst;
                return ci.getClassName(cpg) + "." + ci.getMethodName(cpg) + ci.getSignature(cpg);
            } else if (inst instanceof FieldInstruction) {
                FieldInstruction fi = (FieldInstruction) inst;
                return fi.getClassName(cpg) + "." + fi.getFieldName(cpg);
            } else if (inst instanceof CPInstruction) {
                return ((CPInstruction) inst).toString(cpg.getConstantPool());
            } else if (inst instanceof BranchInstruction) {
                BranchInstruction bi = (BranchInstruction) inst;
                return "-> 0x" + String.format("%04X", bi.getTarget().getPosition());
            }
        } catch (Exception ignore) {}
        return "";
    }
}

