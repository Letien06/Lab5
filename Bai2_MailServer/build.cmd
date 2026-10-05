@echo off
setlocal
cd /d "%~dp0"
if not exist out mkdir out
javac -encoding UTF-8 -cp ".;lib\*" -d out src\*.java
exit /b %errorlevel%
