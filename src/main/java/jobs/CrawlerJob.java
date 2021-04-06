package jobs;

import main.Poisonable;

public class CrawlerJob extends Job {

    private final String directoryName;

    // Creates job with given directory name
    public CrawlerJob(String directoryName) {
        super(false);
        this.directoryName = directoryName;
    }

    // Creates poisonous job
    public CrawlerJob() {
        super(true);
        this.directoryName = "";
    }

    public String getDirectoryName() {
        return directoryName;
    }

}
