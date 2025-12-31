package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * 최종 출력(JSON)은 "명령어 노드(오프셋/hex/mnemonic/operands)"와
 * "엣지 집합(CFG/예외/DFG/CDG/DDG)"만 포함. IR 텍스트는 일절 출력하지 않는다.
 */
public class JsonExporter {

    public static void export(String internalClassName, String methodName, String methodDesc,
                              BcelBytecodeCFG.Graph g, WalaIRProjector.Flow f, Path out) throws IOException {

        ObjectMapper om = new ObjectMapper();
        ObjectNode root = om.createObjectNode();
        root.put("method", internalClassName.replace('/', '.') + "." + methodName + methodDesc);

        ArrayNode nodes = om.createArrayNode();
        for (Map.Entry<Integer, InstructionInfo> e : g.nodes.entrySet()) {
            InstructionInfo info = e.getValue();
            ObjectNode n = om.createObjectNode();
            n.put("offset", info.offset);
            n.put("hex", info.hexBytes);
            n.put("mnemonic", info.mnemonic);
            n.put("operands", info.operands);
            nodes.add(n);
        }
        root.set("nodes", nodes);

        ObjectNode edges = om.createObjectNode();
        edges.set("cfg", pairs(om, g.cfgEdges));   // 정상 흐름
        edges.set("ex",  pairs(om, g.exEdges));    // 예외 흐름
        edges.set("dfg", pairs(om, f.dfg));        // 데이터 흐름(def->use)
        edges.set("cdg", pairs(om, f.cdg));        // 제어 의존(근사)
        edges.set("ddg", pairs(om, f.ddg));        // 데이터 의존(=DFG와 동일 스키마)
        root.set("edges", edges);

        om.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), root);
    }

    private static ArrayNode pairs(ObjectMapper om, Map<Integer, Set<Integer>> adj) {
        ArrayNode arr = om.createArrayNode();
        for (var e : adj.entrySet()) {
            int src = e.getKey();
            for (int dst : e.getValue()) {
                ObjectNode p = om.createObjectNode();
                p.put("src", src); p.put("dst", dst);
                arr.add(p);
            }
        }
        return arr;
    }
}
