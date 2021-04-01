package main;


import directory_crawler.CrawlerJob;
import directory_crawler.DirectoryCrawler;
import job_dispatcher.*;
import org.apache.commons.validator.routines.UrlValidator;
import result_retriever.ResultRetriever;
import scanner.file.FileScanner;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

public class Main {

    private static final int MB_SIZE = 1048576;
    private static final String[] TEST_COMMAND = {"ad example/data/corpus_sagan"};

    private static final String PROPERTIES_FILENAME = "src/main/resources/app.properties";
    private static final Properties properties = new Properties();
    private static final Map<String, Object> watchedDirectories = new ConcurrentHashMap<>();

    private static final ConcurrentLinkedQueue<CrawlerJob> directoryNames = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<ScanningJob> scanningJobs = new ConcurrentLinkedQueue<>();
    private static final BlockingQueue<ScanningJob> fileScanningJobs = new LinkedBlockingQueue<>();
    private static final BlockingQueue<ScanningJob> webScanningJobs = new LinkedBlockingQueue<>();
    private static final ConcurrentLinkedQueue<ResultJob> resultJobs = new ConcurrentLinkedQueue<>();

    private static DirectoryCrawler directoryCrawler;
    private static JobDispatcher jobDispatcher;
    private static FileScanner fileScanner;
    private static ResultRetriever resultRetriever;

    private static final ExecutorService fileScanningThreadPool = Executors.newCachedThreadPool();
    private static final ExecutorCompletionService<Map<String, Integer>> fileScanningCompletionService = new ExecutorCompletionService<>(fileScanningThreadPool);

    private static final ExecutorService resultRetrieverThreadPool = Executors.newCachedThreadPool();
    private static final ExecutorCompletionService<Map<String, Integer>> resultRetrieverCompletionService = new ExecutorCompletionService<>(resultRetrieverThreadPool);

    public static void main(String[] args) {
        loadProperties();
        initWorkers();
        forever();
    }

    private static void initWorkers() {
        // Directory crawler
        String fileCorpusPrefix = (String) properties.get("file_corpus_prefix");
        int dirCrawlerSleepTime = Integer.parseInt(String.valueOf(properties.get("dir_crawler_sleep_time")));
        directoryCrawler = new DirectoryCrawler(directoryNames, scanningJobs, watchedDirectories, fileCorpusPrefix, dirCrawlerSleepTime);
        Thread directoryCrawlerThread = new Thread(directoryCrawler, "directoryCrawler");
        directoryCrawlerThread.start();

        // Job dispatcher
        jobDispatcher = new JobDispatcher(scanningJobs, fileScanningJobs, webScanningJobs);
        Thread jobDispatcherThread = new Thread(jobDispatcher, "jobDispatcher");
        jobDispatcherThread.start();

        // FileScanner ThreadPool
        int fileScanningSizeLimit = Integer.parseInt(String.valueOf(properties.get("file_scanning_size_limit")));
        List<String> keywords = parseKeywords();
        fileScanner = new FileScanner(fileScanningJobs, resultJobs, fileScanningThreadPool, fileScanningCompletionService, fileScanningSizeLimit, keywords);
        Thread fileScannerThread = new Thread(fileScanner, "fileScanner");
        fileScannerThread.start();

        // Result retriever
        int urlRefreshTime = Integer.parseInt(String.valueOf(properties.get("url_refresh_time")));
        resultRetriever = new ResultRetriever(resultJobs, resultRetrieverThreadPool, resultRetrieverCompletionService, watchedDirectories, urlRefreshTime);
        Thread resultRetrieverThread = new Thread(resultRetriever, "resultRetriever");
        resultRetrieverThread.start();

    }

    private static void forever() {
        Scanner sc = new Scanner(System.in);

        while(true) {
            String line = sc.nextLine();


            if(line.equals("stop")) {
                System.out.println("Exiting gracefully. . .");
                directoryCrawler.stop();
                jobDispatcher.stop();
                fileScanner.stop();
                resultRetriever.stop();
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

            if(!line.contains("query") && !line.contains("ad") && !line.contains("aw") && !line.contains("get")) {
                System.out.println("Wrong command");
                continue;
            }

            if(!line.contains(" ")) {
                System.out.println("No argument provided");
                continue;
            }

            if(line.split(" ").length > 2) {
                System.out.println("Only one argument required");
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
                UrlValidator urlValidator = new UrlValidator(new String[]{"http", "https"});
                if(!urlValidator.isValid(param)) {
                    System.out.println("Bad url format");
                    continue;
                }
                continue;
            }

            if(command.equals("get")) {
                if( !param.startsWith("file|") && !param.startsWith("web|")) {
                    System.out.println("Supported formats: file|/corpus_name/, file|summary, web|/corpus_name/, web|summary");
                    continue;
                }

                // Finding summary
                if(param.endsWith("|summary")) {
                    ScanningJobType type = (param.startsWith("file|")) ? ScanningJobType.FILE_SCANNING_JOB : ScanningJobType.WEB_SCANNING_JOB;
                    Map<String, Map<String, Integer>> result = resultRetriever.getResultSummary(type);
                    result.forEach((key, value) -> System.out.println(key + ": " + value));
                    continue;
                }

                // Finding for corpus
                String corpusName = param.split("\\|")[1];
                if(param.startsWith("file|")) {
                    corpusName = "src/main/resources/" + corpusName;
                    Map<String, Integer> result = resultRetriever.getResult(corpusName, ScanningJobType.FILE_SCANNING_JOB);
                    if(result != null) {
                        System.out.println(result);
                    }
                    continue;
                }
                System.out.println("Trazimo rez za web");
            }

            if(command.equals("query")) {
                if( !param.startsWith("file|") && !param.startsWith("web|")) {
                    System.out.println("Supported formats: file|corpus_name, web|corpus_name");
                    continue;
                }

                System.out.println("query");
            }

        }
        sc.close();

        System.out.println("Main shutting down");
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

    private static void printActiveThreads() {
        int noThreads = 2;
        while(noThreads > 1) {
            System.out.println();
            System.out.println();

            ThreadGroup currentGroup = Thread.currentThread().getThreadGroup();
            noThreads = currentGroup.activeCount();
            Thread[] lstThreads = new Thread[noThreads];
            currentGroup.enumerate(lstThreads);
            for (int i = 0; i < noThreads; i++)
                System.out.println("Thread No:" + i + " = " + lstThreads[i].getName());

            System.out.println();
            System.out.println();
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

}
