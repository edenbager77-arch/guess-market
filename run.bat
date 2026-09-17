@echo off
REM ---------------------------------------------------------------
REM Guess Market - Exercise 2
REM Runs from any directory. Looks for JavaFX in three places:
REM   1. the bundled lib\ folder shipped next to this file
REM   2. the JAVAFX_HOME environment variable
REM   3. a JDK that already includes JavaFX (e.g. Liberica Full / Zulu FX)
REM ---------------------------------------------------------------
setlocal
set "DIR=%~dp0"

if exist "%DIR%lib\javafx.controls.jar" (
    set "MODPATH=%DIR%lib"
    goto :run
)

if defined JAVAFX_HOME (
    if exist "%JAVAFX_HOME%\lib\javafx.controls.jar" (
        set "MODPATH=%JAVAFX_HOME%\lib"
        goto :run
    )
)

REM No separate JavaFX found - try running anyway, in case the JDK bundles it.
echo.
echo Could not find a separate JavaFX SDK.
echo Trying to run with the JDK's own JavaFX (works on Liberica Full / Zulu FX).
echo.
java -cp "%DIR%engine.jar;%DIR%ui.jar" guessmarket.ui.Main
if errorlevel 1 (
    echo.
    echo ============================================================
    echo Could not start the application.
    echo JavaFX was not found on this machine.
    echo Please either:
    echo   - place a JavaFX SDK's lib folder next to this file as "lib", or
    echo   - set JAVAFX_HOME to your JavaFX SDK folder, or
    echo   - run with a JDK that bundles JavaFX.
    echo ============================================================
)
pause
exit /b

:run
java --module-path "%MODPATH%" --add-modules javafx.controls,javafx.fxml -cp "%DIR%engine.jar;%DIR%ui.jar" guessmarket.ui.Main
pause
