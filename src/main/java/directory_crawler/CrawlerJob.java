package directory_crawler;

public class CrawlerJob {

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

    public boolean isPoison() {
        return poison;
    }
}
