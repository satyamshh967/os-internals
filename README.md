# os-internals 🐧

> **POSIX-compliant Shell & Operating System Systems Programming in Java.**

[![Language](https://img.shields.io/badge/Language-Java_21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://www.java.com)
[![Topic](https://img.shields.io/badge/Topic-Systems_Programming-blue?style=flat-square)](https://github.com/satyamshh967/os-internals)
[![Status](https://img.shields.io/badge/Status-Active_Development-brightgreen?style=flat-square)](https://github.com/satyamshh967/os-internals)

---

## 📖 Overview

`os-internals` explores fundamental operating systems concepts, process lifecycles, and systems programming by implementing a custom Unix-like command shell from scratch in Java.

Instead of relying on system-provided shells like Bash or Zsh, this project builds the command interpretation loop, environment parsing, executable path lookup, and stream redirection manually.

---

## ✨ Core Features

- **REPL & Command Parsing:** Continuous Read-Eval-Print Loop supporting command tokens, double/single quotes, escapes, and variable expansion.
- **Built-in Commands:**
  - `exit <code?>`: Clean shell termination with exit statuses.
  - `echo <args...>`: Argument rendering respecting single and double quotes.
  - `type <command>`: Checks if a command is a built-in or locates the binary path on `$PATH`.
  - `pwd`: Prints the current absolute working directory.
  - `cd <path>`: Directory traversal supporting absolute paths, relative paths, and `~` home directory shortcuts.
- **Executable Discovery:** Scans system `$PATH` environment variables, parses file permissions, and invokes external system binaries.
- **Process Management & Redirection:** Forking/executing external processes and capturing standard I/O streams (`stdout`, `stderr`).

---

## 🚀 Getting Started

### Prerequisites
- **Java Development Kit (JDK 21+)**
- **Apache Maven** or modern build tooling

### Build & Run
```bash
# Clone the repository
git clone https://github.com/satyamshh967/os-internals.git
cd os-internals/codecrafters-shell-java

# Compile with Maven
mvn clean compile

# Run the shell directly
./your_shell.sh
```

---

## 🛠️ Architecture
```text
User Input
    │
    ▼
Lexer / Tokenizer (Handles quotes, escapes, args)
    │
    ▼
Command Dispatcher
    ├── Built-in Handler (exit, echo, type, pwd, cd)
    └── External Binary Resolver ($PATH lookup -> ProcessBuilder -> I/O Pipe)
```

---

## 👤 Author
- **Satyam Sharma** - [@satyamshh967](https://github.com/satyamshh967)
