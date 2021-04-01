package result_retriever;

import job_dispatcher.ResultJob;
import job_dispatcher.ScanningJobType;
import main.Stoppable;

import java.util.HashMap;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;

public class ResultRetriever implements Runnable, Stoppable {

    private final ConcurrentLinkedQueue<ResultJob> resultJobs;
    private final ExecutorService threadPool;
    private final ExecutorCompletionService<Map<String, Integer>> completionService;
    private final int urlRefreshTime;

    private volatile boolean forever = true;
    private final Map<String, Map<String, Integer>> cookedOccurrences;

    public ResultRetriever(ConcurrentLinkedQueue<ResultJob> resultJobs,
                           ExecutorService threadPool,
                           ExecutorCompletionService<Map<String, Integer>> completionService,
                           int urlRefreshTime) {
        this.resultJobs = resultJobs;
        this.threadPool = threadPool;
        this.completionService = completionService;
        this.urlRefreshTime = urlRefreshTime;

        this.cookedOccurrences = new HashMap<>();
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

//                System.out.println("Before getResult(): " + this.cookedOccurrences);

                if(resultJob.getType() == ScanningJobType.FILE_SCANNING_JOB) {
//                    resultJob.getResult().forEach(this.occurrences::put);
                    this.cookedOccurrences.put(resultJob.getCorpusName(), resultJob.getResult());
                }

//                System.out.println("After getResult(): " + this.cookedOccurrences);

            }
        }
        System.out.println("Result retriever shutting down");
    }

    public Map<String, Integer> getResult(String corpusName, ScanningJobType corpusType) {
        for(String s: this.cookedOccurrences.keySet()) {
            System.out.println();
            System.out.println(s);
            System.out.println();
        }
        return this.cookedOccurrences.get(corpusName);
    }

    public List<Map<String, Integer>> queryResult(String corpusName, ScanningJobType corpusType, boolean summary) {
        return null;
    }

//    public List<Map<String, Integer>> getResult(String corpusName, ScanningJobType corpusType, boolean summary) {
//        List<Map<String, Integer>> returnResult = new ArrayList<>();
//
//        if(summary) {
//            return null;
//        }
//
//        System.out.println("Corpus name: " + corpusName);
//        for(Map.Entry<String, Map<String, Integer>> entry : this.occurrences.entrySet()) {
//            System.out.println(entry.getKey());
//            System.out.println(entry.getKey().endsWith(corpusName));
//            if(entry.getKey().endsWith(corpusName)) {
//                returnResult.add(new HashMap<>(this.occurrences.get(entry.getKey())));
//            }
//        }
//
//        return returnResult;
//    }

    @Override
    public synchronized void stop() {
        this.forever = false;
        this.threadPool.shutdown();
        this.resultJobs.add(new ResultJob());
    }
}
