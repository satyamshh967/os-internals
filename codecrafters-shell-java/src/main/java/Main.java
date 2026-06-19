import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

public class Main {

    static class Job {
        int number;
        long pid;
        String command;
        String status;
        Process process;

        Job(int number, long pid, String command, String status, Process process) {
            this.number = number;
            this.pid = pid;
            this.command = command;
            this.status = status;
            this.process = process;
        }
    }

    static List<Job> backgroundJobs = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        File currentDirectory = new File(System.getProperty("user.dir"));

        while (true) {
            for (int i = 0; i < backgroundJobs.size(); i++) {
                Job job = backgroundJobs.get(i);
                if (!job.process.isAlive()) {
                    job.status = "Done";
                    String marker = " ";
                    if (i == backgroundJobs.size() - 1) marker = "+";
                    else if (i == backgroundJobs.size() - 2) marker = "-";
                    String displayCommand = job.command;
                    if (displayCommand.endsWith("&"))
                        displayCommand = displayCommand.substring(0, displayCommand.length() - 1).trim();
                    System.out.printf("[%d]%s  %-24s%s%n", job.number, marker, job.status, displayCommand);
                    backgroundJobs.remove(i);
                    i--;
                }
            }

            System.out.print("$ ");
            if (!scanner.hasNextLine()) break;

            String input = scanner.nextLine();
            if (input.trim().isEmpty()) continue;

            List<String> parsed = parseInput(input);
            if (parsed.isEmpty()) continue;

            String outputFile = null;
            String errorFile = null;
            boolean appendOutput = false;
            boolean appendError = false;
            boolean isBackgroundJob = false;
            List<String> commandArgs = new ArrayList<>();

            for (int i = 0; i < parsed.size(); i++) {
                String token = parsed.get(i);
                if (token.equals("&") && i == parsed.size() - 1) {
                    isBackgroundJob = true;
                } else if ((token.equals(">") || token.equals("1>")) && i + 1 < parsed.size()) {
                    outputFile = parsed.get(++i);
                    appendOutput = false;
                } else if ((token.equals(">>") || token.equals("1>>")) && i + 1 < parsed.size()) {
                    outputFile = parsed.get(++i);
                    appendOutput = true;
                } else if (token.equals("2>") && i + 1 < parsed.size()) {
                    errorFile = parsed.get(++i);
                    appendError = false;
                } else if (token.equals("2>>") && i + 1 < parsed.size()) {
                    errorFile = parsed.get(++i);
                    appendError = true;
                } else {
                    commandArgs.add(token);
                }
            }

            parsed = commandArgs;
            if (parsed.isEmpty()) continue;

            try {
                if (outputFile != null) new FileOutputStream(new File(outputFile), appendOutput).close();
                if (errorFile != null) new FileOutputStream(new File(errorFile), appendError).close();
            } catch (Exception e) {
                System.out.println("Error creating redirection files: " + e.getMessage());
            }

            if (parsed.contains("|")) {
                handlePipeline(parsed, outputFile, errorFile, appendOutput, appendError, isBackgroundJob, input, currentDirectory);
                continue;
            }

            String command = parsed.get(0);

            if (command.equals("exit") && (parsed.size() == 1 || (parsed.size() == 2 && parsed.get(1).equals("0")))) {
                break;
            } else if (command.equals("echo")) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < parsed.size(); i++) {
                    if (i > 1) sb.append(" ");
                    sb.append(parsed.get(i));
                }
                String out = sb.toString();
                if (outputFile != null) writeToFile(outputFile, out + System.lineSeparator(), appendOutput);
                else System.out.println(out);
            } else if (command.equals("pwd")) {
                String out = currentDirectory.getAbsolutePath();
                if (outputFile != null) writeToFile(outputFile, out + System.lineSeparator(), appendOutput);
                else System.out.println(out);
            } else if (command.equals("cd")) {
                if (parsed.size() < 2) continue;
                String path = parsed.get(1);
                File target;
                if (path.equals("~")) target = new File(System.getenv("HOME"));
                else if (path.startsWith("/")) target = new File(path);
                else target = new File(currentDirectory, path);
                target = target.getCanonicalFile();
                if (target.exists() && target.isDirectory()) {
                    currentDirectory = target;
                } else {
                    String errorMsg = "cd: " + path + ": No such file or directory";
                    if (errorFile != null) writeToFile(errorFile, errorMsg + System.lineSeparator(), appendError);
                    else System.out.println(errorMsg);
                }
            } else if (command.equals("jobs")) {
                for (int i = 0; i < backgroundJobs.size(); i++) {
                    Job job = backgroundJobs.get(i);
                    String marker = " ";
                    if (i == backgroundJobs.size() - 1) marker = "+";
                    else if (i == backgroundJobs.size() - 2) marker = "-";
                    if (!job.process.isAlive()) {
                        job.status = "Done";
                        String displayCommand = job.command;
                        if (displayCommand.endsWith("&"))
                            displayCommand = displayCommand.substring(0, displayCommand.length() - 1).trim();
                        System.out.printf("[%d]%s  %-24s%s%n", job.number, marker, job.status, displayCommand);
                        backgroundJobs.remove(i);
                        i--;
                    } else {
                        job.status = "Running";
                        System.out.printf("[%d]%s  %-24s%s%n", job.number, marker, job.status, job.command);
                    }
                }
            } else if (command.equals("type")) {
                if (parsed.size() < 2) continue;
                String cmdToCheck = parsed.get(1);
                String out;
                if (isBuiltin(cmdToCheck)) {
                    out = cmdToCheck + " is a shell builtin";
                } else {
                    String path = getExecutablePath(cmdToCheck, currentDirectory);
                    out = path != null ? cmdToCheck + " is " + path : cmdToCheck + ": not found";
                }
                if (outputFile != null) writeToFile(outputFile, out + System.lineSeparator(), appendOutput);
                else System.out.println(out);
            } else {
                String executablePath = getExecutablePath(command, currentDirectory);
                if (executablePath != null) {
                    try {
                        ProcessBuilder pb = new ProcessBuilder(parsed);
                        pb.directory(currentDirectory);
                        if (outputFile != null) {
                            if (appendOutput) pb.redirectOutput(ProcessBuilder.Redirect.appendTo(new File(outputFile)));
                            else pb.redirectOutput(new File(outputFile));
                        } else {
                            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                        }
                        if (errorFile != null) {
                            if (appendError) pb.redirectError(ProcessBuilder.Redirect.appendTo(new File(errorFile)));
                            else pb.redirectError(new File(errorFile));
                        } else {
                            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                        }
                        pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
                        Process process = pb.start();
                        if (isBackgroundJob) {
                            int newJobNumber = 1;
                            for (Job j : backgroundJobs) if (j.number >= newJobNumber) newJobNumber = j.number + 1;
                            System.out.println("[" + newJobNumber + "] " + process.pid());
                            backgroundJobs.add(new Job(newJobNumber, process.pid(), input.trim(), "Running", process));
                        } else {
                            process.waitFor();
                        }
                    } catch (Exception e) {
                        System.out.println("Error executing program: " + e.getMessage());
                    }
                } else {
                    String errorMsg = command + ": command not found";
                    if (errorFile != null) writeToFile(errorFile, errorMsg + System.lineSeparator(), appendError);
                    else System.out.println(errorMsg);
                }
            }
        }

        scanner.close();
    }

    private static void handlePipeline(List<String> parsed, String outputFile, String errorFile,
                                        boolean appendOutput, boolean appendError,
                                        boolean isBackgroundJob, String input, File currentDirectory) throws Exception {

        List<List<String>> segments = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String token : parsed) {
            if (token.equals("|")) {
                segments.add(new ArrayList<>(current));
                current.clear();
            } else {
                current.add(token);
            }
        }
        segments.add(current);

        int n = segments.size();

        boolean anyBuiltin = segments.stream().anyMatch(seg -> !seg.isEmpty() && isBuiltin(seg.get(0)));

        if (!anyBuiltin) {
            List<ProcessBuilder> builders = new ArrayList<>();
            for (List<String> seg : segments) {
                if (seg.isEmpty()) {
                    System.out.println("Invalid pipeline syntax");
                    return;
                }
                String cmd = seg.get(0);
                String path = getExecutablePath(cmd, currentDirectory);
                if (path == null) {
                    System.out.println(cmd + ": command not found");
                    return;
                }
                ProcessBuilder pb = new ProcessBuilder(seg);
                pb.directory(currentDirectory);
                builders.add(pb);
            }

            ProcessBuilder firstPb = builders.get(0);
            ProcessBuilder lastPb = builders.get(builders.size() - 1);

            firstPb.redirectInput(ProcessBuilder.Redirect.INHERIT);

            for (ProcessBuilder pb : builders) {
                if (pb != lastPb) pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            }

            if (outputFile != null) {
                if (appendOutput) lastPb.redirectOutput(ProcessBuilder.Redirect.appendTo(new File(outputFile)));
                else lastPb.redirectOutput(new File(outputFile));
            } else {
                lastPb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            }

            if (errorFile != null) {
                if (appendError) lastPb.redirectError(ProcessBuilder.Redirect.appendTo(new File(errorFile)));
                else lastPb.redirectError(new File(errorFile));
            } else {
                lastPb.redirectError(ProcessBuilder.Redirect.INHERIT);
            }

            List<Process> processes = ProcessBuilder.startPipeline(builders);
            Process lastProcess = processes.get(processes.size() - 1);

            if (isBackgroundJob) {
                int newJobNumber = 1;
                for (Job j : backgroundJobs) if (j.number >= newJobNumber) newJobNumber = j.number + 1;
                System.out.println("[" + newJobNumber + "] " + lastProcess.pid());
                backgroundJobs.add(new Job(newJobNumber, lastProcess.pid(), input.trim(), "Running", lastProcess));
            } else {
                for (Process p : processes) p.waitFor();
            }
            return;
        }

        PipedInputStream[] pipeIns = new PipedInputStream[n - 1];
        PipedOutputStream[] pipeOuts = new PipedOutputStream[n - 1];
        for (int i = 0; i < n - 1; i++) {
            pipeIns[i] = new PipedInputStream(65536);
            pipeOuts[i] = new PipedOutputStream(pipeIns[i]);
        }

        List<Thread> threads = new ArrayList<>();
        List<Process> processes = new ArrayList<>();
        boolean invalidCommand = false;

        for (int i = 0; i < n; i++) {
            List<String> seg = segments.get(i);
            if (seg.isEmpty()) {
                System.out.println("Invalid pipeline syntax");
                invalidCommand = true;
                break;
            }

            String cmd = seg.get(0);

            OutputStream stdoutStream;
            if (i == n - 1) {
                if (outputFile != null) {
                    stdoutStream = appendOutput
                            ? new FileOutputStream(new File(outputFile), true)
                            : new FileOutputStream(new File(outputFile), false);
                } else {
                    stdoutStream = System.out;
                }
            } else {
                stdoutStream = pipeOuts[i];
            }

            if (isBuiltin(cmd)) {
                final InputStream finalIn = (i == 0) ? System.in : pipeIns[i - 1];
                final OutputStream finalOut = stdoutStream;
                final List<String> finalSeg = seg;
                final File finalDir = currentDirectory;

                Thread t = new Thread(() -> {
                    try {
                        runBuiltinInThread(finalSeg, finalIn, finalOut, finalDir);
                    } catch (Exception e) {
                        System.err.println("Builtin error: " + e.getMessage());
                    } finally {
                        if (finalOut != System.out) {
                            try { finalOut.close(); } catch (IOException ignored) {}
                        } else {
                            ((PrintStream) finalOut).flush();
                        }
                    }
                });
                threads.add(t);
                t.start();
            } else {
                String path = getExecutablePath(cmd, currentDirectory);
                if (path == null) {
                    System.out.println(cmd + ": command not found");
                    invalidCommand = true;
                    break;
                }

                ProcessBuilder pb = new ProcessBuilder(seg);
                pb.directory(currentDirectory);

                if (i == 0) {
                    pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
                } else {
                    pb.redirectInput(ProcessBuilder.Redirect.PIPE);
                }

                if (i == n - 1) {
                    if (outputFile != null) {
                        if (appendOutput) pb.redirectOutput(ProcessBuilder.Redirect.appendTo(new File(outputFile)));
                        else pb.redirectOutput(new File(outputFile));
                    } else {
                        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                    }
                } else {
                    pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
                }

                if (errorFile != null && i == n - 1) {
                    if (appendError) pb.redirectError(ProcessBuilder.Redirect.appendTo(new File(errorFile)));
                    else pb.redirectError(new File(errorFile));
                } else {
                    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                }

                Process proc = pb.start();
                processes.add(proc);

                if (i > 0) {
                    final InputStream src = pipeIns[i - 1];
                    final OutputStream dst = proc.getOutputStream();
                    Thread feeder = new Thread(() -> {
                        try {
                            src.transferTo(dst);
                        } catch (IOException ignored) {
                        } finally {
                            try { dst.close(); } catch (IOException ignored) {}
                        }
                    });
                    feeder.setDaemon(true);
                    feeder.start();
                    threads.add(feeder);
                }

                if (i < n - 1) {
                    final InputStream procOut = proc.getInputStream();
                    final PipedOutputStream nextPipe = pipeOuts[i];
                    Thread forwarder = new Thread(() -> {
                        try {
                            procOut.transferTo(nextPipe);
                        } catch (IOException ignored) {
                        } finally {
                            try { nextPipe.close(); } catch (IOException ignored) {}
                        }
                    });
                    forwarder.setDaemon(true);
                    forwarder.start();
                    threads.add(forwarder);
                }
            }
        }

        if (!invalidCommand) {
            for (Process p : processes) p.waitFor();
            for (Thread t : threads) {
                t.join(5000);
            }
        }
    }

    private static void runBuiltinInThread(List<String> seg, InputStream stdin, OutputStream stdout, File currentDirectory) throws Exception {
        String cmd = seg.get(0);
        PrintStream out = (stdout instanceof PrintStream) ? (PrintStream) stdout : new PrintStream(stdout, true);

        if (cmd.equals("echo")) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < seg.size(); i++) {
                if (i > 1) sb.append(" ");
                sb.append(seg.get(i));
            }
            out.println(sb.toString());
            out.flush();
        } else if (cmd.equals("type")) {
            if (seg.size() < 2) return;
            String cmdToCheck = seg.get(1);
            String result;
            if (isBuiltin(cmdToCheck)) {
                result = cmdToCheck + " is a shell builtin";
            } else {
                String path = getExecutablePath(cmdToCheck, currentDirectory);
                result = path != null ? cmdToCheck + " is " + path : cmdToCheck + ": not found";
            }
            out.println(result);
            out.flush();
        } else if (cmd.equals("pwd")) {
            out.println(currentDirectory.getAbsolutePath());
            out.flush();
        }
    }

    private static boolean isBuiltin(String cmd) {
        return cmd.equals("echo") || cmd.equals("exit") || cmd.equals("type")
                || cmd.equals("pwd") || cmd.equals("cd") || cmd.equals("jobs");
    }

    private static void writeToFile(String filePath, String content, boolean append) {
        try {
            if (append) {
                Files.writeString(new File(filePath).toPath(), content, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                Files.writeString(new File(filePath).toPath(), content);
            }
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }
    }

    private static List<String> parseInput(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean escaping = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (escaping) { current.append(c); escaping = false; continue; }
            if (inDoubleQuotes && c == '\\') {
                if (i + 1 < input.length()) {
                    char next = input.charAt(i + 1);
                    if (next == '"' || next == '\\') { current.append(next); i++; }
                    else current.append('\\');
                } else current.append('\\');
                continue;
            }
            if (!inSingleQuotes && !inDoubleQuotes && c == '\\') { escaping = true; continue; }
            if (c == '\'' && !inDoubleQuotes) { inSingleQuotes = !inSingleQuotes; continue; }
            if (c == '"' && !inSingleQuotes) { inDoubleQuotes = !inDoubleQuotes; continue; }
            if (Character.isWhitespace(c) && !inSingleQuotes && !inDoubleQuotes) {
                if (current.length() > 0) { tokens.add(current.toString()); current.setLength(0); }
            } else {
                current.append(c);
            }
        }
        if (escaping) current.append('\\');
        if (current.length() > 0) tokens.add(current.toString());
        return tokens;
    }

    private static String getExecutablePath(String command, File currentDirectory) {
        if (command.contains("/")) {
            File file = command.startsWith("/") ? new File(command) : new File(currentDirectory, command);
            try { file = file.getCanonicalFile(); } catch (Exception e) { return null; }
            return (file.exists() && file.isFile() && file.canExecute()) ? file.getAbsolutePath() : null;
        }
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(":")) {
                File file = new File(dir, command);
                if (file.exists() && file.isFile() && file.canExecute()) return file.getAbsolutePath();
            }
        }
        return null;
    }
}