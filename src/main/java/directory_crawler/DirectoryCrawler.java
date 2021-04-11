package directory_crawler;

import jobs.CrawlerJob;
import jobs.FileScanningJob;
import jobs.Job;
import main.Stoppable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

public class DirectoryCrawler implements Stoppable, Runnable {

    private final BlockingQueue<CrawlerJob> crawlingJobs;
    private final BlockingQueue<Job> jobs;
    private final Map<String, Object> watchedDirectories;
    private final String fileCorpusPrefix;
    private final int dirCrawlerSleepTime;

    private final HashMap<String, Long> lastModifiedValueForFiles = new HashMap<>();
    private boolean forever = true;

    public DirectoryCrawler(BlockingQueue<CrawlerJob> crawlingJobs,
                            BlockingQueue<Job> jobs,
                            Map<String, Object> watchedDirectories, String fileCorpusPrefix,
                            int dirCrawlerSleepTime) {

        this.crawlingJobs = crawlingJobs;
        this.jobs = jobs;
        this.watchedDirectories = watchedDirectories;
        this.fileCorpusPrefix = fileCorpusPrefix;
        this.dirCrawlerSleepTime = dirCrawlerSleepTime;
    }

    @Override
    public void run() {
        while(this.forever) {

            while(!this.crawlingJobs.isEmpty()) {

                // Get directory name
                CrawlerJob crawlerJob = null;
                try {
                    crawlerJob = this.crawlingJobs.take();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if(crawlerJob != null) {
                    String directoryName = crawlerJob.getDirectoryName();

                    if(crawlerJob.isPoisonous()) {
                        break;
                    }

                    // Search for all suitable subdirectories
                    this.searchDir(directoryName);
                    // Add this directory to be tracked forever for changes
                    this.crawlingJobs.add(crawlerJob);
                }

                // Sleep for fixed time
                try {
                    synchronized (this) {
                        wait(this.dirCrawlerSleepTime);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        System.out.println("Directory crawler shutting down");
    }

    private void searchDir(String directoryName) {
        File directory = new File(directoryName);

        File[] dirFiles = directory.listFiles();
        if(dirFiles == null) {
            return;
        }

        // Check if corpus
        boolean isCorpus = true;
        for(File f : dirFiles) {
            if(f.isDirectory()) {
                isCorpus = false;
            }
        }

        // Check if change happened
        boolean isMatch = true;
        int countOfSameFiles = 0;
        for(File f : dirFiles) {
            if(f.isDirectory()) {
                continue;
            }
            try {
                Long newLastModifiedTime = Files.getLastModifiedTime(Path.of(f.getAbsolutePath())).toMillis();
                Long oldLastModifiedTime = this.lastModifiedValueForFiles.get(f.getAbsolutePath());

                this.lastModifiedValueForFiles.put(f.getAbsolutePath(), newLastModifiedTime);

                if(oldLastModifiedTime == null) {
                    continue;
                }

                if(newLastModifiedTime.equals(oldLastModifiedTime)) {
                    countOfSameFiles++;
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if(countOfSameFiles == dirFiles.length) {
            isMatch = false;
        }

        // Add new job with current dir
        if(directory.getName().startsWith(this.fileCorpusPrefix) && isCorpus && isMatch) {

            Job job = new FileScanningJob(directoryName);
            this.jobs.add(job);

            directoryName = directoryName.replace("\\", "/");
            String corpusName = directoryName.split("/")[directoryName.split("/").length - 1];
            this.watchedDirectories.put(corpusName, new Object());
            return;
        }

        for(File f : dirFiles) {
            if(f.isDirectory()) {
                searchDir(f.getPath());
            }
        }
    }

    @Override
    public void stop() {
        this.forever = false;
        this.crawlingJobs.add(new CrawlerJob());
    }
}
