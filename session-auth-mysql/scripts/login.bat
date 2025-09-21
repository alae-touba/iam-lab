@echo off
REM login.bat - Login with username/email and password
REM Usage: login.bat [usernameOrEmail] [password]

set USERNAME_OR_EMAIL=%1
if "%USERNAME_OR_EMAIL%"=="" set USERNAME_OR_EMAIL=testuser

set PASSWORD=%2
if "%PASSWORD%"=="" set PASSWORD=password

curl -i -X POST ^
  -H "Content-Type: application/x-www-form-urlencoded" ^
  -d "usernameOrEmail=%USERNAME_OR_EMAIL%^&password=%PASSWORD%" ^
  -c cookies.txt ^
  http://localhost:8080/api/auth/login

echo.
echo Session cookie saved to cookies.txt