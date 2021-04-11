package scanner.file;

import jobs.FileScanningJob;
import jobs.FileScanningResultJob;
import jobs.Job;
import main.Stoppable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class FileScanner implements Runnable, Stoppable {

    private final BlockingQueue<FileScanningJob> fileScanningJobs;
    private final BlockingQueue<Job> resultJobs;
    private final ExecutorService threadPool;
    private final ExecutorCompletionService<Map<String, Integer>> completionService;
    private final int fileScanningJobLimit;
    private final List<String> keywords;

    private volatile boolean forever = true;

    public FileScanner(BlockingQueue<FileScanningJob> fileScanningJobs,
                       BlockingQueue<Job> resultJobs,
                       int fileScanningJobLimit,
                       List<String> keywords) {
        this.fileScanningJobs = fileScanningJobs;
        this.resultJobs = resultJobs;
        this.threadPool = Executors.newCachedThreadPool();
        this.completionService = new ExecutorCompletionService<>(this.threadPool);
        this.fileScanningJobLimit = fileScanningJobLimit;
        this.keywords = keywords;
    }

    @Override
    public void run() {

        while(this.forever) {

            while(!this.fileScanningJobs.isEmpty()) {
                FileScanningJob fileScanningJob = null;
                try {
                    fileScanningJob = this.fileScanningJobs.take();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if(fileScanningJob == null) {
                    continue;
                }

                if(fileScanningJob.isPoisonous()) {
                    this.threadPool.shutdownNow();
                    break;
                }

                String directoryName = fileScanningJob.getPath();

                List<List<File>> jobs = divideWork(directoryName);

                List<Future<Map<String, Integer>>> occurrences = new ArrayList<>();

                for(List<File> job : jobs) {
                    Future<Map<String, Integer>> res = this.completionService.submit(new FileScannerWorker(job, keywords));
                    occurrences.add(res);
                }

                directoryName = directoryName.replace("\\", "/");
                String corpusName = directoryName.split("/")[directoryName.split("/").length - 1];
                FileScanningResultJob resultJob = new FileScanningResultJob(occurrences, corpusName);
                this.resultJobs.add(resultJob);

            }
        }
        System.out.println("File scanner shutting down");
    }

    private List<List<File>> divideWork(String directoryName) {
        List<List<File>> jobs = new ArrayList<>();
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
            jobs.add(new ArrayList<>(files));
            files.clear();
            files.add(f);
            currentSize = (int) f.length();
        }
        jobs.add(new ArrayList<>(files));
        return jobs;
    }

    @Override
    public void stop() {
        this.forever = false;
        this.fileScanningJobs.add(new FileScanningJob());
    }
}
