package toolkit.tools.sequencerdump.sequence.writer;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import toolkit.tools.sequencerdump.sequence.Sequence;
import toolkit.tools.sequencerdump.utils.MidiUtils;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

public class AbletonSequenceWriter extends AbstractSequenceWriter {

    public static final double STEP_QUARTER_NOTE = 1;
    public static final double STEP_16TH_NOTE = STEP_QUARTER_NOTE / 4;
    public static final double STEP_12TH_NOTE = STEP_QUARTER_NOTE / 3;

    private static final double PITCH_BEND_SEMITONE = (double) 8192 / 48;

    private final Sequence sequence;

    public AbletonSequenceWriter(Sequence sequence) {
        this.sequence = sequence;
    }

    public double stepToAbletonPosition(int step, boolean triplet) {
        double result;
        if (triplet) {
            // Calculate the base position for triplet notes
            int tripletGroup = step / 4; // Each quarter note contains 4 16th notes
            int tripletPosition = step % 4; // Position within the 16th note group
            result = tripletGroup * STEP_QUARTER_NOTE + tripletPosition * STEP_12TH_NOTE;
        } else {
            result = step * STEP_16TH_NOTE;
        }
        return result;
    }

    private void populateSequencerElement(Document document, Element sequencerElement, Sequence.Track track) {
        Element clipSlotListElement = document.createElement("ClipSlotList");
        sequencerElement.appendChild(clipSlotListElement);

        Element clipSlotElement = document.createElement("ClipSlot");
        clipSlotElement.setAttribute("Id", "0");
        clipSlotListElement.appendChild(clipSlotElement);

        Element clipSlotInnerElement = document.createElement("ClipSlot");
        clipSlotElement.appendChild(clipSlotInnerElement);

        Element valueElement = document.createElement("Value");
        clipSlotInnerElement.appendChild(valueElement);

        if (track == null) {
            return;
        }

        Element midiClipElement = document.createElement("MidiClip");
        midiClipElement.setAttribute("Id", "0");
        valueElement.appendChild(midiClipElement);

        Element notesElement = document.createElement("Notes");
        midiClipElement.appendChild(notesElement);

        Element keyTracksElement = document.createElement("KeyTracks");
        notesElement.appendChild(keyTracksElement);

        Element perNoteEventStoreElement = document.createElement("PerNoteEventStore");
        notesElement.appendChild(perNoteEventStoreElement);

        Element eventListsElement = document.createElement("EventLists");
        perNoteEventStoreElement.appendChild(eventListsElement);

        Map<Integer, Element> keyTrackNotesElements = new LinkedHashMap<>();
        int lastNoteId = 0;
        int lastEventListId = 0;

        for (Sequence.Track.Note note : track.getNotes()) {
            Element currentKeyNotesElement = keyTrackNotesElements.computeIfAbsent(note.getStart().getNote(), currentNote -> {
                Element currentKeyTrackElement = document.createElement("KeyTrack");
                currentKeyTrackElement.setAttribute("Id", String.valueOf(keyTrackNotesElements.size() + 1));
                keyTracksElement.appendChild(currentKeyTrackElement);

                Element currentNotesElement = document.createElement("Notes");
                currentKeyTrackElement.appendChild(currentNotesElement);

                Element currentMidiKeyElement = document.createElement("MidiKey");
                currentMidiKeyElement.setAttribute("Value", String.valueOf(currentNote));
                currentKeyTrackElement.appendChild(currentMidiKeyElement);

                return currentNotesElement;
            });

            Element midiNoteEventElement = document.createElement("MidiNoteEvent");
            double timeStart = stepToAbletonPosition(note.getStart().getStep() - 1, note.getStart().isTriplet());
            double timeEnd = stepToAbletonPosition(note.getEnd().getStep() - 1, note.getEnd().isTriplet());
            midiNoteEventElement.setAttribute("Time", String.valueOf(timeStart));
            double duration = timeEnd - timeStart;
            midiNoteEventElement.setAttribute("Duration", String.valueOf(duration));
            int velocity = note.hasVolumeAutomation() ? 100 : note.getStart().getVolume();
            midiNoteEventElement.setAttribute("Velocity", String.valueOf(velocity));
            midiNoteEventElement.setAttribute("NoteId", String.valueOf(++lastNoteId));
            currentKeyNotesElement.appendChild(midiNoteEventElement);

            {
                Element perNoteEventListElement = document.createElement("PerNoteEventList");
                perNoteEventListElement.setAttribute("Id", String.valueOf(++lastEventListId));
                perNoteEventListElement.setAttribute("NoteId", String.valueOf(lastNoteId));
                perNoteEventListElement.setAttribute("CC", "-2"); // MPE Pitch
                eventListsElement.appendChild(perNoteEventListElement);

                Element noteEventsElement = document.createElement("Events");
                perNoteEventListElement.appendChild(noteEventsElement);

                // Initial value
                {
                    Element pitchBendPointElement = document.createElement("PerNoteEvent");
                    pitchBendPointElement.setAttribute("TimeOffset", String.valueOf(0));
                    pitchBendPointElement.setAttribute("Value", String.valueOf(0));
                    noteEventsElement.appendChild(pitchBendPointElement);
                }

                if (note.hasPitchBend()) {
                    for (Sequence.Track.Note.Point point : note.getPoints()) {
                        if (point.isStart()) {
                            continue;
                        }
                        Element pitchBendPointElement = document.createElement("PerNoteEvent");
                        double timeOffset = stepToAbletonPosition(point.getStep() - point.getParent().getStart().getStep(), point.isTriplet());
                        pitchBendPointElement.setAttribute("TimeOffset", String.valueOf(timeOffset));
                        int deltaSemitones = point.getNote() - point.getParent().getStart().getNote();
                        pitchBendPointElement.setAttribute("Value", String.valueOf(PITCH_BEND_SEMITONE * deltaSemitones));
                        noteEventsElement.appendChild(pitchBendPointElement);
                    }
                }
            }

            {
                Element perNoteEventListElement = document.createElement("PerNoteEventList");
                perNoteEventListElement.setAttribute("Id", String.valueOf(++lastEventListId));
                perNoteEventListElement.setAttribute("NoteId", String.valueOf(lastNoteId));
                perNoteEventListElement.setAttribute("CC", "-1"); // MPE Pressure
                eventListsElement.appendChild(perNoteEventListElement);

                Element noteEventsElement = document.createElement("Events");
                perNoteEventListElement.appendChild(noteEventsElement);

                // Initial value
                {
                    Element pitchBendPointElement = document.createElement("PerNoteEvent");
                    pitchBendPointElement.setAttribute("TimeOffset", String.valueOf(0));
                    pitchBendPointElement.setAttribute("Value", String.valueOf(note.hasVolumeAutomation() ? note.getStart().getVolume() : MidiUtils.MAX_CC_VALUE));
                    noteEventsElement.appendChild(pitchBendPointElement);
                }

                if (note.hasVolumeAutomation()) {
                    for (Sequence.Track.Note.Point point : note.getPoints()) {
                        Element pitchBendPointElement = document.createElement("PerNoteEvent");
                        double timeOffset = stepToAbletonPosition(point.getStep() - point.getParent().getStart().getStep(), point.isTriplet());
                        pitchBendPointElement.setAttribute("TimeOffset", String.valueOf(timeOffset));
                        pitchBendPointElement.setAttribute("Value", String.valueOf(point.getVolume()));
                        noteEventsElement.appendChild(pitchBendPointElement);
                    }
                }
            }

            {
                Element perNoteEventListElement = document.createElement("PerNoteEventList");
                perNoteEventListElement.setAttribute("Id", String.valueOf(++lastEventListId));
                perNoteEventListElement.setAttribute("NoteId", String.valueOf(lastNoteId));
                perNoteEventListElement.setAttribute("CC", "74"); // MPE Pressure
                eventListsElement.appendChild(perNoteEventListElement);

                Element noteEventsElement = document.createElement("Events");
                perNoteEventListElement.appendChild(noteEventsElement);

                // Initial value
                {
                    Element pitchBendPointElement = document.createElement("PerNoteEvent");
                    pitchBendPointElement.setAttribute("TimeOffset", String.valueOf(0));
                    pitchBendPointElement.setAttribute("Value", String.valueOf(note.getStart().getTimbre()));
                    noteEventsElement.appendChild(pitchBendPointElement);
                }

                if (note.hasTimbreAutomation()) {

                    for (Sequence.Track.Note.Point point : note.getPoints()) {
                        Element pitchBendPointElement = document.createElement("PerNoteEvent");
                        double timeOffset = stepToAbletonPosition(point.getStep() - point.getParent().getStart().getStep(), point.isTriplet());
                        pitchBendPointElement.setAttribute("TimeOffset", String.valueOf(timeOffset));
                        pitchBendPointElement.setAttribute("Value", String.valueOf(point.getTimbre()));
                        noteEventsElement.appendChild(pitchBendPointElement);
                    }
                }
            }
        }
    }

