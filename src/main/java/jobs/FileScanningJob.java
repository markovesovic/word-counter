package jobs;

public class FileScanningJob extends Job {

    private final String path;

    public FileScanningJob(String path) {
        super(false);
        this.path = path;
    }

    public FileScanningJob() {
        super(true);
        this.path = "";
    }

    public String getPath() {
        return this.path;
    }
}
