package job_dispatcher;

import java.util.Map;
import java.util.concurrent.Future;

public interface ScanningJob {
    String query();
    Future<Map<String, Integer>> initiate();
    boolean isPoison();
}
