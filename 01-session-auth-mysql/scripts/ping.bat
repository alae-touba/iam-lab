@echo off
REM ping.bat - Access the secure ping endpoint
REM Usage: ping.bat

curl -i -X GET ^
  -H "Content-Type: application/json" ^
  -b cookies.txt ^
  http://localhost:8080/api/secure/ping