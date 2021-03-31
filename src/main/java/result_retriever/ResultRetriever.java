package result_retriever;

import job_dispatcher.ResultJob;
import job_dispatcher.ScanningJobType;
import main.Stoppable;

import java.util.HashMap;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ResultRetriever implements Runnable, Stoppable {

    private final ConcurrentLinkedQueue<ResultJob> resultJobs;
    private final ExecutorService threadPool;
    private final ExecutorCompletionService<Map<String, Integer>> completionService;
    private final int urlRefreshTime;

    private volatile boolean forever = true;
    private final Map<String, Map<String, Integer>> occurrences;

    public ResultRetriever(ConcurrentLinkedQueue<ResultJob> resultJobs,
                           ExecutorService threadPool,
                           ExecutorCompletionService<Map<String, Integer>> completionService,
                           int urlRefreshTime) {
        this.resultJobs = resultJobs;
        this.threadPool = threadPool;
        this.completionService = completionService;
        this.urlRefreshTime = urlRefreshTime;

        this.occurrences = new ConcurrentHashMap<>();
    }

    @Override
    public void run() {

        while(this.forever) {
            while(!this.resultJobs.isEmpty()) {

                ResultJob resultJob = this.resultJobs.poll();

//                System.out.println(resultJob.getResult());
                if(resultJob.isPoisonous()) {
                    System.out.println("Result retriever waiting for threadpool");
                    this.threadPool.shutdown();
                    while(!this.threadPool.isShutdown()) {

                    }
                    break;
                }

                if(resultJob.getType() == ScanningJobType.FILE_SCANNING_JOB) {
                    resultJob.getResult().forEach(this.occurrences::put);
                }

            }
        }
        System.out.println("Result retriever shutting down");
    }

    public List<Map<String, Integer>> getResult(String corpusName, ScanningJobType corpusType, boolean summary) {
        List<Map<String, Integer>> returnResult = new ArrayList<>();

        if(summary) {
            return null;
        }

        System.out.println("Corpus name: " + corpusName);
        for(Map.Entry<String, Map<String, Integer>> entry : this.occurrences.entrySet()) {
            System.out.println(entry.getKey());
            System.out.println(entry.getKey().endsWith(corpusName));
            if(entry.getKey().endsWith(corpusName)) {
                returnResult.add(new HashMap<>(this.occurrences.get(entry.getKey())));
            }
        }

        return returnResult;
    }

    @Override
    public synchronized void stop() {
        this.forever = false;
        this.threadPool.shutdown();
        this.resultJobs.add(new ResultJob());
    }
}
