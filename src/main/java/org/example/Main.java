
package org.example;

import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java -jar bytegraph.jar <class-or-root-dir>");
            System.exit(1);
        }

        // 1) 입력 경로: .class 또는 폴더(클래스패스 루트)
        Path input = Paths.get(args[0]).toAbsolutePath();
        if (!Files.exists(input)) {
            System.err.println("Input not found: " + input);
            System.exit(2);
        }

        // 2) 결과 폴더
        Path outDir = Paths.get("out");
        Files.createDirectories(outDir);

        // 3) 분석기 준비
        BcelBytecodeCFG bcel = new BcelBytecodeCFG();
        WalaIRProjector projector = new WalaIRProjector();

        // 4) 클래스 파일 목록 수집
        List<Path> classFiles = new ArrayList<>();
        String appClassPath; // WALA Application 스코프에 넣을 ‘루트’ 경로

        if (Files.isDirectory(input)) {
            // 폴더 전체를 루트로, 재귀적으로 .class 수집
            appClassPath = input.toString();
            try {
                // 하위 모든 .class (재귀)
                try (var stream = Files.walk(input)) {
                    stream.filter(p -> p.toString().endsWith(".class"))
                            .forEach(classFiles::add);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to scan classes under " + input + ": " + e.getMessage(), e);
            }
        } else if (input.toString().endsWith(".class")) {
            // 단일 .class 입력이라면, 그 부모 폴더를 루트로 사용
            classFiles.add(input);
            Path parent = input.getParent();
            if (parent == null) {
                System.err.println("No parent directory for the class file: " + input);
                System.exit(3);
            }
            appClassPath = parent.toString();
        } else {
            System.err.println("Input must be a .class file or a directory containing .class files");
            System.exit(4);
            return;
        }

        // 5) 클래스별 분석 실행
        int ok = 0, fail = 0;
        for (Path classFile : classFiles) {
            try {
                // 내부 이름 및 메서드 목록 스캔
                BcelClassIntrospector.ClassScan scan =
                        BcelClassIntrospector.scanClassFile(classFile.toString());
                String internalClassName = scan.internalName; // 예: org/primeframework/jwt/JWTEncoder
                List<BcelClassIntrospector.MethodSig> methods = scan.methods;
                if (methods.isEmpty()) continue;

                ObjectMapper om = new ObjectMapper();
                var root = om.createObjectNode();
                root.put("class", internalClassName.replace('/', '.'));
                var methodsArr = om.createArrayNode();

                for (var ms : methods) {
                    // BCEL: hex 노드/CFG(+예외)
                    BcelBytecodeCFG.Graph instrCFG =
                            bcel.build(classFile.toString(), ms.name, ms.desc);

                    // WALA: DFG/CDP/DDP (IR↔bytecode 매핑)
                    WalaIRProjector.Flow flow =
                            projector.analyze(appClassPath, internalClassName, ms.name, ms.desc, instrCFG);

                    // 메서드 노드 구성
                    var mnode = om.createObjectNode();
                    mnode.put("method", ms.name + ms.desc);

                    // nodes
                    var nodes = om.createArrayNode();
                    for (var e : instrCFG.nodes.entrySet()) {
                        InstructionInfo info = e.getValue();
                        var n = om.createObjectNode();
                        n.put("offset", info.offset);
                        n.put("hex", info.hexBytes);
                        n.put("mnemonic", info.mnemonic);
                        n.put("operands", info.operands);
                        nodes.add(n);
                    }
                    mnode.set("nodes", nodes);

                    // edges
                    var edges = om.createObjectNode();
                    edges.set("cfg", pairs(om, instrCFG.cfgEdges));
                    edges.set("ex",  pairs(om, instrCFG.exEdges));
                    edges.set("dfg", pairs(om, flow.dfg));
                    edges.set("cdp", pairs(om, flow.cdp));
                    edges.set("ddp", pairs(om, flow.ddp));
                    mnode.set("edges", edges);

                    methodsArr.add(mnode);
                }

                root.set("methods", methodsArr);

                // 파일로 저장: out/<internal.class.name>.json
                String outName = internalClassName.replace('/', '.') + ".json";
                Path outFile = outDir.resolve(outName);
                om.writerWithDefaultPrettyPrinter().writeValue(outFile.toFile(), root);

                System.out.println("Done: " + outFile);
                ok++;
            } catch (Exception ex) {
                System.err.println("[FAIL] " + classFile + " : " + ex.getMessage());
                fail++;
            }
        }

        System.out.printf("Summary: success=%d, fail=%d%n", ok, fail);
    }

    private static com.fasterxml.jackson.databind.node.ArrayNode
    pairs(com.fasterxml.jackson.databind.ObjectMapper om, Map<Integer, Set<Integer>> adj) {
        var arr = om.createArrayNode();
        for (var e : adj.entrySet()) {
            int src = e.getKey();
            for (int dst : e.getValue()) {
                var p = om.createObjectNode();
                p.put("src", src); p.put("dst", dst);
                arr.add(p);
            }
        }
        return arr;
    }
}
