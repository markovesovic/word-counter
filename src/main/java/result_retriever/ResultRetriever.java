package result_retriever;

import jobs.FileScanningResultJob;
import jobs.Job;
import jobs.WebScanningResultJob;
import main.Stoppable;

import java.util.*;
import java.util.concurrent.*;

import static java.util.concurrent.TimeUnit.SECONDS;

public class ResultRetriever implements Runnable, Stoppable {

    private volatile boolean forever = true;

    private final int urlRefreshTime;

    private final BlockingQueue<Job> resultJobs;
    private final ExecutorService threadPool;
    private final ExecutorCompletionService<Map<String, Integer>> completionService;

    private final Map<String, Object> watchedUrls;
    private final Map<String, Object> availableDomains;

    private final Map<String, FileScanningResultJob> pendingFileScanningResultJobs;
    private final Map<String, WebScanningResultJob> pendingWebScanningResultJobs;
    private final Map<String, Future<Map<String, Integer>>> cachedDomains;

    private final Map<String, Map<String, Integer>> fileScanResultSummary;
    private final Map<String, Future<Map<String, Integer>>> webScanResultSummary;

    private final ScheduledExecutorService cron;

    public ResultRetriever(BlockingQueue<Job> resultJobs,
                           Map<String, Object> watchedUrls,
                           Map<String, Object> availableDomains,
                           int urlRefreshTime) {

        this.resultJobs = resultJobs;
        this.threadPool = Executors.newCachedThreadPool();
        this.completionService = new ExecutorCompletionService<>(this.threadPool);
        this.urlRefreshTime = urlRefreshTime;

        this.watchedUrls = watchedUrls;
        this.availableDomains =availableDomains;

        this.pendingFileScanningResultJobs = new HashMap<>();
        this.pendingWebScanningResultJobs = new HashMap<>();
        this.cachedDomains = new HashMap<>();

        this.fileScanResultSummary = new HashMap<>();
        this.webScanResultSummary = new HashMap<>();

        cron = Executors.newScheduledThreadPool(1);
        startCron();
    }


    @Override
    public void run() {

        while(this.forever) {

            while(!this.resultJobs.isEmpty()) {

                Job resultJob = null;
                try {
                    resultJob = this.resultJobs.take();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if(resultJob == null) {
                    continue;
                }

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

    public Map<String, Map<String, Integer>> getFileScanResultSummary() {

        if(!this.fileScanResultSummary.isEmpty()) {
            return this.fileScanResultSummary;
        }

        Map<String, Map<String, Integer>> result = new HashMap<>();

        this.pendingFileScanningResultJobs.forEach((key, value) -> result.put(key, value.getResult()));
        this.fileScanResultSummary.putAll(result);

        return result;
    }

    public Map<String, Map<String, Integer>> queryFileScanResultSummary() {
        if(!this.fileScanResultSummary.isEmpty()) {
            return this.fileScanResultSummary;
        }
        boolean isDone = true;
        for(FileScanningResultJob job : this.pendingFileScanningResultJobs.values()) {
            if(!job.isReady()) {
                isDone = false;
            }
        }
        if(!isDone) {
            System.out.println("Summary is not ready yet");
            return null;
        }

        Map<String, Map<String, Integer>> result = new HashMap<>();
        this.pendingFileScanningResultJobs.forEach((key, value) -> result.put(key, value.getResult()));
        this.fileScanResultSummary.putAll(result);

        return result;
    }

    public Map<String, Integer> getWebScanResult(String domain) {

        if(this.cachedDomains.containsKey(domain)) {
            try {
                return this.cachedDomains.get(domain).get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        if(this.startWebDomainJob(domain)) {
            return null;
        }
        return this.getWebScanResult(domain);
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

        if(this.startWebDomainJob(domain)) {
            return null;
        }
        return this.queryWebScanResult(domain);
    }

    public boolean startWebDomainJob(String domain) {
        List<WebScanningResultJob> results = new ArrayList<>();

        this.pendingWebScanningResultJobs.forEach((url, job) -> {
            String urlDomain = url.split("/")[2];
            if(urlDomain.equals(domain) || urlDomain.equals("www." + domain)) {
                results.add(job);
            }
        });

        if(results.isEmpty()) {
            System.out.println("There are no pages for given domain");
            return true;
        }

        Future<Map<String, Integer>> future = this.completionService.submit(new DomainMergerWorker(results));
        this.cachedDomains.put(domain, future);
        return false;
    }

    public Map<String, Map<String, Integer>> getWebScanResultSummary() {
        Map<String, Map<String, Integer>> results = new HashMap<>();

        if(!this.webScanResultSummary.isEmpty()) {
            for(String key : this.webScanResultSummary.keySet()) {
                try {
                    results.put(key, this.webScanResultSummary.get(key).get());
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }
            return results;
        }

        startWebSummaryJob();

        return this.getWebScanResultSummary();
    }

    public Map<String, Map<String, Integer>> queryWebScanResultSummary() {

        Map<String, Map<String, Integer>> results = new HashMap<>();

        if(!this.webScanResultSummary.isEmpty()) {
            boolean isDone = true;
            for(Future<Map<String, Integer>> action : this.webScanResultSummary.values()) {
                if(!action.isDone()) {
                    isDone = false;
                }
            }
            if(isDone) {
                for(String key : this.webScanResultSummary.keySet()) {
                    try {
                        results.put(key, this.webScanResultSummary.get(key).get());
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }
                }
                return results;
            }
            System.out.println("Results are not ready yet");
            return null;
        }

        startWebSummaryJob();

        return this.queryWebScanResultSummary();
    }

    private void startWebSummaryJob() {
        Map<String, List<WebScanningResultJob>> data = new HashMap<>();

        this.availableDomains.forEach((domain, o) -> data.put(domain, new ArrayList<>()));

        this.pendingWebScanningResultJobs.forEach((url, job) -> {
            String matchingDomain = "";
            String urlDomain = url.split("/")[2];
            for (String domainName : this.availableDomains.keySet()) {
                if (urlDomain.equals(domainName) || urlDomain.equals("www." + domainName)) {
                    matchingDomain = domainName;
                    break;
                }
            }
            if(!matchingDomain.equals("")) {
                data.get(matchingDomain).add(job);
            }
        });

        this.availableDomains.forEach((domain, o) -> {
            Future<Map<String, Integer>> result = this.completionService.submit(new DomainMergerWorker(data.get(domain)));
            this.webScanResultSummary.put(domain, result);
        });
    }

    public void Test() {
        this.cachedDomains.forEach((key, value) -> {
            System.out.println(key);
            try {
                System.out.println(value.get());
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
            System.out.println();
        });
    }

    public void domains() {
        this.availableDomains.forEach((key, value) -> System.out.println(key));
    }

    public void allWebResults() {
        this.pendingWebScanningResultJobs.forEach((key, value) -> System.out.println(key + ": " + value.getResult()));
    }

    public void clearFileSummary() {
        this.fileScanResultSummary.clear();
    }

    public void clearWebSummary() {
        this.webScanResultSummary.clear();
    }

    private void startCron() {
        final Runnable cronJob = this.watchedUrls::clear;
        this.cron.scheduleAtFixedRate(cronJob, 0, this.urlRefreshTime, SECONDS);
    }

    @Override
    public void stop() {
        this.forever = false;
        this.threadPool.shutdown();
        this.cron.shutdownNow();
        this.resultJobs.add(new Job(true));
    }


}
