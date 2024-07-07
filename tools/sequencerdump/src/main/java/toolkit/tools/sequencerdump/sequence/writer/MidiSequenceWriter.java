package toolkit.tools.sequencerdump.sequence.writer;

import toolkit.tools.sequencerdump.utils.MidiChannelStack;
import toolkit.tools.sequencerdump.utils.MidiUtils;

import javax.sound.midi.*;
import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;

public class MidiSequenceWriter extends AbstractSequenceWriter {

    public static final int MIDI_TICKS_PER_QUARTER_NOTE = 96; // 1/4 note = 96 ticks
    public static final int MIDI_TICKS_PER_16TH_NOTE = MIDI_TICKS_PER_QUARTER_NOTE / 4; // 1/16 note = 24 ticks
    public static final int MIDI_TICKS_PER_12TH_NOTE = MIDI_TICKS_PER_QUARTER_NOTE / 3; // 1/12 note = 32 ticks
    public static final int MIDI_TICKS_AUTOMATION_RESOLUTION = 1; // 1/48 note = 8 ticks

    private final toolkit.tools.sequencerdump.sequence.Sequence sequence;

    public MidiSequenceWriter(toolkit.tools.sequencerdump.sequence.Sequence sequence) {
        this.sequence = sequence;
    }

    private long pointToMidiTick(toolkit.tools.sequencerdump.sequence.Sequence.Track.Note.Point point) {
        long result;
        if (point.isTriplet()) {
            // Calculate the base position for triplet notes
            int tripletGroup = point.getStep() / 4; // Each quarter note contains 4 16th notes
            int tripletPosition = point.getStep() % 4; // Position within the 16th note group
            result = (long) tripletGroup * MIDI_TICKS_PER_QUARTER_NOTE + tripletPosition * MIDI_TICKS_PER_12TH_NOTE;
        } else {
            result = (long) point.getStep() * MIDI_TICKS_PER_16TH_NOTE;
        }
        return result;
    }

