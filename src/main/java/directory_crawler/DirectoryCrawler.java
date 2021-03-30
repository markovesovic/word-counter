package directory_crawler;

import main.Stoppable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class DirectoryCrawler implements Stoppable, Runnable {

    private final ConcurrentLinkedQueue<CrawlerJob> directoryNames;
    private final String fileCorpusPrefix;
    private final int dirCrawlerSleepTime;

    private final HashMap<String, Long> lastModifiedValueForFiles = new HashMap<>();
    private boolean forever = true;

    public DirectoryCrawler(ConcurrentLinkedQueue<CrawlerJob> directoryNames, String fileCorpusPrefix, int dirCrawlerSleepTime) {
        this.directoryNames = directoryNames;
        this.fileCorpusPrefix = fileCorpusPrefix;
        this.dirCrawlerSleepTime = dirCrawlerSleepTime;
    }

    @Override
    public void run() {
        while(this.forever) {

            while(!this.directoryNames.isEmpty()) {
                // Get directory name
                CrawlerJob crawlerJob = this.directoryNames.poll();
                String directoryName = crawlerJob.getDirectoryName();

                if(crawlerJob.isPoison()) {
                    this.forever = false;
                    break;
                }

                // Search for all suitable subdirectories
                this.searchDir(directoryName);
                // Add this directory to be tracked forever for changes
                this.directoryNames.add(crawlerJob);

                // Sleep for fixed time
                try {
                    System.out.println("Directory crawler asleep for " + this.dirCrawlerSleepTime);
                    synchronized (this) {
                        wait(this.dirCrawlerSleepTime);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
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
            System.out.println("Adding dir: " + directory.getAbsolutePath());
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
        this.directoryNames.add(new CrawlerJob());
    }
}
