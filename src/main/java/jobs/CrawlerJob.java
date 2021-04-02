package jobs;

import main.Poisonable;

public class CrawlerJob implements Poisonable {

    private final String directoryName;
    private final boolean poison;

    // Creates job with given directory name
    public CrawlerJob(String directoryName) {
        this.directoryName = directoryName;
        this.poison = false;
    }

    // Creates poisonous job
    public CrawlerJob() {
        this.directoryName = "";
        this.poison = true;
    }

    public String getDirectoryName() {
        return directoryName;
    }

    @Override
    public boolean isPoisonous() {
        return poison;
    }
}
