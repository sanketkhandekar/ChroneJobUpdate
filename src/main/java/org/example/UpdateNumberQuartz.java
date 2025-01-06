package org.example;

import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

public class UpdateNumberQuartz {

    private static final String NUMBER_FILE = "number.txt";
    private static final String SCRIPT_DIR = Paths.get("").toAbsolutePath().toString();

    public static void main(String[] args) throws SchedulerException {
        // Create a scheduler factory
        SchedulerFactory schedulerFactory = new StdSchedulerFactory();
        Scheduler scheduler = schedulerFactory.getScheduler();

        // Define a job and tie it to our Job class
        JobDetail job = JobBuilder.newJob(UpdateNumberJob.class)
                .withIdentity("updateNumberJob", "group1")
                .build();

        // Trigger the job at a random time (daily)
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("updateNumberTrigger", "group1")
                .withSchedule(CronScheduleBuilder.cronSchedule(getRandomCronExpression()))
                .build();

        // Tell Quartz to schedule the job using our trigger
        scheduler.scheduleJob(job, trigger);

        // Start the scheduler
        scheduler.start();
    }

    // Generates a random cron expression for a random time each day
    private static String getRandomCronExpression() {
        Random random = new Random();
        int randomHour = random.nextInt(24); // 0-23 hours
        int randomMinute = random.nextInt(60); // 0-59 minutes
        return String.format("%d %d * * * ?", randomMinute, randomHour);
    }

    // Job class that updates the number and performs Git operations
    public static class UpdateNumberJob implements Job {

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            try {
                int currentNumber = readNumber();
                int newNumber = currentNumber + 1;
                writeNumber(newNumber);

                gitCommit();
                gitPush();
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                e.printStackTrace();
            }
        }

        private static int readNumber() throws IOException {
            Path path = Paths.get(NUMBER_FILE);
            return Integer.parseInt(Files.readString(path).trim());
        }

        private static void writeNumber(int num) throws IOException {
            Path path = Paths.get(NUMBER_FILE);
            Files.writeString(path, String.valueOf(num));
        }

        private static void gitCommit() throws IOException, InterruptedException {
            ProcessBuilder builder = new ProcessBuilder("git", "add", NUMBER_FILE);
            runProcess(builder);

            String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String commitMessage = "Update number: " + date;

            builder = new ProcessBuilder("git", "commit", "-m", commitMessage);
            runProcess(builder);
        }

        private static void gitPush() throws IOException, InterruptedException {
            ProcessBuilder builder = new ProcessBuilder("git", "push");
            Process process = builder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }

                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    System.err.println("Error pushing to GitHub:");
                    while ((line = errorReader.readLine()) != null) {
                        System.err.println(line);
                    }
                } else {
                    System.out.println("Changes pushed to GitHub successfully.");
                }
            }
        }

        private static void runProcess(ProcessBuilder builder) throws IOException, InterruptedException {
            Process process = builder.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    StringBuilder errorMessage = new StringBuilder();
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        errorMessage.append(line).append("\n");
                    }
                    throw new IOException("Command failed with error: " + errorMessage);
                }
            }
        }
    }
}
