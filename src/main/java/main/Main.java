package main;


import directory_crawler.CrawlerJob;
import directory_crawler.DirectoryCrawler;
import job_dispatcher.JobDispatcher;
import job_dispatcher.ScanningJob;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {

    private static final int MB_SIZE = 1048576;
    private static final String[] TEST_COMMAND = {"ad example/data/corpus_sagan"};

    private static final String PROPERTIES_FILENAME = "src/main/resources/app.properties";
    private static final Properties properties = new Properties();

    private static final ConcurrentLinkedQueue<CrawlerJob> directoryNames = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<ScanningJob> scanningJobs = new ConcurrentLinkedQueue<>();

    private static DirectoryCrawler directoryCrawler;
    private static JobDispatcher jobDispatcher;

    private static final ExecutorService fileScanningThreadPool = Executors.newCachedThreadPool();
    private static final ExecutorCompletionService<Map<String, Integer>> completionService = new ExecutorCompletionService<>(fileScanningThreadPool);

    public static void main(String[] args) {
        loadProperties();
        initWorkers();
        forever();
    }

    private static void initWorkers() {
        // Directory crawler
        String fileCorpusPrefix = (String) properties.get("file_corpus_prefix");
        int dirCrawlerSleepTime = Integer.parseInt(String.valueOf(properties.get("dir_crawler_sleep_time")));
        directoryCrawler = new DirectoryCrawler(directoryNames, scanningJobs, fileCorpusPrefix, dirCrawlerSleepTime);
        Thread directoryCrawlerThread = new Thread(directoryCrawler, "directoryCrawler");
        directoryCrawlerThread.start();

        // Job dispatcher
        int fileScanningSizeLimit = Integer.parseInt(String.valueOf(properties.get("file_scanning_size_limit")));
        List<String> keywords = parseKeywords();
        jobDispatcher = new JobDispatcher(scanningJobs, completionService, fileScanningSizeLimit, keywords);
        Thread jobDispatcherThread = new Thread(jobDispatcher, "jobDispatcher");
        jobDispatcherThread.start();

        // FileScanner ThreadPool


    }

    private static void forever() {
        Scanner sc = new Scanner(System.in);

        while(true) {
            String line = sc.nextLine();


            if(line.equals("stop")) {
                System.out.println("Exiting. . .");
                fileScanningThreadPool.shutdown();
                directoryCrawler.stop();
                jobDispatcher.stop();
                break;
            }

            if(line.equals("cfs")) {
                System.out.println("cfs");
                continue;
            }

            if(line.equals("cws")) {
                System.out.println("cws");
                continue;
            }

            if(!line.contains(" ")) {
                System.out.println("Niste uneli dobru komandu");
                continue;
            }

            if(line.split(" ").length > 2) {
                System.out.println("Uneli ste previse argumenata");
                continue;
            }


            String command = line.split(" ")[0];
            String param = line.split(" ")[1];

            if(command.equals("ad")) {
                String dirName = "src/main/resources/" + param;
                if(Files.notExists(Path.of(dirName))) {
                    System.out.println("Could not find dir: " + dirName);
                    continue;
                }
                CrawlerJob crawlerJob = new CrawlerJob(dirName);
                directoryNames.add(crawlerJob);
            }

            if(command.equals("aw")) {
                String websiteName = param;
                try {
                    new URL(websiteName).toURI();
                } catch (URISyntaxException | MalformedURLException e) {
                    System.out.println("Los format urla");
                    continue;
                }
                System.out.println("Dodajemo url");
            }

            if(command.equals("query")) {
                if(!param.startsWith("file|") || !param.startsWith("web|")) {
                    System.out.println("Los format argumenta");
                    break;
                }
                String corpusName = param.split("\\|")[1];
                if(param.startsWith("file|")) {
                    System.out.println("Trazimo rez za file");
                    break;
                }
                System.out.println("Trazimo rez za web");
            }

            if(command.equals("get")) {
                System.out.println("get");
            }

        }
        sc.close();
    }

    private static void loadProperties() {
        FileReader fr = null;
        try {
            fr = new FileReader(PROPERTIES_FILENAME);
            properties.load(fr);

        } catch (IOException e) {
            System.err.println("Could not read properties file !!");
            System.err.println("Exiting. . .");
            System.exit(-1);
        } finally {
            try {
                if(fr != null) {
                        fr.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static List<String> parseKeywords() {
        String keywords_raw = String.valueOf(properties.get("keywords"));
        if(!keywords_raw.contains(",")) {
            System.err.println("Failed to parse keywords !!");
            System.err.println("Exiting. . .");
            System.exit(-1);
        }
        return new ArrayList<>(Arrays.asList(keywords_raw.split(",")));
    }

}
