@echo off
if not exist out mkdir out
javac -d out src\Main.java || exit /b 1
echo Open http://localhost:8080  (admin / admin123)
java -cp out Main serve 8080
