package jobs;

import main.Poisonable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class ResultJob implements Poisonable {

    private final ScanningJobType type;
    private final List<Future<Map<String, Integer>>> futureResult;
    private final Map<String, Integer> calculatedResult;
    private final String corpusName;
    private final boolean poison;

    public ResultJob(ScanningJobType type, String corpusName, List<Future<Map<String, Integer>>> futureResult) {
        this.type = type;
        this.futureResult = futureResult;
        this.calculatedResult = new HashMap<>();
        this.corpusName = corpusName;
        this.poison = false;
    }
    public ResultJob() {
        this.type = null;
        this.futureResult = null;
        this.calculatedResult = null;
        this.corpusName = null;
        this.poison = true;
    }

    public ScanningJobType getType() {
        return type;
    }

    public Map<String, Integer> getResult() {
        assert calculatedResult != null;
        if(!calculatedResult.isEmpty()) {
            return this.calculatedResult;
        }

        assert this.futureResult != null;
        for(Future<Map<String, Integer>> future : this.futureResult) {

            try {
                Map<String, Integer> localOccurrences = future.get();
                localOccurrences.forEach((key, value) -> this.calculatedResult.merge(key, value, Integer::sum));

            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        return this.calculatedResult;
    }

    public String getCorpusName() {
        return this.corpusName;
    }

    @Override
    public boolean isPoisonous() {
        return this.poison;
    }
}
