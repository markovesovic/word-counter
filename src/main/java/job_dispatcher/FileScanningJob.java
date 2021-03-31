package job_dispatcher;


import java.io.File;
import java.util.List;

public class FileScanningJob {

    private final List<File> files;

    public FileScanningJob(List<File> files) {
        this.files = files;
    }

    public List<File> getFiles() {
        return this.files;
    }

}
