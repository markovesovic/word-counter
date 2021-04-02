package jobs;

public class WebScanningJob {

    private final String webUrl;

    public WebScanningJob(String webUrl) {
        this.webUrl = webUrl;
    }

    public String getWebUrl() {
        return this.webUrl;
    }

}
