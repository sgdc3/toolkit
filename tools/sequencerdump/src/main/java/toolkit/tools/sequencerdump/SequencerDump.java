package toolkit.tools.sequencerdump;

import cwlib.enums.Part;
import cwlib.enums.ResourceType;
import cwlib.io.Resource;
import cwlib.resources.RLevel;
import cwlib.resources.RPlan;
import cwlib.structs.things.Thing;
import cwlib.structs.things.components.CompactComponent;
import cwlib.structs.things.parts.*;
import cwlib.types.SerializedResource;
import cwlib.types.archives.Fat;
import cwlib.types.archives.SaveArchive;
import cwlib.types.data.WrappedResource;
import cwlib.util.*;
import org.joml.Vector3f;
import toolkit.tools.sequencerdump.sequence.Sequence;
import toolkit.tools.sequencerdump.sequence.SequenceThingReader;
import toolkit.tools.sequencerdump.sequence.writer.AbletonSequenceWriter;
import toolkit.tools.sequencerdump.sequence.writer.AbstractSequenceWriter;
import toolkit.tools.sequencerdump.utils.PositionUtils;
import toolkit.tools.sequencerdump.sequence.writer.MidiSequenceWriter;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public class SequencerDump {

    public static void main(String[] args) {
        if (args.length < 2 || args.length > 3) {
            System.out.println("java -jar sequencerdump.java <input> <outputPath> [format]");
            return;
        }

        File input = new File(args[0]);
        File outputPath = new File(args[1]);

        ExportType format = ExportType.MIDI;
        if (args.length == 3) {
            format = ExportType.valueOf(args[2].toUpperCase());
        }

        if (!input.exists()) {
            System.err.println("Input file doesn't exist!");
            return;
        }

        processFile(input, outputPath, format);
    }

    private static void processLevelBackup(File backupFolder, File outputFolder, ExportType exportType) {
        Pattern regex = Pattern.compile("\\d+");
        File[] fragments = backupFolder.listFiles((dir, name) -> regex.matcher(name).matches());
        if (fragments == null || fragments.length == 0) {
            return;
        }
        // Fix file sorting on linux
        Arrays.sort(fragments, Comparator.comparing(File::getName));

        byte[][] data = new byte[fragments.length + 1][];
        data[fragments.length] = new byte[] { 0x46, 0x41, 0x52, 0x34 };

        for (int i = 0; i < fragments.length; ++i) {
            byte[] fragment = FileIO.read(fragments[i].getAbsolutePath());
            if (fragment == null) {
                continue;
            }
            if (i + 1 == fragments.length) {
                fragment = Arrays.copyOfRange(fragment, 0, fragment.length - 4);
            }
            data[i] = Crypto.XXTEA(fragment, true);
        }

        SaveArchive saveArchive = new SaveArchive(Bytes.combine(data));
        for (Fat entry : saveArchive) {
            byte[] entryData = entry.extract();
            ResourceType type = Resources.getResourceType(entryData);
            Resource resource = null;
            switch (type) {
                case LEVEL -> resource = saveArchive.loadResource(entry.getSHA1(), RLevel.class);
                case PLAN -> resource = saveArchive.loadResource(entry.getSHA1(), RPlan.class);
            }
            if (resource == null) {
                continue;
            }
            processResource(resource, outputFolder, exportType);
        }
    }

    private static void processFile(File input, File outputPath, ExportType exportType) {
        System.out.println("Processing file " + input.getName());

        if (input.isDirectory()) {
            if (input.getName().contains("LEVEL")) {
                processLevelBackup(input, outputPath, exportType);
                return;
            }
            for (File current : Objects.requireNonNull(input.listFiles())) {
                processFile(current, outputPath, exportType);
            }
            return;
        }

        WrappedResource wrapper;
        if (input.getAbsolutePath().toLowerCase().endsWith(".json")) {
            System.out.println("[MODE] JSON -> MIDI");
            wrapper = GsonUtils.fromJSON(
                    FileIO.readString(Path.of(input.getAbsolutePath())),
                    WrappedResource.class
            );
        } else {
            System.out.println("[MODE] RESOURCE -> MIDI");
            SerializedResource resource = new SerializedResource(input.getAbsolutePath());
            wrapper = new WrappedResource(resource);
        }

        Resource resource = (Resource) wrapper.resource;
        processResource(resource, outputPath, exportType);
    }

    private static void processResource(Resource resource, File outputPath, ExportType exportType) {
        List<Thing> things;
        if (resource instanceof RLevel level) {
            PWorld world = level.worldThing.getPart(Part.WORLD);
            things = world.things;
        } else if (resource instanceof RPlan plan) {
            things = List.of(plan.getThings());
        } else {
            throw new RuntimeException("Unsupported resource " + resource.getClass().getSimpleName());
        }

        for (Thing thing : things) {
            if (thing == null) {
                // Skip null things
                continue;
            }

            PMicrochip microchip = thing.getPart(Part.MICROCHIP);
            if (microchip == null) {
                // Not a Microchip
                continue;
            }

            // Include instruments of sequencers with an open circuit board
            Thing circuitBoardThing = microchip.circuitBoardThing;
            if (circuitBoardThing.hasPart(Part.POS)) {
                microchip.components = things.stream().filter(current ->
                        current != null
                                && current.parent != null
                                && current.parent.UID == circuitBoardThing.UID
                                && current.hasPart(Part.INSTRUMENT)
                ).map(child -> {
                    Vector3f instrumentPosition = PositionUtils.getRelativePosition(circuitBoardThing, child);
                    CompactComponent component = new CompactComponent();
                    component.x = instrumentPosition.x;
                    component.y = instrumentPosition.y;
                    component.thing = child;

                    return component;
                }).toArray(CompactComponent[]::new);
            }

            PSequencer sequencer = thing.getPart(Part.SEQUENCER);
            if (sequencer == null) {
                // Not a sequencer
                continue;
            }
            if (!sequencer.musicSequencer) {
                //  Not a music sequencer
                continue;
            }
            SequenceThingReader loader = new SequenceThingReader(thing);
            Sequence sequence = loader.load();
            AbstractSequenceWriter dumper = switch (exportType) {
                case MIDI -> new MidiSequenceWriter(sequence);
                case ABLETON -> new AbletonSequenceWriter(sequence);
            };
            String sanitizedSequenceName = sequence.getName()
                    .replace("&quot;", "\"")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&apos;", "'")
                    .replaceAll("[\\\\/:*?\"<>|]", "");
            File sequenceOutputFolder = new File(outputPath, sanitizedSequenceName);
            try {
                dumper.writeMidiTracks(sequenceOutputFolder);
            } catch (Exception e) {
                throw new RuntimeException("Unable to export midi!", e);
            }
        }
    }
}
