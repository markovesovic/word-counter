package result_retriever;

import jobs.FileScanningResultJob;
import jobs.Job;
import jobs.WebScanningResultJob;
import main.Stoppable;
import org.apache.commons.validator.routines.UrlValidator;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;

import static java.util.concurrent.TimeUnit.SECONDS;

public class ResultRetriever implements Runnable, Stoppable {

    private volatile boolean forever = true;

    private final int urlRefreshTime;

    private final ConcurrentLinkedQueue<Job> resultJobs;
    private final ExecutorService threadPool;
    private final ExecutorCompletionService<Map<String, Integer>> completionService;

    private final Map<String, Object> watchedDirectories;
    private final Map<String, Object> watchedUrls;

    private final Map<String, FileScanningResultJob> pendingFileScanningResultJobs;
    private final Map<String, WebScanningResultJob> pendingWebScanningResultJobs;
    private final Map<String, Future<Map<String, Integer>>> cachedDomains;
//    private final Map<String, Map<String, Integer>> fileScanningResults;
//    private final Map<String, Map<String, Integer>> webScanningResults;


    private final Map<String, Map<String, Integer>> cookedOccurrences;
    private final Map<String, Map<String, Integer>> cachedWebOccurrences;
    private final Map<String, Integer> webSummary;
    private final Map<String, Integer> fileSummary;

    private final ScheduledExecutorService cron;

    public ResultRetriever(ConcurrentLinkedQueue<Job> resultJobs,
                           ExecutorService threadPool,
                           ExecutorCompletionService<Map<String, Integer>> completionService,
                           Map<String, Object> watchedDirectories,
                           Map<String, Object> watchedUrls,
                           int urlRefreshTime) {

        this.resultJobs = resultJobs;
        this.threadPool = threadPool;
        this.completionService = completionService;
        this.watchedDirectories = watchedDirectories;
        this.watchedUrls = watchedUrls;
        this.urlRefreshTime = urlRefreshTime;

        this.pendingFileScanningResultJobs = new HashMap<>();
        this.pendingWebScanningResultJobs = new HashMap<>();
        this.cachedDomains = new HashMap<>();
//        this.fileScanningResults = new HashMap<>();
//        this.webScanningResults = new HashMap<>();

        this.cookedOccurrences = new ConcurrentHashMap<>();
        this.cachedWebOccurrences = new ConcurrentHashMap<>();
        this.webSummary = new ConcurrentHashMap<>();
        this.fileSummary = new ConcurrentHashMap<>();
        cron = Executors.newScheduledThreadPool(1);
        startCron();
    }


    @Override
    public void run() {

        while(this.forever) {

            while(!this.resultJobs.isEmpty()) {

                Job resultJob = this.resultJobs.poll();

                if(resultJob.isPoisonous()) {
                    break;
                }

                if(resultJob instanceof FileScanningResultJob) {
                    this.pendingFileScanningResultJobs.put(((FileScanningResultJob) resultJob).getCorpusName(), (FileScanningResultJob) resultJob);
                }
                if(resultJob instanceof WebScanningResultJob) {
                    this.pendingWebScanningResultJobs.put(((WebScanningResultJob) resultJob).getWebUrl(), (WebScanningResultJob) resultJob);
                }

            }
        }
        System.out.println("Result retriever shutting down");
    }

    public Map<String, Integer> getFileScanResult(String corpusName) {
        FileScanningResultJob resultJob = this.pendingFileScanningResultJobs.get(corpusName);
        if(resultJob == null) {
            System.out.println("Given corpus was never added!");
            return null;
        }
        return new HashMap<>(resultJob.getResult());

    }

    public Map<String, Integer> queryFileScanResult(String corpusName) {
        FileScanningResultJob resultJob = this.pendingFileScanningResultJobs.get(corpusName);
        if(resultJob == null) {
            System.out.println("Given corpus was never added!");
            return null;
        }
        Map<String, Integer> resultMap = resultJob.queryResult();
        if(resultMap == null) {
            System.out.println("Result is not ready yet!");
            return null;
        }
        return resultMap;
    }

