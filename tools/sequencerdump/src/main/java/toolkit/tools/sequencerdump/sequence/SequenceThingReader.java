package toolkit.tools.sequencerdump.sequence;

import cwlib.enums.Part;
import cwlib.structs.instrument.Note;
import cwlib.structs.things.Thing;
import cwlib.structs.things.components.CompactComponent;
import cwlib.structs.things.parts.PInstrument;
import cwlib.structs.things.parts.PMicrochip;
import cwlib.structs.things.parts.PSequencer;
import toolkit.tools.sequencerdump.instrument.Instrument;
import toolkit.tools.sequencerdump.utils.MathUtils;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

public class SequenceThingReader {

    public static final float GRID_UNIT_SIZE = 52.5F; // Grid size is min unit size / 2
    public static final int GRID_UNIT_STEPS = 16; // Steps per grid unit is min unit steps / 2

    private final Thing thing;
    private final PMicrochip microchip;
    private final PSequencer sequencer;

    public SequenceThingReader(Thing thing) {
        this.thing = thing;
        this.microchip = Objects.requireNonNull(thing.getPart(Part.MICROCHIP), "Thing isn't a microchip!");
        this.sequencer = Objects.requireNonNull(thing.getPart(Part.SEQUENCER), "Thing isn't a sequencer!");
        if (!this.sequencer.musicSequencer) {
            throw new IllegalArgumentException("Thing isn't a music sequencer!");
        }
    }

    public Sequence load() {
        Sequence result = new Sequence();

        // Extract metadata
        String name = microchip.name.trim();
        if (name.isEmpty()) {
            name = "Unnamed_" + thing.UID;
        }
        result.setName(name);
        result.setTempo(sequencer.tempo);
        result.setSwing(sequencer.swing);

        System.out.println(MessageFormat.format("Found sequencer: {0}", result.getName()));

        // Snap to grid
        // LBP positioning isn't very precise, round to fraction of 2 to have a bit of tolerance
        for (CompactComponent component : microchip.components) {
            component.x = MathUtils.roundToFraction(component.x, 2);
            component.y = MathUtils.roundToFraction(component.y, 2);
        }

        // Sort components, makes debugging easier
        Arrays.sort(microchip.components, (a, b) -> {
            if (a.x == b.x) {
                return Float.compare(b.y, a.y);
            }
            return Float.compare(a.x, b.x);
        });

        // Process components, extract note data and group them by track
        for (CompactComponent component : microchip.components) {
            PInstrument instrument = component.thing.getPart(Part.INSTRUMENT);
            if (instrument == null) {
                continue; // Skip non-instruments
            }

            Instrument instrumentType = Instrument.fromGUID(instrument.instrument.getGUID());

            // Since lbp doesn't group instruments into channels, treat instruments of the same type on same row as a channel
            Sequence.Track track = result.getTrack(component.y, instrumentType);
            if (track == null) {
                track = result.addTrack(component.y, instrumentType);
            }

            // Calculate current toolkit.sequencerdump.instrument grid index and tick
            int componentGridIndex = (int) Math.floor(component.x / GRID_UNIT_SIZE);
            int componentInitialStep = componentGridIndex * GRID_UNIT_STEPS;

            Sequence.Track.Note currentNote = null;
            for (Note note : instrument.notes) {
                int noteStep = componentInitialStep + note.x;
                if (currentNote == null) {
                    currentNote = track.addNote();
                }
                currentNote.pushPoint(noteStep, note.y, note.volume, note.timbre, note.triplet);
                if (note.end) {
                    // Push end
                    currentNote.pushPoint(noteStep + 1, note.y, note.volume, note.timbre, note.triplet);
                    // Fix point order, sometimes this isn't correct...
                    currentNote.getPoints().sort(Comparator.comparingInt(Sequence.Track.Note.Point::getStep));
                    currentNote = null;
                }
            }
            if (currentNote != null) {
                throw new RuntimeException("Should never happen!");
            }
        }

        return result;
    }
}
