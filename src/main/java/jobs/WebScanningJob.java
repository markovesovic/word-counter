package jobs;


public class WebScanningJob extends Job {

    private final String url;
    private final int hopCount;

    public WebScanningJob(String url, int hopCount) {
        super(false);
        this.url = url;
        this.hopCount = hopCount;
    }

    public WebScanningJob() {
        super(true);
        this.url = "";
        this.hopCount = -1;
    }

    public String getUrl() {
        return url;
    }

    public int getHopCount() {
        return hopCount;
    }
}
