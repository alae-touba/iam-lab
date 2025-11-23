@echo off
REM logout.bat - Logout and invalidate session
REM Usage: logout.bat

curl -i -X POST ^
  -H "Content-Type: application/json" ^
  -b cookies.txt ^
  http://localhost:8080/api/auth/logout

echo.
echo Session cookie cleared