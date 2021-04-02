package jobs;

import main.Poisonable;

public class ScanningJob implements Poisonable {

    private final ScanningJobType type;
    private final String path;
    private final int hopCount;
    private final boolean poison;

    // Creates web scanning job
    public ScanningJob(String path, int hopCount) {
        this.type = ScanningJobType.WEB_SCANNING_JOB;
        this.path = path;
        this.hopCount = hopCount;
        this.poison = false;
    }

    // Creates file scanning job
    public ScanningJob(String path) {
        this.type = ScanningJobType.FILE_SCANNING_JOB;
        this.path = path;
        this.hopCount = -1;
        this.poison = false;
    }

    // Creates poisonous scanning job
    public ScanningJob() {
        this.type = null;
        this.path = null;
        this.hopCount = -1;
        this.poison = true;
    }

    public ScanningJobType getType() {
        return this.type;
    }

    public String getPath() {
        return path;
    }

    public int getHopCount() {
        return hopCount;
    }

    @Override
    public boolean isPoisonous() {
        return this.poison;
    }

}
