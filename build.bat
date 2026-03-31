@echo off
echo ========================================
echo  RandomEffectsPlugin - Build Script
echo ========================================

REM Delete broken jar from previous attempt if too small
if exist "spigot-api.jar" (
    for %%A in (spigot-api.jar) do (
        if %%~zA LSS 100000 (
            echo Removing broken spigot-api.jar from last attempt...
            del spigot-api.jar
        )
    )
)

REM Download Spigot API if not present
if not exist "spigot-api.jar" (
    echo Downloading Spigot API - mirror 1...
    curl -L "https://repo.papermc.io/repository/maven-public/org/spigotmc/spigot-api/1.21.4-R0.1-SNAPSHOT/spigot-api-1.21.4-R0.1-SNAPSHOT-shaded.jar" -o spigot-api.jar 2>nul

    for %%A in (spigot-api.jar) do (
        if %%~zA LSS 100000 (
            echo Mirror 1 failed, trying mirror 2...
            del spigot-api.jar 2>nul
            curl -L "https://repo1.maven.org/maven2/org/spigotmc/spigot-api/1.21.4-R0.1-SNAPSHOT/spigot-api-1.21.4-R0.1-SNAPSHOT-shaded.jar" -o spigot-api.jar 2>nul
        )
    )

    for %%A in (spigot-api.jar) do (
        if %%~zA LSS 100000 (
            echo.
            echo FAILED to download Spigot API automatically.
            echo.
            echo Please do this manually:
            echo 1. Open this URL in your browser:
            echo    https://repo.papermc.io/repository/maven-public/org/spigotmc/spigot-api/1.21.4-R0.1-SNAPSHOT/spigot-api-1.21.4-R0.1-SNAPSHOT-shaded.jar
            echo 2. Save the file as spigot-api.jar in this folder
            echo 3. Run build.bat again
            del spigot-api.jar 2>nul
            pause
            exit /b 1
        )
    )
    echo Spigot API downloaded successfully!
)

echo Compiling...
mkdir classes 2>nul
javac -cp spigot-api.jar -d classes RandomEffectsPlugin.java
if errorlevel 1 (
    echo COMPILE FAILED. Make sure Java JDK 21 is installed.
    pause
    exit /b 1
)

echo Packaging jar...
mkdir jartemp 2>nul
xcopy /s /q classes\* jartemp\ >nul
copy plugin.yml jartemp\ >nul
cd jartemp
jar cf ..\RandomEffectsPlugin.jar .
cd ..
rmdir /s /q jartemp
rmdir /s /q classes

echo.
echo ========================================
echo  SUCCESS! RandomEffectsPlugin.jar ready
echo  Upload it to your PebbleHost plugins/ folder
echo ========================================
pause
