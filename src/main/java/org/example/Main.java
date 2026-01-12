package org.example;

import java.nio.file.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Main {
    public static void main(String[] args) throws Exception {

        if (args.length < 1) {
            System.err.println("Usage: java -jar bytegraph.jar <appClassPath> [mode]");
            System.exit(1);
        }

        // 1) 분석 모드 설정 (기본값 SEMANTIC)
        String mode = (args.length > 1) ? args[1].toUpperCase() : "SEMANTIC";
        System.out.println(">>> Analysis Mode: " + mode);

        // 2) 분석 대상 루트 경로 설정 및 WALA 세션 초기화
        Path inputPath = Paths.get(args[0]).toAbsolutePath();
        String appClassPath = Files.isDirectory(inputPath) ? inputPath.toString() : inputPath.getParent().toString();

        System.out.println(">>> [Daemon] Initializing WALA Global Session (this may take a while)...");
        WalaSession session = WalaSession.init(appClassPath);
        System.out.println(">>> WALA Session Ready.\n");

        BcelBytecodeCFG bcel = new BcelBytecodeCFG();
        WalaIRProjector projector = new WalaIRProjector();
        Scanner scanner = new Scanner(System.in);

        System.out.println("=== Analysis Daemon Started ===");
        System.out.println("enter absolute path of .class file (or exit):");

        // 3) daemon
        while (true) {
            System.out.print("\nREADY > ");
            String inputLine = scanner.nextLine().trim();
            if (inputLine.startsWith("\"") && inputLine.endsWith("\"")) {
                inputLine = inputLine.substring(1, inputLine.length() - 1);
            }

            if (inputLine.equalsIgnoreCase("exit")) break;
            if (inputLine.isEmpty()) continue;

            Path classFile = Paths.get(inputLine);
            if (!Files.exists(classFile)) {
                System.out.println("[ERROR] cannot find file: " + inputLine);
                continue;
            }

            try {
                long startTime = System.currentTimeMillis();

                // BCEL 클래스 스캔
                BcelClassIntrospector.ClassScan scan = BcelClassIntrospector.scanClassFile(classFile.toString());
                System.out.println("Analyzing " + scan.methods.size() + " methods in " + scan.internalName);

                for (var ms : scan.methods) {
                    // BCEL 분석
                    BcelBytecodeCFG.Graph instrCFG = bcel.build(classFile.toString(), ms.name, ms.desc, mode);

                    // WALA 분석
                    WalaIRProjector.Flow flow = projector.analyze(session, scan.internalName, ms.name, ms.desc, instrCFG);

                    // JSON 결과 저장
                    Path outDir = Paths.get("out");
                    Files.createDirectories(outDir);
                    String safeMethodName = ms.name.replace("<", "").replace(">", "");
                    String outName = scan.internalName.replace('/', '.') + "." + safeMethodName + ".json";
                    JsonExporter.export(scan.internalName, ms.name, ms.desc, instrCFG, flow, outDir.resolve(outName));
                }

                long endTime = System.currentTimeMillis();
                System.out.printf("SUCCESS: Analysis finished in %.2fs\n", (endTime - startTime) / 1000.0);

            } catch (Exception ex) {
                System.out.println("FAILED: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
        System.out.println("Daemon stopped.");
    }
}