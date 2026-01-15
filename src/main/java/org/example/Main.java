package org.example;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws Exception {

        if (args.length < 1) {
            System.err.println("Usage: java -jar bytegraph.jar <appClassPath> [mode]");
            System.exit(1);
        }

        String mode = (args.length > 1) ? args[1].toUpperCase() : "SEMANTIC";
        boolean skipDDG = (args.length > 2) && "NO_DDG".equalsIgnoreCase(args[2]);

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
            int failCount = 0;
            List<String> failedFiles = new ArrayList<>();
            long startTime = System.currentTimeMillis();

            for (Path classFile : filesToProcess) {
                String fileName = classFile.getFileName().toString();
                try {
                    BcelClassIntrospector.ClassScan scan = BcelClassIntrospector.scanClassFile(classFile.toString());

                    for (var ms : scan.methods) {
                        BcelBytecodeCFG.Graph instrCFG = bcel.build(classFile.toString(), ms.name, ms.desc, mode);
                        WalaIRProjector.Flow flow = projector.analyze(session, scan.internalName, ms.name, ms.desc, instrCFG, skipDDG);

                        Path outDir = Paths.get("out");
                        Files.createDirectories(outDir);
                        String safeMethodName = ms.name.replace("<", "").replace(">", "");
                        String outName = scan.internalName.replace('/', '.') + "." + safeMethodName + ".json";
                        JsonExporter.export(scan.internalName, ms.name, ms.desc, instrCFG, flow, outDir.resolve(outName));
                    }

                    System.out.println("[SUCCESS] " + fileName);
                    successCount++;

                } catch (Exception ex) {
                    // 개별 파일 분석 실패 시 출력
                    System.err.println("[FAILED] " + fileName);
                    System.err.println("  - Error Type: " + ex.getClass().getSimpleName());
                    System.err.println("  - Message: " + ex.getMessage());

                    // 의존성 문제인 경우 원인 추적을 위해 StackTrace 일부 출력 (선택 사항)
                    if (ex.getMessage() != null && ex.getMessage().contains("Class not found")) {
                        System.err.println("  - Hint: Check if this class depends on missing external JARs (like Swing/AWT).");
                    }
                    failedFiles.add(fileName + " (" + ex.getMessage() + ")");
                    failCount++;
                }
            }

            // 최종 요약 보고
            long duration = System.currentTimeMillis() - startTime;
            System.out.println("\n--- Analysis Summary ---");
            System.out.printf("Total Files: %d | Success: %d | Failure: %d\n", (successCount + failCount), successCount, failCount);
            System.out.printf("Total Time: %.2fs\n", duration / 1000.0);

            if (!failedFiles.isEmpty()) {
                System.out.println("Failed List:");
                failedFiles.forEach(f -> System.out.println("  - " + f));
            }
            System.out.println("------------------------");
        }
    }
}