    public Map<String, Integer> getWebScanResult(String domain) {
        if(this.cachedDomains.containsKey(domain)) {
            try {
                return this.cachedDomains.get(domain).get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        List<WebScanningResultJob> results = new ArrayList<>();
        this.pendingWebScanningResultJobs.forEach((key, value) -> {
            if(key.contains(domain)) {
                results.add(value);
            }
        });
        Future<Map<String, Integer>> future = this.completionService.submit(new DomainMergerWorker(results));
        this.cachedDomains.put(domain, future);
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Map<String, Integer> queryWebScanResult(String domain) {
        if(this.cachedDomains.containsKey(domain)) {
            Future<Map<String, Integer>> result = this.cachedDomains.get(domain);
            if(result.isDone()) {
                try {
                    return result.get();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }
            System.out.println("Result is not ready yet");
            return null;
        }
        List<WebScanningResultJob> results = new ArrayList<>();
        this.pendingWebScanningResultJobs.forEach((key, value) -> {
            if(key.contains(domain)) {
                results.add(value);
            }
        });
        Future<Map<String, Integer>> future = this.completionService.submit(new DomainMergerWorker(results));
        this.cachedDomains.put(domain, future);

        System.out.println("Result is not yet ready");

        return null;
    }


//    public Map<String, Integer> getFileScanResult(String corpusName) {
//
//        if (!this.watchedDirectories.containsKey(corpusName)) {
//            System.out.println("Given corpus was never added!");
//            return null;
//        }
//
//        while(!this.cookedOccurrences.containsKey(corpusName)) {
//            try {
//                Thread.sleep(100);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
//        return this.cookedOccurrences.get(corpusName);
//
//    }
//
//    public Map<String, Integer> queryFileScanResult(String corpusName) {
//
//        if(!this.watchedDirectories.containsKey(corpusName)) {
//            System.out.println("Given corpus was never added!");
//            return null;
//        }
//        if(this.cookedOccurrences.containsKey(corpusName)) {
//            return this.cookedOccurrences.get(corpusName);
//        }
//        System.out.println("Result is not yet ready");
//        return null;
//    }
//
//    public Map<String, Integer> getWebScanResult(String corpusName) {
//        boolean exists = doesWebCorpusExist(corpusName);
//        if(!exists) {
//            return null;
//        }
//
//        if(this.cachedWebOccurrences.containsKey(corpusName)) {
//            System.out.println("Cached data");
//            return this.cachedWebOccurrences.get(corpusName);
//        }
//
//        List<Map<String, Integer>> jobs = new ArrayList<>();
//        this.cookedOccurrences.forEach((key, value) -> {
//            if(key.contains(corpusName)) {
//                jobs.add(value);
//            }
//        });
//        try {
//            System.out.println("Calculating all results");
//            Map<String, Integer> result = this.completionService.submit(new DomainMergerWorker(jobs)).get();
//            this.cachedWebOccurrences.put(corpusName, result);
//            return result;
//        } catch (InterruptedException | ExecutionException e) {
//            e.printStackTrace();
//        }
//        return null;
//
//    }
//
//    public Map<String, Integer> queryWebScanResult(String corpusName) {
//        boolean exists = doesWebCorpusExist(corpusName);
//        if(!exists) {
//            return null;
//        }
//        return new HashMap<>();
//    }
//
//    public Map<String, Integer> getFileScanResultSummary() {
//        if(!this.fileSummary.isEmpty()) {
//            System.out.println("Cached");
//            return this.fileSummary;
//        }
//
//        this.cookedOccurrences.forEach((key, value) -> {
//            UrlValidator urlValidator = new UrlValidator(new String[]{"http", "https"});
//            if (!urlValidator.isValid(key)) {
//                value.forEach((localKey, localValue) -> fileSummary.merge(localKey, localValue, Integer::sum));
//            }
//        });
//
//        return this.fileSummary;
//    }
//
//    public Map<String, Integer> getWebScanResultSummary() {
//        if(!this.webSummary.isEmpty()) {
//            return this.webSummary;
//        }
//        this.cookedOccurrences.forEach((key, value) -> {
//            UrlValidator urlValidator = new UrlValidator(new String[]{"http", "https"});
//            if(urlValidator.isValid(key)) {
//                value.forEach((localKey, localValue) -> webSummary.merge(localKey, localValue, Integer::sum));
//            }
//        });
//
//        return this.webSummary;
//    }
//
//
//
//    private boolean doesWebCorpusExist(String corpusName) {
//        boolean exists = false;
//        for(String key : this.watchedUrls.keySet()) {
//            if (key.contains(corpusName)) {
//                exists = true;
//                break;
//            }
//        }
//        if(!exists) {
//            System.out.println("No corpus under given domain");
//            return  false;
//        }
//        return true;
//    }
//
//
//    public void Test() {
//        if(this.cookedOccurrences.isEmpty()) {
//            System.out.println("empty");
//            return;
//        }
//        this.cookedOccurrences.forEach((key, value) -> {
//            System.out.println(key + ": " + value);
//        });
//    }


//    public void clearFileSummary() {
//        this.fileSummary.clear();
//    }
//
//    public void clearWebSummary() {
//        this.webSummary.clear();
//    }

    private void startCron() {
        final Runnable cronJob = this.watchedUrls::clear;
        final ScheduledFuture<?> cronHandle = this.cron.scheduleAtFixedRate(cronJob, 0, this.urlRefreshTime, SECONDS);
    }

    @Override
    public void stop() {
        this.forever = false;
        this.threadPool.shutdown();
        this.cron.shutdownNow();
        this.resultJobs.add(new Job(true));
    }
}
