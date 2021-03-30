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

    private final HashMap<Path, Integer> lastModifiedValueForFiles = new HashMap<>();
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

        boolean isCorpus = true;

        for(File f : dirFiles) {
            if(f.isDirectory()) {
                isCorpus = false;
            }
        }

        boolean isMatch = true;
        for(File f : dirFiles) {
            if(f.isDirectory()) {
                continue;
            }
            try {
                FileTime filetime = Files.getLastModifiedTime(Path.of(f.getAbsolutePath()));
//                System.out.println(f.getAbsolutePath() + ": " + filetime.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Add new job with current dir
        if(directory.getName().startsWith(this.fileCorpusPrefix) && isCorpus) {
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
        this.directoryNames.add(new CrawlerJob());
    }
}
