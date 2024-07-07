package toolkit.tools.sequencerdump.sequence.writer;

import java.io.File;

public abstract class AbstractSequenceWriter {
    public abstract void writeMidiTracks(File outputFolder) throws Exception;
}
