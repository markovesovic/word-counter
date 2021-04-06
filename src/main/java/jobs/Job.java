package jobs;

import main.Poisonable;

public class Job implements Poisonable {

    private final boolean poison;

    public Job(boolean poison) {
        this.poison = poison;
    }

    @Override
    public boolean isPoisonous() {
        return this.poison;
    }

}
