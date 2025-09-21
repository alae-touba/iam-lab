@echo off
REM me.bat - Get current authenticated user
REM Usage: me.bat

curl -i -X GET ^
  -H "Content-Type: application/json" ^
  -b cookies.txt ^
  http://localhost:8080/api/auth/me