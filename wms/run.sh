#!/bin/sh
mkdir -p out && javac -d out src/Main.java && echo "Open http://localhost:8080  (admin / admin123)" && java -cp out Main serve 8080
