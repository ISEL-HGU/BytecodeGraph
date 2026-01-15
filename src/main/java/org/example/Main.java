package org.example;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

    // 분석 옵션 리스트
    private static final Set<String> VALID_DDG_OPTIONS = Set.of("DDG", "NO_DDG");
    private static final Set<String> VALID_DFG_MODES = Set.of("DATA_STACK", "DATA_LOCAL", "WALA_ONLY");

    public static void main(String[] args) throws Exception {

        if (args.length < 1) {
            System.err.println("Usage: java -jar bytegraph.jar <appClassPath> [mode]");
            System.exit(1);
        }

        // 1. DFG 옵션 검증
        String mode = (args.length > 1) ? args[1].toUpperCase() : "DATA_LOCAL";
        if (!VALID_DFG_MODES.contains(mode)) {
            System.err.println("[ERROR] Invalid DFG Mode: " + mode);
            printUsage();
            System.exit(1);
        }

        // 2. DDG 옵션 검증
        String ddgOption = (args.length > 2) ? args[2].toUpperCase() : "DDG";
        if (!VALID_DDG_OPTIONS.contains(ddgOption)) {
            System.err.println("[ERROR] Invalid DDG Option: " + ddgOption);
            printUsage();
            System.exit(1);
        }

        System.out.println(">>> DFG Mode: " + mode + " | DDG Option: " + ddgOption);

        Path inputPath = Paths.get(args[0]).toAbsolutePath();
        String appClassPath = Files.isDirectory(inputPath) ? inputPath.toString() : inputPath.getParent().toString();

        System.out.println(">>> [Daemon] Initializing WALA Session...");
        WalaSession session = WalaSession.init(appClassPath);

        BcelBytecodeCFG bcel = new BcelBytecodeCFG();
        WalaIRProjector projector = new WalaIRProjector();
        Scanner scanner = new Scanner(System.in);

        System.out.println("=== Analysis Daemon Started ===");
        System.out.println("Enter .class file or directory path (or exit):");

        while (true) {
            System.out.print("\nREADY > ");
            String inputLine = scanner.nextLine().trim();
            if (inputLine.isEmpty()) continue;
            if (inputLine.equalsIgnoreCase("exit")) break;

            Path targetPath = Paths.get(inputLine.replace("\"", ""));
            if (!Files.exists(targetPath)) {
                System.out.println("[ERROR] Path does not exist: " + targetPath);
                continue;
            }

            // 분석 대상 파일 수집
            List<Path> filesToProcess = new ArrayList<>();
            if (Files.isDirectory(targetPath)) {
                try (Stream<Path> walk = Files.walk(targetPath)) {
                    filesToProcess = walk.filter(p -> p.toString().endsWith(".class")).collect(Collectors.toList());
                }
            } else {
                filesToProcess.add(targetPath);
            }

            int successCount = 0;
            long startTime = System.currentTimeMillis();
            Map<String, String> failedMap = new LinkedHashMap<>();

            for (Path classFile : filesToProcess) {
                String fileName = classFile.getFileName().toString();
                try {
                    BcelClassIntrospector.ClassScan scan = BcelClassIntrospector.scanClassFile(classFile.toString());

                    for (var ms : scan.methods) {
                        BcelBytecodeCFG.Graph instrCFG = bcel.build(classFile.toString(), ms.name, ms.desc, mode);
                        WalaIRProjector.Flow flow = projector.analyze(session, scan.internalName, ms.name, ms.desc, instrCFG, ddgOption);

                        Path outDir = Paths.get("out");
                        Files.createDirectories(outDir);
                        String safeMethodName = ms.name.replace("<", "").replace(">", "");
                        String outName = scan.internalName.replace('/', '.') + "." + safeMethodName + ".json";
                        JsonExporter.export(scan.internalName, ms.name, ms.desc, instrCFG, flow, outDir.resolve(outName));
                    }

                    System.out.println("[SUCCESS] " + fileName);
                    successCount++;

                } catch (Exception ex) { // class 분석 실패
                    failedMap.put(fileName, ex.getMessage());
                }
            }

            // 최종 요약 보고
            long duration = System.currentTimeMillis() - startTime;
            System.out.println("\n--- Analysis Summary ---");
            System.out.printf("Total Time: %.2fs\n", duration / 1000.0);
            System.out.printf(" >> Total Files   : %d\n", (successCount + failedMap.size()));
            System.out.printf(" >> Success       : %d\n", successCount);
            System.out.printf(" >> Failure       : %d\n", failedMap.size());

            if (!failedMap.isEmpty()) {
                System.out.println();
                System.out.println(" [Detailed Failure Reasons]");
                // System.err 대신 System.out을 사용하여 순서를 보장합니다.
                failedMap.forEach((name, reason) -> {
                    String hint = reason.contains("Class not found") ?
                            " -> [HIERARCHY ISSUE] Check exclusions.txt" : "";
                    System.out.println(String.format(" - %-25s : %s%s", name, reason, hint));
                });
            }
            System.out.println("------------------------");
            System.out.flush();
        }
    }

    private static void printUsage() {
        System.err.println("Usage: java -jar bytegraph.jar <appClassPath> [dfgMode] [ddgOption]");
        System.err.println("\n[DFG Modes (Physical/BCEL)]");
        System.err.println("  - DATA_LOCAL : (Default) Track local variable slots only");
        System.err.println("  - DATA_STACK : Track both slots and stack operations (Rich physical flow)");
        System.err.println("  - WALA_ONLY  : Skip physical flow; use high-level WALA analysis results only");
        System.err.println("\n[DDG Options (High-level/WALA)]");
        System.err.println("  - DDG        : (Default) Full data dependence ");
        System.err.println("  - NO_DDG     : Skip WALA PDG analysis entirely");
    }
}