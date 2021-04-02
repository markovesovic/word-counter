package result_retriever;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class DomainMergerWorker implements Callable<Map<String, Integer>> {

    private final List<Map<String, Integer>> jobs;

    public DomainMergerWorker(List<Map<String, Integer>> jobs) {
        this.jobs = jobs;
    }

    @Override
    public Map<String, Integer> call() throws Exception {

        Map<String, Integer> result = new HashMap<>();
        this.jobs.forEach(job -> {
            this.jobs.forEach(occurrence -> {
                occurrence.forEach((key, value) -> {
                    result.merge(key, value, Integer::sum);
                });
            });
        });

        return result;
    }

}
