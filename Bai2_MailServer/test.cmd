@echo off
setlocal
cd /d "%~dp0"
call build.cmd
if errorlevel 1 exit /b 1
javac -encoding UTF-8 -cp out -d out tests\MailIntegrationTest.java
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -cp out MailIntegrationTest
exit /b %errorlevel%
