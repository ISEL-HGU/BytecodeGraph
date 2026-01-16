package org.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class Diagnosis {
    private final Set<String> packagesToUnblock = new HashSet<>();
    private final Set<String> missingLibraries = new HashSet<>();
    private final Path exclusionsPath;

    public Diagnosis(Path exclusionsPath) {
        this.exclusionsPath = exclusionsPath;
    }

    public void analyzeError(String errorMessage, String currentClassName) {
        if (errorMessage == null || !errorMessage.contains("Class not found: L")) return;

        String fullPath = errorMessage.split("L")[1].split(" ")[0].replace(")", "").replace(";", "");
        String foundClass = fullPath.replace("/", ".");

        if (!foundClass.equals(currentClassName)) {
            if (!fullPath.contains("/")) {
                missingLibraries.add("Parent class of " + fullPath);
                return;
            }

            int lastSlash = fullPath.lastIndexOf('/');
            String packagePath = fullPath.substring(0, lastSlash);

            try {
                List<String> exclusionLines = Files.readAllLines(exclusionsPath);
                boolean isExcluded = exclusionLines.stream()
                        .anyMatch(line -> !line.startsWith("#") && line.trim().equals(packagePath));

                if (isExcluded) {
                    packagesToUnblock.add(packagePath);
                } else {
                    missingLibraries.add(fullPath.replace("/", "."));
                }
            } catch (IOException e) {
            }
        }
    }

    public void addMissingLibrary(String missingClassName, String currentClassName) {
        if (missingClassName == null || missingClassName.equals("java.lang.Object")) return;

        String missing = missingClassName.replace('/', '.');
        String current = currentClassName.replace('/', '.');
        if (!missing.equals(current)) {
            missingLibraries.add(missing);
        }
    }

    public boolean hasSuggestions() {
        return !packagesToUnblock.isEmpty() || !missingLibraries.isEmpty();
    }

    public Set<String> getPackagesToUnblock() { return packagesToUnblock; }

    public void printReport() {
        System.out.println("\n" + "=".repeat(20) + " DIAGNOSIS REPORT " + "=".repeat(20));

        if (!packagesToUnblock.isEmpty()) {
            System.out.println("[Configuration Issue]");
            System.out.println("  - The following packages are blocked by 'exclusions.txt':");
            System.out.println("    " + packagesToUnblock);
            System.out.println("  - Action: The program will automatically unblock them in memory for Pass 2.");
        }

        if (!missingLibraries.isEmpty()) {
            System.out.println("\n[Library Dependency Issue]");
            System.out.println("  - The following classes/dependencies are missing:");
            for (String lib : missingLibraries) {
                System.out.println("    * " + lib);
            }
            System.out.println("  - Action: Please add the required JAR files (e.g., JavaFX SDK) to your classpath.");

            if (missingLibraries.toString().toLowerCase().contains("javafx")) {
                System.out.println("  - Note: JavaFX libraries (openjfx) are required for these classes.");
            }
        }

        if (packagesToUnblock.isEmpty() && missingLibraries.isEmpty()) {
            System.out.println("No specific library or exclusion issues identified.");
        }

        System.out.println("=".repeat(58) + "\n");
    }
}