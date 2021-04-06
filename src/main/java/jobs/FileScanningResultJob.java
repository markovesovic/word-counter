package jobs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class FileScanningResultJob extends Job {

    private final List<Future<Map<String, Integer>>> futures;
    private final Map<String, Integer> result;
    private final String corpusName;

    private boolean calculating = false;

    public FileScanningResultJob(List<Future<Map<String, Integer>>> futures, String corpusName) {
        super(false);
        this.futures = futures;
        this.corpusName = corpusName;
        this.result = new HashMap<>();
    }

    public Map<String, Integer> queryResult() {
        if(this.isReady()) {
            return this.getResult();
        }
        return null;
    }

    public Map<String, Integer> getResult() {
        if(!this.result.isEmpty()) {
            return this.result;
        }

        for(Future<Map<String, Integer>> future : this.futures) {

            try {
                Map<String, Integer> localOccurrences = future.get();
                if(localOccurrences == null) {
                    continue;
                }
                localOccurrences.forEach((key, value) -> this.result.merge(key, value, Integer::sum));

            } catch (InterruptedException | ExecutionException e) {
                System.out.println("Ovde puca");
            }
        }

        return this.result;
    }

    public boolean isReady() {
        for(Future<Map<String, Integer>> future : this.futures) {
            if(!future.isDone()) {
                return false;
            }
        }
        return true;
    }

    public String getCorpusName() {
        return this.corpusName;
    }

}
