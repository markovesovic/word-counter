package scanner.file;

import job_dispatcher.FileScanningJob;
import job_dispatcher.ResultJob;
import job_dispatcher.ScanningJob;
import job_dispatcher.ScanningJobType;
import main.Stoppable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class FileScanner implements Runnable, Stoppable {

    private final BlockingQueue<ScanningJob> fileScanningJobs;
    private final ConcurrentLinkedQueue<ResultJob> resultJobs;
    private final ExecutorService threadPool;
    private final ExecutorCompletionService<Map<String, Integer>> completionService;
    private final int fileScanningJobLimit;
    private final List<String> keywords;

    private volatile boolean forever = true;

    public FileScanner(BlockingQueue<ScanningJob> fileScanningJobs,
                       ConcurrentLinkedQueue<ResultJob> resultJobs,
                       ExecutorService threadPool,
                       ExecutorCompletionService<Map<String, Integer>> completionService,
                       int fileScanningJobLimit,
                       List<String> keywords) {
        this.fileScanningJobs = fileScanningJobs;
        this.resultJobs = resultJobs;
        this.threadPool = threadPool;
        this.completionService = completionService;
        this.fileScanningJobLimit = fileScanningJobLimit;
        this.keywords = keywords;
    }

    @Override
    public void run() {

        while(this.forever) {

            while(!this.fileScanningJobs.isEmpty()) {
                ScanningJob scanningJob = this.fileScanningJobs.poll();

                System.out.println("File scanner - file scanning job received " + scanningJob.getPath());

                if(scanningJob.isPoisonous()) {
//                    this.threadPool.shutdown();
//                    System.out.println("File scanner waiting for threadPool");
//                    while (!this.threadPool.isShutdown()) {
//
//                    }
                    break;
                }

                String directoryName = scanningJob.getPath();

                // Divide work between file scanning workers
                List<FileScanningJob> jobs = divideWork(directoryName);

                // Starting jobs in completionService
                for(FileScanningJob job : jobs) {
                    this.completionService.submit(new FileScannerWorker(job, keywords));
                }

                List<Future<Map<String, Integer>>> occurrences = new ArrayList<>();

                for(int i = 0; i< jobs.size(); i++) {
                    try {
                        Future<Map<String, Integer>> localOccurrences = this.completionService.take();
                        occurrences.add(localOccurrences);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                ResultJob resultJob = new ResultJob(ScanningJobType.FILE_SCANNING_JOB, directoryName, occurrences);
                this.resultJobs.add(resultJob);


                // Waiting and reducing results for given corpus

                // Blocking call
                /*
                Map<String, Integer> occurrences = new HashMap<>();
                for(int i = 0; i < jobs.size(); i++) {
                    try {
                        Map<String, Integer> localOccurrences = this.completionService.take().get();
                        localOccurrences.forEach((key, value) -> occurrences.merge(key, value, Integer::sum));
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }
                }
                Map<String, Map<String, Integer>> resultMap = new HashMap<>();
                resultMap.put(directoryName, occurrences);
                ResultJob resultJob = new ResultJob(ScanningJobType.FILE_SCANNING_JOB, resultMap);
                this.resultJobs.add(resultJob);
                */
            }
        }
        System.out.println("File scanner shutting down");
    }

    private List<FileScanningJob> divideWork(String directoryName) {
        List<FileScanningJob> jobs = new ArrayList<>();
        List<File> files = new ArrayList<>();
        int currentSize = 0;

        File[] childrenFiles = new File(directoryName).listFiles();
        assert childrenFiles != null;

        for(File f : childrenFiles) {
            if(currentSize < this.fileScanningJobLimit) {
                files.add(f);
                currentSize += f.length();
                continue;
            }
            jobs.add(new FileScanningJob(new ArrayList<>(files)));
            files.clear();
            files.add(f);
            currentSize = (int) f.length();
        }
        jobs.add(new FileScanningJob(new ArrayList<>(files)));
        return jobs;
    }

    @Override
    public synchronized void stop() {
        this.forever = false;
        this.threadPool.shutdown();
        this.fileScanningJobs.add(new ScanningJob());
    }
}
