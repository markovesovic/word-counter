package main;


import jobs.*;
import directory_crawler.DirectoryCrawler;
import job_dispatcher.*;
import org.apache.commons.validator.routines.UrlValidator;
import result_retriever.ResultRetriever;
import scanner.file.FileScanner;
import scanner.web.WebScanner;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

public class Main {

    private static final String PROPERTIES_FILENAME = "src/main/resources/app.properties";
    private static final Properties properties = new Properties();
    private static int hopCount;

    private static final Map<String, Object> watchedDirectories = new ConcurrentHashMap<>();
    private static final Map<String, Object> watchedUrls = new ConcurrentHashMap<>();
    private static final Map<String, Object> availableDomains = new ConcurrentHashMap<>();

    private static final BlockingQueue<CrawlerJob> crawlingJobs = new LinkedBlockingQueue<>();
    private static final BlockingQueue<Job> scanningJobs = new LinkedBlockingQueue<>();
    private static final BlockingQueue<FileScanningJob> fileScanningJobs = new LinkedBlockingQueue<>();
    private static final BlockingQueue<WebScanningJob> webScanningJobs = new LinkedBlockingQueue<>();
    private static final BlockingQueue<Job> resultJobs = new LinkedBlockingQueue<>();

    private static DirectoryCrawler directoryCrawler;
    private static JobDispatcher jobDispatcher;
    private static FileScanner fileScanner;
    private static WebScanner webScanner;
    private static ResultRetriever resultRetriever;

    public static void main(String[] args) {
        DebugStream.activate();
        loadProperties();
        initWorkers();
        forever();
    }


    // TODO: Poll -> Take
    private static void initWorkers() {

        String fileCorpusPrefix = (String) properties.get("file_corpus_prefix");
        int dirCrawlerSleepTime = Integer.parseInt(String.valueOf(properties.get("dir_crawler_sleep_time")));
        directoryCrawler = new DirectoryCrawler(crawlingJobs,
                                                scanningJobs,
                                                watchedDirectories,
                                                fileCorpusPrefix,
                                                dirCrawlerSleepTime);
        Thread directoryCrawlerThread = new Thread(directoryCrawler, "DirectoryCrawler");
        directoryCrawlerThread.start();

        jobDispatcher = new JobDispatcher(scanningJobs,
                                          fileScanningJobs,
                                          webScanningJobs);
        Thread jobDispatcherThread = new Thread(jobDispatcher, "JobDispatcher");
        jobDispatcherThread.start();

        int fileScanningSizeLimit = Integer.parseInt(String.valueOf(properties.get("file_scanning_size_limit")));
        List<String> keywords = parseKeywords();
        fileScanner = new FileScanner(fileScanningJobs,
                                      resultJobs,
                                      fileScanningSizeLimit,
                                      keywords);
        Thread fileScannerThread = new Thread(fileScanner, "FileScanner");
        fileScannerThread.start();

        webScanner = new WebScanner(webScanningJobs,
                                    resultJobs,
                                    watchedUrls,
                                    availableDomains,
                                    keywords);
        Thread webScannerThread = new Thread(webScanner, "WebScanner");
        webScannerThread.start();

        int urlRefreshTime = Integer.parseInt(String.valueOf(properties.get("url_refresh_time")));
        resultRetriever = new ResultRetriever(resultJobs,
                                              watchedUrls,
                                              availableDomains,
                                              urlRefreshTime);
        Thread resultRetrieverThread = new Thread(resultRetriever, "ResultRetriever");
        resultRetrieverThread.start();


    }