    @Override
    public void writeMidiTracks(File outputFolder) throws ParserConfigurationException, TransformerException, IOException {
        outputFolder.mkdirs();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        Document document = builder.newDocument();

        Element abletonElement = document.createElement("Ableton");
        abletonElement.setAttribute("MajorVersion", "5");
        abletonElement.setAttribute("MinorVersion", "12.0_12049");
        abletonElement.setAttribute("SchemaChangeCount", "12");
        abletonElement.setAttribute("Creator", "Ableton Live 12.0.10");
        abletonElement.setAttribute("Revision", "518b0e8f662095a813fbfe2191c405929dce7c4f");
        document.appendChild(abletonElement);

        Element liveSetElement = document.createElement("LiveSet");
        abletonElement.appendChild(liveSetElement);

        Element nextPointeeIdElement = document.createElement("NextPointeeId");
        nextPointeeIdElement.setAttribute("Value", "1");
        liveSetElement.appendChild(nextPointeeIdElement);

        Element scenesElement = document.createElement("Scenes");
        liveSetElement.appendChild(scenesElement);

        Element sceneElement = document.createElement("Scene");
        sceneElement.setAttribute("Id", "0");
        scenesElement.appendChild(sceneElement);

        {
            Element mainTrackElement = document.createElement("MainTrack");
            liveSetElement.appendChild(mainTrackElement);

            Element deviceChainElement = document.createElement("DeviceChain");
            mainTrackElement.appendChild(deviceChainElement);

            Element mixerElement = document.createElement("Mixer");
            deviceChainElement.appendChild(mixerElement);

            Element tempoElement = document.createElement("Tempo");
            mixerElement.appendChild(tempoElement);

            Element manualElement = document.createElement("Manual");
            manualElement.setAttribute("Value", String.valueOf(sequence.getTempo()));
            tempoElement.appendChild(manualElement);
        }

        Element tracksElement = document.createElement("Tracks");
        liveSetElement.appendChild(tracksElement);

        int trackId = 0;
        for (Sequence.Track track : sequence.getTracks()) {
            Element trackElement = document.createElement("MidiTrack");
            trackElement.setAttribute("Id", String.valueOf(++trackId));
            tracksElement.appendChild(trackElement);

            Element nameElement = document.createElement("Name");
            trackElement.appendChild(nameElement);

            Element userNameElement = document.createElement("UserName");
            userNameElement.setAttribute("Value", "%d %s".formatted(track.getId(), track.getInstrument().getName()));
            nameElement.appendChild(userNameElement);

            Element deviceChainElement = document.createElement("DeviceChain");
            trackElement.appendChild(deviceChainElement);

            Element mainSequencerElement = document.createElement("MainSequencer");
            deviceChainElement.appendChild(mainSequencerElement);
            populateSequencerElement(document, mainSequencerElement, track);

            Element freezeSequencerElement = document.createElement("FreezeSequencer");
            deviceChainElement.appendChild(freezeSequencerElement);
            populateSequencerElement(document, freezeSequencerElement, null);
        }

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        DOMSource source = new DOMSource(document);

        String fileName = "%s.als".formatted(sequence.getName());
        try(FileOutputStream fileOutputStream = new FileOutputStream(new File(outputFolder, fileName))) {
            try(GZIPOutputStream gzipOutputStream = new GZIPOutputStream(fileOutputStream)) {
                transformer.transform(source, new StreamResult(gzipOutputStream));
            }
        }
    }
}
