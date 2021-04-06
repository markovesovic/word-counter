package result_retriever;

import jobs.ResultJob;
import jobs.ScanningJobType;
import main.Stoppable;
import org.apache.commons.validator.routines.UrlValidator;
import scanner.file.FileScanner;

import java.util.*;
import java.util.concurrent.*;

import static java.util.concurrent.TimeUnit.SECONDS;

public class ResultRetriever implements Runnable, Stoppable {

    private final ConcurrentLinkedQueue<ResultJob> resultJobs;
    private final ExecutorService threadPool;
    private final ExecutorCompletionService<Map<String, Integer>> completionService;
    private final Map<String, Object> watchedDirectories;
    private final Map<String, Object> watchedUrls;
    private final int urlRefreshTime;

    private volatile boolean forever = true;
    private final Map<String, Map<String, Integer>> cookedOccurrences;
    private final Map<String, Map<String, Integer>> cachedWebOccurrences;
    private final Map<String, Integer> webSummary;
    private final Map<String, Integer> fileSummary;
    private final ScheduledExecutorService cron;

    public ResultRetriever(ConcurrentLinkedQueue<ResultJob> resultJobs,
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

                ResultJob resultJob = this.resultJobs.poll();

                if(resultJob.isPoisonous()) {
                    break;
                }
                Map<String, Integer> result = resultJob.getResult();
                if(result == null) {
                    System.out.println("Result is null" + " corpus: " + resultJob.getCorpusName());
                    continue;
                }

                this.cookedOccurrences.put(resultJob.getCorpusName(), resultJob.getResult());
            }
        }
        System.out.println("Result retriever shutting down");
    }


    public Map<String, Integer> getFileScanResult(String corpusName) {

        if (!this.watchedDirectories.containsKey(corpusName)) {
            System.out.println("Given corpus was never added!");
            return null;
        }

        while(!this.cookedOccurrences.containsKey(corpusName)) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return this.cookedOccurrences.get(corpusName);

    }

    public Map<String, Integer> queryFileScanResult(String corpusName) {

        if(!this.watchedDirectories.containsKey(corpusName)) {
            System.out.println("Given corpus was never added!");
            return null;
        }
        if(this.cookedOccurrences.containsKey(corpusName)) {
            return this.cookedOccurrences.get(corpusName);
        }
        System.out.println("Result is not yet ready");
        return null;
    }

    public Map<String, Integer> getWebScanResult(String corpusName) {
        boolean exists = doesWebCorpusExist(corpusName);
        if(!exists) {
            return null;
        }

        if(this.cachedWebOccurrences.containsKey(corpusName)) {
            System.out.println("Cached data");
            return this.cachedWebOccurrences.get(corpusName);
        }

        List<Map<String, Integer>> jobs = new ArrayList<>();
        this.cookedOccurrences.forEach((key, value) -> {
            if(key.contains(corpusName)) {
                jobs.add(value);
            }
        });
        try {
            System.out.println("Calculating all results");
            Map<String, Integer> result = this.completionService.submit(new DomainMergerWorker(jobs)).get();
            this.cachedWebOccurrences.put(corpusName, result);
            return result;
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return null;

    }

    public Map<String, Integer> queryWebScanResult(String corpusName) {
        boolean exists = doesWebCorpusExist(corpusName);
        if(!exists) {
            return null;
        }
        return new HashMap<>();
    }

    public Map<String, Integer> getFileScanResultSummary() {
        if(!this.fileSummary.isEmpty()) {
            return this.fileSummary;
        }

        this.cookedOccurrences.forEach((key, value) -> {
            UrlValidator urlValidator = new UrlValidator(new String[]{"http", "https"});
            if (!urlValidator.isValid(key)) {
                value.forEach((localKey, localValue) -> fileSummary.merge(localKey, localValue, Integer::sum));
            }
        });

        return this.fileSummary;
    }

    public Map<String, Integer> getWebScanResultSummary() {
        if(!this.webSummary.isEmpty()) {
            return this.webSummary;
        }
        this.cookedOccurrences.forEach((key, value) -> {
            UrlValidator urlValidator = new UrlValidator(new String[]{"http", "https"});
            if(!urlValidator.isValid(key)) {
                value.forEach((localKey, localValue) -> webSummary.merge(localKey, localValue, Integer::sum));
            }
        });

        return this.webSummary;
    }



    private boolean doesWebCorpusExist(String corpusName) {
        boolean exists = false;
        for(String key : this.watchedUrls.keySet()) {
            if (key.contains(corpusName)) {
                exists = true;
                break;
            }
        }
        if(!exists) {
            System.out.println("No corpus under given domain");
            return  false;
        }
        return true;
    }


    public void Test() {
        if(this.cookedOccurrences.isEmpty()) {
            System.out.println("empty");
            return;
        }
        this.cookedOccurrences.forEach((key, value) -> {
            System.out.println(key + ": " + value);
        });
    }

    private void startCron() {
        final Runnable cronJob = this.watchedUrls::clear;
        final ScheduledFuture<?> cronHandle = this.cron.scheduleAtFixedRate(cronJob, 0, this.urlRefreshTime, SECONDS);
    }

    public void clearFileSummary() {
        this.fileSummary.clear();
    }

    public void clearWebSummary() {
        this.webSummary.clear();
    }

    @Override
    public void stop() {
        this.forever = false;
        this.threadPool.shutdown();
        this.cron.shutdownNow();
        this.resultJobs.add(new ResultJob());
    }
}
