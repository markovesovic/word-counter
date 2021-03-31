package job_dispatcher;

import main.Stoppable;
import scanner.file.FileScanningWorker;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;

public class JobDispatcher implements Runnable, Stoppable {

    private final ConcurrentLinkedQueue<ScanningJob> scanningJobs;
    private final ExecutorCompletionService<Map<String, Integer>> completionService;
    private final int fileScanningJobLimit;
    private final List<String> keywords;

    private volatile boolean forever = true;

    public JobDispatcher(ConcurrentLinkedQueue<ScanningJob> scanningJobs,
                         ExecutorCompletionService<Map<String, Integer>> completionService,
                         int fileScanningSizeLimit,
                         List<String> keywords) {
        this.scanningJobs = scanningJobs;
        this.completionService = completionService;
        this.fileScanningJobLimit = fileScanningSizeLimit;
        this.keywords = keywords;
    }

    @Override
    public void run() {
        while(this.forever) {

            while(!this.scanningJobs.isEmpty()) {
                ScanningJob scanningJob = this.scanningJobs.poll();

                if(scanningJob.isPoisonous()) {
                    break;
                }
                if(scanningJob.getType() == ScanningJobType.FILE_SCANNING_JOB) {
                    System.out.println("Scanning job received: " + scanningJob.getPath());

                    String directoryName = scanningJob.getPath();

                    // Divide work between file scanning workers
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
                        System.out.println("One job: " + files);
                        jobs.add(new FileScanningJob(new ArrayList<>(files)));
                        files.clear();
                        files.add(f);
                        currentSize = (int) f.length();
                    }
                    jobs.add(new FileScanningJob(new ArrayList<>(files)));

                    // Starting jobs in completionService
                    for(FileScanningJob job : jobs) {
                        this.completionService.submit(new FileScanningWorker(job, keywords));
                    }

                    // Waiting and reducing results for given corpus
                    Map<String, Integer> occurrences = new HashMap<>();
                    for(int i = 0; i < jobs.size(); i++) {
                        try {
                            Map<String, Integer> localOccurrences = this.completionService.take().get();
                            localOccurrences.forEach((key, value) -> occurrences.merge(key, value, Integer::sum));
                        } catch (InterruptedException | ExecutionException e) {
                            e.printStackTrace();
                        }
                    }

                }
                if(scanningJob.getType() == ScanningJobType.WEB_SCANNING_JOB) {
                    System.out.println("Web scanning job");
                }
            }

            try {
                synchronized (this) {
                    wait(1000);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public synchronized void stop() {
        this.forever = false;
        this.scanningJobs.add(new ScanningJob());
    }
}
