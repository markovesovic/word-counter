package result_retriever;

import jobs.WebScanningResultJob;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class DomainMergerWorker implements Callable<Map<String, Integer>> {

    private final List<WebScanningResultJob> jobs;

    public DomainMergerWorker(List<WebScanningResultJob> jobs) {
        this.jobs = jobs;
    }

    @Override
    public Map<String, Integer> call() {

        Map<String, Integer> result = new HashMap<>();
        this.jobs.forEach(job -> {
            Map<String, Integer> res = job.getResult();
            if(res != null) {
                res.forEach((key, value) -> result.merge(key, value, Integer::sum));
            }
        });

        return result;
    }

}
