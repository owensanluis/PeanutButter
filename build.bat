@echo off
setlocal
cd /d "%~dp0"
if exist build\classes rmdir /s /q build\classes
mkdir build\classes
if not exist build\test mkdir build\test
if not exist dist\lib mkdir dist\lib
(for /r src %%f in (*.java) do @echo "%%f") > build\sources.txt
javac -source 8 -target 8 -encoding UTF-8 -cp sqlite-jdbc-3.23.1.jar -d build\classes @build\sources.txt
if errorlevel 1 exit /b 1
jar cfm dist\CSSECDV.jar manifest.mf -C build\classes .
if errorlevel 1 exit /b 1
copy /y sqlite-jdbc-3.23.1.jar dist\lib\ >nul
if not exist dist\database.db copy database.db dist\database.db >nul
if /I "%~1"=="test" (
  javac -source 8 -target 8 -encoding UTF-8 -cp "build\classes;sqlite-jdbc-3.23.1.jar" -d build\test test\Controller\SecurityRegression.java
  if errorlevel 1 exit /b 1
  java -Djava.awt.headless=true -cp "build\test;build\classes;sqlite-jdbc-3.23.1.jar" Controller.SecurityRegression
  if errorlevel 1 exit /b 1
)
endlocal