    @Override
    public void writeMidiTracks(File outputFolder) throws InvalidMidiDataException, IOException {
        outputFolder.mkdirs();

        for (toolkit.tools.sequencerdump.sequence.Sequence.Track track : sequence.getTracks()) {
            javax.sound.midi.Sequence midiOut = new javax.sound.midi.Sequence(
                    javax.sound.midi.Sequence.PPQ,
                    MIDI_TICKS_PER_QUARTER_NOTE
            );
            javax.sound.midi.Track defaultTrack = midiOut.createTrack();

            MidiUtils.trackName(defaultTrack, "%d %s".formatted(track.getId(), track.getInstrument().getName()));

            // Fake note to fix Logic Pro alignment
            MidiUtils.noteOn(defaultTrack, 0, 0, 0, 1);
            MidiUtils.noteOff(defaultTrack, 1, 0, 0);

            // TODO: set bpm

            List<toolkit.tools.sequencerdump.sequence.Sequence.Track.Note.Point> points = track.getNotes().stream()
                    .flatMap(note -> note.getPoints().stream())
                    .sorted(Comparator.comparingLong(this::pointToMidiTick))
                    .toList();

            MidiChannelStack automationChannels = new MidiChannelStack(15, 1);

            for (toolkit.tools.sequencerdump.sequence.Sequence.Track.Note.Point point : points) {
                toolkit.tools.sequencerdump.sequence.Sequence.Track.Note note = point.getParent();

                Integer channel;
                if (note.hasAutomation()) {
                    if (automationChannels.isFull()) {
                        // Max polyphony for automation notes reached!
                        System.err.println("Max polyphony reached! Note will be skipped!");
                        continue;
                    } else if (point.isStart()) {
                        channel = automationChannels.push(note.getId()); // Add note to channel stack and get channel
                    } else {
                        channel = automationChannels.peek(note.getId()); // Get note channel
                        if (channel == null) {
                            // Note start was skipped due tue max polyphony, skip updates/end
                            continue;
                        }
                    }
                } else {
                    channel = 0;
                }

                if (point.isStart()) {
                    // Note on

                    // Reset MPE values to note
                    MidiUtils.channelPressure(defaultTrack, pointToMidiTick(point), channel, note.hasAutomation() ? point.getVolume() : MidiUtils.MAX_CC_VALUE);
                    MidiUtils.timbre(defaultTrack, pointToMidiTick(point), channel, note.hasAutomation() ? point.getTimbre() : 64);
                    MidiUtils.pitchBend(defaultTrack, pointToMidiTick(point), channel, MidiUtils.ZERO_PITCH_VALUE);

                    MidiUtils.noteOn(defaultTrack, pointToMidiTick(point), channel, point.getNote(), Math.max(1, note.hasAutomation() ? 100 : point.getVolume()));

                    //MidiUtils.metaText(defaultTrack, point.toMidiTick(), "ON(t:" + point.toMidiTick() + "ch:" + channel + ";nt:" + point.getNote() + ";ni:" + point.getParent().getId() + ")");
                } else {
                    // Update
                    toolkit.tools.sequencerdump.sequence.Sequence.Track.Note.Point start = note.getStart();
                    toolkit.tools.sequencerdump.sequence.Sequence.Track.Note.Point previous = point.getPrevious();

                    long automationSteps = (pointToMidiTick(point) - pointToMidiTick(previous)) / MIDI_TICKS_AUTOMATION_RESOLUTION;

                    int deltaVolume = point.getVolume() - previous.getVolume();
                    if (deltaVolume != 0) {
                        double volumeIncrement = (double) deltaVolume / automationSteps;
                        double currentVolume = previous.getVolume();
                        for (
                                long currentTick = pointToMidiTick(previous);
                                currentTick < pointToMidiTick(point);
                                currentTick += MIDI_TICKS_AUTOMATION_RESOLUTION
                        ) {
                            currentVolume += volumeIncrement;

                            var actualValue = (int) Math.round(currentVolume);
                            MidiUtils.channelPressure(defaultTrack, currentTick, channel, actualValue);
                        }
                    }

                    int deltaTimbre = point.getTimbre() - previous.getTimbre();
                    if (deltaTimbre != 0) {
                        MidiUtils.timbre(defaultTrack, pointToMidiTick(point), channel, point.getTimbre());

                        double timbreIncrement = (double) deltaTimbre / automationSteps;
                        double currentTimbre = previous.getVolume();
                        for (
                                long currentTick = pointToMidiTick(previous);
                                currentTick < pointToMidiTick(point);
                                currentTick += MIDI_TICKS_AUTOMATION_RESOLUTION
                        ) {
                            currentTimbre += timbreIncrement;

                            var actualValue = (int) Math.round(currentTimbre);
                            MidiUtils.timbre(defaultTrack, currentTick, channel, actualValue);
                        }
                    }

                    double deltaNote = point.getNote() - previous.getNote();
                    if (deltaNote != 0) {
                        double previousBend = previous.getNote() - start.getNote();
                        double semitonesIncrement = deltaNote / automationSteps;

                        double currentSemitones = previousBend;
                        for (
                                long currentTick = pointToMidiTick(previous);
                                currentTick < pointToMidiTick(point);
                                currentTick += MIDI_TICKS_AUTOMATION_RESOLUTION
                        ) {
                            currentSemitones += semitonesIncrement;

                            int bendValue = MidiUtils.ZERO_PITCH_VALUE + (int) Math.round(
                                    currentSemitones * MidiUtils.SEMITONE_PITCH_VALUE
                            );
                            if (bendValue < 0 || bendValue > MidiUtils.MAX_PITCH_VALUE) {
                                System.err.println("Note exceeds the maximum pitch bend range! (" + bendValue + ") Will be constrained.");
                                MidiUtils.pitchBend(defaultTrack, currentTick, channel, bendValue);
                                break;
                            }
                            MidiUtils.pitchBend(defaultTrack, currentTick, channel, bendValue);
                        }
                    }
                }

                if (point.isEnd()) {
                    // Note off
                    MidiUtils.noteOff(defaultTrack, pointToMidiTick(point), channel, note.getStart().getNote());
                    automationChannels.pull(note.getId()); // Remove note from channel stack

                    //MidiUtils.metaText(defaultTrack, point.toMidiTick(), "OFF(t:" + point.toMidiTick() + "ch:" + channel + ";nt:" + point.getNote() + ";ni:" + point.getParent().getId() + ")");
                }
            }

            File outputFile = new File(outputFolder, "%d %s.mid".formatted(track.getId(), track.getInstrument().getName()));
            MidiSystem.write(midiOut, 1, outputFile);
        }
    }
}
