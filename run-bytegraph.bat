
@echo off
setlocal enabledelayedexpansion

REM ============================================
REM ByteGraph Runner (Windows .bat)
REM Usage: run-bytegraph.bat "C:\path\to\MyClass.class" [JAVA8_HOME]
REM  - arg1 (required): absolute path to the .class file to analyze
REM  - arg2 (optional): JDK 8 home (e.g., C:\Java\jdk1.8.0_202)
REM ============================================

REM 0) Check arguments
if "%~1"=="" (
  echo [ERROR] .class file path is required.
  echo Usage: %~nx0 "C:\path\to\MyClass.class" [JAVA8_HOME]
  exit /b 1
)
set "CLASS_PATH=%~1"

REM 1) Set JAVA8_HOME (arg2 overrides existing env var)
if not "%~2"=="" (
  set "JAVA8_HOME=%~2"
)
if "%JAVA8_HOME%"=="" (
  echo [ERROR] JAVA8_HOME is not set.
  echo Example: set JAVA8_HOME=C:\Java\jdk1.8.0_202
  exit /b 2
)

REM 2) Locate rt.jar (try both common locations)
set "RT1=%JAVA8_HOME%\jre\lib\rt.jar"
set "RT2=%JAVA8_HOME%\lib\rt.jar"
set "RTJAR="
if exist "%RT1%" ( set "RTJAR=%RT1%" ) else if exist "%RT2%" ( set "RTJAR=%RT2%" )

if "%RTJAR%"=="" (
  echo [ERROR] rt.jar not found.
  echo Checked:
  echo   %RT1%
  echo   %RT2%
  echo Ensure JAVA8_HOME points to a valid JDK 8 root.
  exit /b 3
)

echo [INFO] JAVA8_HOME=%JAVA8_HOME%
echo [INFO] rt.jar=%RTJAR%

REM 3) Run Gradle (must be executed in project root)
echo [INFO] Running Gradle...
gradle run --args="%CLASS_PATH%"
set "RC=%ERRORLEVEL%"

if not "%RC%"=="0" (
  echo [ERROR] Gradle run failed (exit code=%RC%)
  exit /b %RC%
)

echo [INFO] Done. Check the out/ folder for the generated JSON.
exit /b 0
