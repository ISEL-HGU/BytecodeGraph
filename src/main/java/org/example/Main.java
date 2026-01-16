package org.example;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    private static final Set<String> VALID_DDG_OPTIONS = Set.of("DDG", "NO_DDG");
    private static final Set<String> VALID_DFG_MODES = Set.of("DATA_STACK", "DATA_LOCAL", "WALA_ONLY");
    private static final Path EXCLUSIONS_PATH = Paths.get("exclusions.txt");

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }

        String mode = (args.length > 1) ? args[1].toUpperCase() : "DATA_LOCAL";
        String ddgOption = (args.length > 2) ? args[2].toUpperCase() : "DDG";
        Path targetPath = Paths.get(args[0]).toAbsolutePath();
        String appClassPath = Files.isDirectory(targetPath) ? targetPath.toString() : targetPath.getParent().toString();

        Diagnosis diagnosis = new Diagnosis(EXCLUSIONS_PATH);
        Analysis engine = new Analysis(mode, ddgOption, diagnosis);
        Set<Path> failedFiles = new LinkedHashSet<>();

        try {
            // [1차 시도] 기존 exclusions.txt 사용하여 빠르게 분석
            System.out.println(">>> [Pass 1] Starting fast analysis with exclusions...");
            WalaSession session1 = WalaSession.init(appClassPath, Collections.emptySet(), Collections.emptyList());

            List<Path> filesToProcess = Files.isDirectory(targetPath)
                    ? Files.walk(targetPath).filter(p -> p.toString().endsWith(".class")).collect(Collectors.toList())
                    : List.of(targetPath);

            engine.run(session1, filesToProcess, failedFiles);

            // [2차 시도 - Healing] 실패한 파일 재시도
            if (!failedFiles.isEmpty()) {
                System.out.println("\n>>> [Pass 2] Healing session starting...");

                // 1차 시도에서 수집된 에러를 바탕으로 해결책이 있는지 확인
                if (diagnosis.hasSuggestions()) {
                    diagnosis.printReport(); // 사용자에게 필요한 라이브러리 리포트 출력

                    // 차단 해제가 필요한 패키지가 있다면 2차 시도 진행
                    if (!diagnosis.getPackagesToUnblock().isEmpty()) {
                        System.out.println(">>> Retrying with dynamic unblocking for: " + diagnosis.getPackagesToUnblock());

                        WalaSession healingSession = WalaSession.init(
                                appClassPath,
                                diagnosis.getPackagesToUnblock(),
                                Collections.emptyList() // 필요 시 외부 라이브러리 경로 추가 가능
                        );

                        Set<Path> pass2Failed = new HashSet<>();
                        engine.run(healingSession, new ArrayList<>(failedFiles), pass2Failed);
                    }
                } else {
                    System.out.println(">>> No clear healing path found for remaining failures.");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void printUsage() {
        System.err.println("Usage: java -jar bytegraph.jar <appClassPath> [mode] [ddgOption]");
    }
}