package job_dispatcher;

import java.util.Map;
import java.util.concurrent.Future;

public class FileScanningJob implements ScanningJob {

    private final String directoryName;

    private final boolean poison;

    public FileScanningJob(String directoryName) {
        this.directoryName = directoryName;
        this.poison = false;
    }

    public FileScanningJob() {
        this.directoryName = "";
        this.poison = true;
    }

    @Override
    public String query() {
        return null;
    }

    @Override
    public Future<Map<String, Integer>> initiate() {
        return null;
    }

    @Override
    public boolean isPoison() {
        return this.poison;
    }

    public String getDirectoryName() {
        return this.directoryName;
    }
}
