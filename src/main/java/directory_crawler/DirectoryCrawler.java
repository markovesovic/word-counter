package directory_crawler;

import job_dispatcher.FileScanningJob;
import job_dispatcher.ScanningJob;
import main.Stoppable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class DirectoryCrawler implements Stoppable, Runnable {

    private final HashMap<String, Long> lastModifiedValueForFiles = new HashMap<>();
    private boolean forever = true;

    private final ConcurrentLinkedQueue<CrawlerJob> directoryNames;
    private final ConcurrentLinkedQueue<ScanningJob> scanningJobs;
    private final String fileCorpusPrefix;
    private final int dirCrawlerSleepTime;

    public DirectoryCrawler(ConcurrentLinkedQueue<CrawlerJob> directoryNames,
                            ConcurrentLinkedQueue<ScanningJob> scanningJobs,
                            String fileCorpusPrefix,
                            int dirCrawlerSleepTime) {
        this.directoryNames = directoryNames;
        this.scanningJobs = scanningJobs;
        this.fileCorpusPrefix = fileCorpusPrefix;
        this.dirCrawlerSleepTime = dirCrawlerSleepTime;
    }

    @Override
    public void run() {
        while(this.forever) {

            while(!this.directoryNames.isEmpty()) {

                // Get directory name
                CrawlerJob crawlerJob = this.directoryNames.poll();
                assert crawlerJob != null;
                String directoryName = crawlerJob.getDirectoryName();

                if(crawlerJob.isPoisonous()) {
                    break;
                }

                // Search for all suitable subdirectories
                this.searchDir(directoryName);
                // Add this directory to be tracked forever for changes
                this.directoryNames.add(crawlerJob);

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

//                System.out.println(f.getAbsolutePath() + "   new: " + newLastModifiedTime + ", old: " + oldLastModifiedTime);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if(countOfSameFiles == dirFiles.length) {
            isMatch = false;
        }

        // Add new job with current dir
        if(directory.getName().startsWith(this.fileCorpusPrefix) && isCorpus && isMatch) {
            ScanningJob scanningJob = new ScanningJob(directoryName);
            this.scanningJobs.add(scanningJob);

//            System.out.println("Adding dir: " + directory.getAbsolutePath());
            return;
        }

        for(File f : dirFiles) {
            if(f.isDirectory()) {
                searchDir(f.getPath());
            }
        }
    }

    @Override
    public synchronized void stop() {
        notifyAll();
        this.forever = false;
        this.directoryNames.add(new CrawlerJob());
    }
}
