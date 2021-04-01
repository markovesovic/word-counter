package result_retriever;

import job_dispatcher.ResultJob;
import job_dispatcher.ScanningJobType;
import main.Stoppable;
import org.apache.commons.validator.routines.UrlValidator;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;

public class ResultRetriever implements Runnable, Stoppable {

    private final ConcurrentLinkedQueue<ResultJob> resultJobs;
    private final ExecutorService threadPool;
    private final ExecutorCompletionService<Map<String, Integer>> completionService;
    private final Map<String, Object> watchedDirectories;
    private final int urlRefreshTime;

    private volatile boolean forever = true;
    private final Map<String, Map<String, Integer>> cookedOccurrences;

    public ResultRetriever(ConcurrentLinkedQueue<ResultJob> resultJobs,
                           ExecutorService threadPool,
                           ExecutorCompletionService<Map<String, Integer>> completionService,
                           Map<String, Object> watchedDirectories,
                           int urlRefreshTime) {
        this.resultJobs = resultJobs;
        this.threadPool = threadPool;
        this.completionService = completionService;
        this.watchedDirectories = watchedDirectories;
        this.urlRefreshTime = urlRefreshTime;

        this.cookedOccurrences = new ConcurrentHashMap<>();
        this.cookedOccurrences.put("https://google.com", new HashMap<>());
    }

    @Override
    public void run() {

        while(this.forever) {
            while(!this.resultJobs.isEmpty()) {

                ResultJob resultJob = this.resultJobs.poll();

                System.out.println("Result retriever - result job received: " + resultJob.getCorpusName());

                if(resultJob.isPoisonous()) {
//                    System.out.println("Result retriever waiting for thread pool");
//                    this.threadPool.shutdown();
//                    while(!this.threadPool.isShutdown()) {
//
//                    }
                    break;
                }

                if(resultJob.getType() == ScanningJobType.FILE_SCANNING_JOB) {
                    this.cookedOccurrences.put(resultJob.getCorpusName(), resultJob.getResult());
                }
            }
        }
        System.out.println("Result retriever shutting down");
    }

    public Map<String, Integer> getResult(String corpusName, ScanningJobType corpusType) {

        if(corpusType == ScanningJobType.FILE_SCANNING_JOB) {

            if (!this.watchedDirectories.containsKey(corpusName)) {
                System.out.println("Trazeni korpus nije dodat za obradu!");
                return null;
            }
            return this.cookedOccurrences.get(corpusName);

        }

        return null;
    }

    public Map<String, Map<String, Integer>> getResultSummary(ScanningJobType corpusType) {

        final boolean isFileScanningJob = corpusType == ScanningJobType.FILE_SCANNING_JOB;

        Map<String, Map<String, Integer>> result = new HashMap<>();

        this.cookedOccurrences.forEach((key, value) -> {
            UrlValidator urlValidator = new UrlValidator(new String[]{"http", "https"});
            if(urlValidator.isValid(key) != isFileScanningJob) {
                result.put(key, value);
            }
        });
        return result;
    }

    public List<Map<String, Integer>> queryResult(String corpusName, ScanningJobType corpusType, boolean summary) {
        return null;
    }

    @Override
    public synchronized void stop() {
        this.forever = false;
        this.threadPool.shutdown();
        this.resultJobs.add(new ResultJob());
    }
}