    private static void forever() {
        Scanner sc = new Scanner(System.in);

        while(true) {
            String line = sc.nextLine();

            if(line.equals("test")) {
                resultRetriever.Test();
                continue;
            }
            if(line.equals("ping")) {
                System.out.println("pong");
                continue;
            }
            if(line.equals("domains")) {
                resultRetriever.domains();
                continue;
            }

            if(line.equals("stop")) {
                System.out.println("Exiting gracefully. . .");
                directoryCrawler.stop();
                jobDispatcher.stop();
                fileScanner.stop();
                webScanner.stop();
                resultRetriever.stop();
                break;
            }

            if(line.equals("cfs")) {
                resultRetriever.clearFileSummary();
                continue;
            }

            if(line.equals("cws")) {
                resultRetriever.clearWebSummary();
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
                try {
                    String dirName = "src/main/resources/" + param;

                    if (Files.notExists(Path.of(dirName))) {
                        System.out.println("Could not find dir: " + dirName);
                        continue;
                    }
                    CrawlerJob crawlerJob = new CrawlerJob(dirName);
                    crawlingJobs.add(crawlerJob);
                } catch (InvalidPathException e) {
                    System.out.println("Invalid path name");
                }
            }

            if(command.equals("aw")) {
                UrlValidator urlValidator = new UrlValidator(new String[]{"http", "https"});
                if(!urlValidator.isValid(param)) {
                    System.out.println("Bad url format");
                    continue;
                }

                WebScanningJob job = new WebScanningJob(param, hopCount);
                scanningJobs.add(job);
                continue;
            }


            if(command.equals("get")) {
                if( !param.startsWith("file|") && !param.startsWith("web|")) {
                    System.out.println("Supported formats: file|/corpus_name/, file|summary, web|/corpus_name/, web|summary");
                    continue;
                }

                if(param.endsWith("|summary")) {
                    Map<String, Map<String, Integer>> result = (param.startsWith("file|")) ?
                                                                resultRetriever.getFileScanResultSummary() :
                                                                resultRetriever.getWebScanResultSummary();
                    if(result != null) {
                        result.forEach((key, value) -> {
                            System.out.println(key + ": " + value);
                        });
                    }
                    continue;
                }

                String corpusName = param.split("\\|")[1];

                if(param.startsWith("file|")) {
                    corpusName = "corpus_" + corpusName;
                    Map<String, Integer> result = resultRetriever.getFileScanResult(corpusName);
                    if(result != null) {
                        System.out.println(result);
                    }
                    continue;
                }

                if(param.startsWith("web|")) {
                    if(corpusName.contains("\\") || corpusName.contains("/") || !corpusName.contains(".")) {
                        System.out.println("Non valid web domain");
                        continue;
                    }
                    Map<String, Integer> result = resultRetriever.getWebScanResult(corpusName);
                    if(result != null) {
                        System.out.println(result);
                    }
                    continue;
                }
            }

            if(command.equals("query")) {
                if( !param.startsWith("file|") && !param.startsWith("web|")) {
                    System.out.println("Supported formats: file|corpus_name, web|corpus_name");
                    continue;
                }

                if(param.endsWith("|summary")) {
                    Map<String, Map<String, Integer>> result = param.startsWith("file|") ?
                                                                resultRetriever.queryFileScanResultSummary() :
                                                                resultRetriever.queryWebScanResultSummary();
                    if(result != null) {
                        result.forEach((key, value) -> {
                            System.out.println(key + ": " + value);
                        });
                    }
                    continue;
                }

                String corpusName = param.split("\\|")[1];

                if(param.startsWith("file|")) {
                    corpusName = "corpus_" + corpusName;
                    Map<String, Integer> result = resultRetriever.queryFileScanResult(corpusName);
                    if(result != null) {
                        System.out.println(result);
                    }
                    continue;
                }
                if(param.startsWith("web|")) {
                    if(corpusName.contains("\\") || corpusName.contains("/") || !corpusName.contains(".")) {
                        System.out.println("Non valid web domain");
                        continue;
                    }
                    Map<String, Integer> result = resultRetriever.queryWebScanResult(corpusName);
                    if(result != null) {
                        System.out.println(result);
                    }
                }
            }
        }
        sc.close();

        System.out.println("Main shutting down");
        System.out.println("Waiting for thread pools to finish");
    }

    private static void loadProperties() {
        FileReader fr = null;
        try {
            fr = new FileReader(PROPERTIES_FILENAME);
            properties.load(fr);
            hopCount = Integer.parseInt(String.valueOf(properties.get("hop_count")));

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
