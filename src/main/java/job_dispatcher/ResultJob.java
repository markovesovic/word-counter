package job_dispatcher;

import main.Poisonable;

import java.util.Map;

public class ResultJob implements Poisonable {

    private final ScanningJobType type;
    private final Map<String, Map<String, Integer>> result;
    private final boolean poison;

    public ResultJob(ScanningJobType type, Map<String, Map<String, Integer>> result) {
        this.type = type;
        this.result = result;
        this.poison = false;
    }
    public ResultJob() {
        this.type = null;
        this.result = null;
        this.poison = true;
    }

    public ScanningJobType getType() {
        return type;
    }

    public Map<String, Map<String, Integer>> getResult() {
        return result;
    }

    @Override
    public boolean isPoisonous() {
        return this.poison;
    }
}
