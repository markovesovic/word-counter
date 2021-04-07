package jobs;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class WebScanningResultJob extends Job {

    private final Future<Map<String, Integer>> future;
    private final Map<String, Integer> result;
    private final String webUrl;

    public WebScanningResultJob(Future<Map<String, Integer>> future, String webUrl) {
        super(false);
        this.future = future;
        this.result = new HashMap<>();
        this.webUrl = webUrl;
    }

    public Map<String, Integer> getResult() {
        if(!this.result.isEmpty()) {
            return this.result;
        }
        try {
            Map<String, Integer> localOccurrences = future.get();
            if(localOccurrences == null) {
                return null;
            }
            localOccurrences.forEach((key, value) -> this.result.merge(key, value, Integer::sum));

        } catch (InterruptedException e) {
            System.out.println("InterruptedException");
        } catch (ExecutionException e) {
            System.out.println("ExecutionException");
        } catch (NullPointerException e ) {
            System.out.println("NullPointerException");
        }
        return this.result;
    }

    public boolean isReady() {
        return this.future.isDone();
    }

    public String getWebUrl() {
        return this.webUrl;
    }

}
