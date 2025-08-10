package com.michelecossu.chords.transposer.service.impl;

import com.michelecossu.chords.transposer.service.ChordTransposeService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChordTransposeServiceImpl implements ChordTransposeService {

    @Value("${app.files.directory}")
    private String filesDirectory;

    // Map of semitones for each note
    private static final Map<String, Integer> NOTE_TO_SEMITONE = new HashMap<>();
    private static final String[] NOTES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    private static final String[] FLAT_NOTES = {"C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"};

    static {
        NOTE_TO_SEMITONE.put("C", 0);
        NOTE_TO_SEMITONE.put("C#", 1); NOTE_TO_SEMITONE.put("Db", 1);
        NOTE_TO_SEMITONE.put("D", 2);
        NOTE_TO_SEMITONE.put("D#", 3); NOTE_TO_SEMITONE.put("Eb", 3);
        NOTE_TO_SEMITONE.put("E", 4);
        NOTE_TO_SEMITONE.put("F", 5);
        NOTE_TO_SEMITONE.put("F#", 6); NOTE_TO_SEMITONE.put("Gb", 6);
        NOTE_TO_SEMITONE.put("G", 7);
        NOTE_TO_SEMITONE.put("G#", 8); NOTE_TO_SEMITONE.put("Ab", 8);
        NOTE_TO_SEMITONE.put("A", 9);
        NOTE_TO_SEMITONE.put("A#", 10); NOTE_TO_SEMITONE.put("Bb", 10);
        NOTE_TO_SEMITONE.put("B", 11);
    }

    // pattern to ricognize chords (like Cmaj, Dm7, Gsus4, etc)
    private static final Pattern CHORD_PATTERN = Pattern.compile(
            "\\b([A-G][#b]?)(" +
                    "maj\\d*|" +           // maj, maj7, maj9, maj11, maj13
                    "min\\d*|" +           // min, min7, min9, min11, min13
                    "m\\d*|" +             // m, m7, m9, m11, m13
                    "dim\\d*|" +           // dim, dim7
                    "aug\\d*|" +           // aug, aug7
                    "sus[24]?\\d*|" +      // sus, sus2, sus4, sus2add9, etc
                    "add\\d+|" +           // add9, add11, add13
                    "\\d+|" +              // 7, 9, 11, 13
                    "\\+\\d*|" +           // +, +7 (increase symbol)
                    "°\\d*|" +             // ° (decrease symbol)
                    "ø\\d*" +              // ø (half-diminished)
                    ")?(?:/([A-G][#b]?))?\\b" // Optionally alternative bass (/E)
    );

    /**
     * Read a file and return its content as a string.
     * Supports PDF, DOCX, and plain text.
     *
     * @param fileName The name of the file to read.
     * @return The content of the file as a string.
     * @throws IOException If the file does not exist or cannot be read.
     */
    public String readFile(String fileName) throws IOException {
        Path filePath = Paths.get(filesDirectory, fileName);
        if (!Files.exists(filePath)) {
            throw new IOException("File non found: " + fileName);
        }

        String extension = getFileExtension(fileName).toLowerCase();

        try {
            return switch (extension) {
                case "pdf" -> readPDFFile(filePath);
                case "docx" -> readDOCXFile(filePath);
                default -> Files.readString(filePath);
            };
        } catch (Exception e) {
            throw new IOException("Error by reading the file " + fileName + ": " + e.getMessage(), e);
        }
    }

    /**
     * Read a PDF file and extract its text content.
     *
     * @param filePath The path to the PDF file.
     * @return The extracted text content.
     * @throws IOException If an error occurs while reading the file.
     */
    private String readPDFFile(Path filePath) throws IOException {
        try (PDDocument document = PDDocument.load(filePath.toFile())) {
            PDFTextStripper pdfStripper = new PDFTextStripper();

            // Configure PDF extraction settings
            pdfStripper.setSortByPosition(true);
            pdfStripper.setLineSeparator("\n");

            String text = pdfStripper.getText(document);

            // Clean up common PDF extraction artifacts
            return cleanPDFText(text);
        }
    }

    /**
     * Read a DOCX file and extract its text content.
     *
     * @param filePath The path to the DOCX file.
     * @return The extracted text content.
     * @throws IOException If an error occurs while reading the file.
     */
    private String readDOCXFile(Path filePath) throws IOException {
        try (FileInputStream fis = new FileInputStream(filePath.toFile());
             XWPFDocument document = new XWPFDocument(fis)) {

            StringBuilder content = new StringBuilder();

            // Extract text from paragraphs
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            for (XWPFParagraph paragraph : paragraphs) {
                String text = paragraph.getText();
                if (text != null && !text.trim().isEmpty()) {
                    content.append(text).append("\n");
                }
            }

            // Extract text from tables if any
            document.getTables().forEach(table ->
                table.getRows().forEach(row -> {
                    row.getTableCells().forEach(cell -> {
                        String cellText = cell.getText();
                        if (cellText != null && !cellText.trim().isEmpty()) {
                            content.append(cellText).append(" ");
                        }
                    });
                    content.append("\n");
                })
            );

            return content.toString().trim();
        }
    }

    /**
     * Clean up text extracted from PDF files to remove artifacts and normalize formatting.
     *
     * @param text The raw text extracted from the PDF.
     * @return The cleaned text.
     */
    private String cleanPDFText(String text) {
        return text
                // Remove excessive whitespace
                .replaceAll("\\s{3,}", " ")
                // Remove page headers/footers patterns
                .replaceAll("(?m)^Page \\d+.*$", "")
                // Remove common PDF artifacts
                .replace("\\x00", "")
                // Normalize line breaks
                .replaceAll("\\r\\n|\\r", "\n")
                // Clean up multiple newlines
                .replaceAll("\\n\\s*\\n\\s*\\n", "\n\n")
                .trim();
    }

    /**
     * Get the file extension from the file name.
     *
     * @param fileName The name of the file.
     * @return The file extension or an empty string if no extension is found.
     */
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex > 0 ? fileName.substring(lastDotIndex + 1) : "";
    }

    /**
     * Transpose the content of a song by transposing all chords found in the text.
     *
     * @param content The content of the song as a string.
     * @param semitones The number of semitones to transpose (positive for up, negative for down).
     * @return The transposed content with chords adjusted accordingly.
     */
    public String transposeContent(String content, int semitones) {
        StringBuilder result = new StringBuilder();
        Matcher matcher = CHORD_PATTERN.matcher(content);

        while (matcher.find()) {
            String chord = matcher.group();
            String transposedChord = transposeChord(chord, semitones);
            matcher.appendReplacement(result, transposedChord);
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Transpose a single chord by the specified number of semitones.
     *
     * @param chord The chord to transpose (e.g., "C", "Dm7", "Gsus4/E").
     * @param semitones The number of semitones to transpose (positive for up, negative for down).
     * @return The transposed chord as a string.
     */
    public String transposeChord(String chord, int semitones) {
        // Pattern to match chords in the format: RootNote[Extensions][OptionalBass]
        Pattern chordParser = Pattern.compile("^([A-G][#b]?)(.*?)(?:/([A-G][#b]?))?$");
        Matcher matcher = chordParser.matcher(chord);

        if (!matcher.matches()) {
            return chord; // If the chord does not match the expected format, return it unchanged
        }

        String rootNote = matcher.group(1);      // Root note
        String suffix = matcher.group(2);        // Extensions (m, 7, sus4, etc)
        String bassNote = matcher.group(3);      // Bass note (optional)

        // Transpose the root note
        String newRootNote = transposeNote(rootNote, semitones);
        if (newRootNote == null) return chord;

        // Transpose the bass note if present
        String newBassNote = null;
        if (bassNote != null) {
            newBassNote = transposeNote(bassNote, semitones);
            if (newBassNote == null) return chord;
        }

        // Re-build the transposed chord
        StringBuilder result = new StringBuilder(newRootNote).append(suffix);
        if (newBassNote != null) {
            result.append("/").append(newBassNote);
        }

        return result.toString();
    }

    /**
     * Transpose a single note by the specified number of semitones.
     *
     * @param note The note to transpose (e.g., "C", "D#", "Bb").
     * @param semitones The number of semitones to transpose (positive for up, negative for down).
     * @return The transposed note as a string, or null if the note is not recognized.
     */
    private String transposeNote(String note, int semitones) {
        Integer currentSemitone = NOTE_TO_SEMITONE.get(note);
        if (currentSemitone == null) {
            return null; // Note not recognized
        }

        int newSemitone = (currentSemitone + semitones + 12) % 12;

        // Choose between sharps and flats based on the semitone value
        return shouldUseFlats(semitones) ? FLAT_NOTES[newSemitone] : NOTES[newSemitone];
    }

    /**
     * Determine if flats should be used based on the number of semitones.
     * This is a simplified logic that can be improved based on specific key signatures.
     *
     * @param semitones The number of semitones to transpose.
     * @return true if flats should be used, false if sharps should be used.
     */
    private boolean shouldUseFlats(int semitones) {
        // Simplify the logic for determining whether to use flats or sharps
        return semitones < 0 || semitones % 12 == 1 || semitones % 12 == 3 ||
                semitones % 12 == 6 || semitones % 12 == 8 || semitones % 12 == 10;
    }

    /**
     * Calculate the number of semitones between two keys.
     *
     * @param fromKey The starting key (e.g., "C", "Dm").
     * @param toKey The target key (e.g., "G", "Am").
     * @return The number of semitones to transpose from the fromKey to the toKey.
     */
    public int calculateSemitones(String fromKey, String toKey) {
        // Remove the 'm' suffix if present, as it is not needed for semitone calculation
        String fromRoot = fromKey.replaceAll("m$", "");
        String toRoot = toKey.replaceAll("m$", "");

        Integer fromSemitone = NOTE_TO_SEMITONE.get(fromRoot);
        Integer toSemitone = NOTE_TO_SEMITONE.get(toRoot);

        if (fromSemitone == null || toSemitone == null) {
            throw new IllegalArgumentException("Tonalità non valida");
        }

        int difference = toSemitone - fromSemitone;
        if (difference < 0) {
            difference += 12;
        }

        return difference;
    }

    /**
     * Detect the key of a song based on the chords present in the content.
     *
     * @param content The content of the song as a string.
     * @return The detected key as a string (e.g., "C", "G", "Am").
     */
    public String detectKey(String content) {
        // Simple approach to detect the key based on chord frequency
        // Count the frequency of each root note in the content
        Map<String, Integer> chordFrequency = new HashMap<>();
        Matcher matcher = CHORD_PATTERN.matcher(content);

        while (matcher.find()) {
            String rootNote = matcher.group(1);
            chordFrequency.put(rootNote, chordFrequency.getOrDefault(rootNote, 0) + 1);
        }

        // Find the most frequent root note
        return chordFrequency.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("C"); // Default a C se non trova accordi
    }

    /**
     * Generate a PDF file with the transposed content.
     *
     * @param content The transposed content to be included in the PDF.
     * @param sourceFileName The name of the source file (for title purposes).
     * @param originalKey The original key of the song (for title purposes).
     * @param targetKey The target key of the song (for title purposes).
     * @return A byte array representing the generated PDF file.
     * @throws IOException If an error occurs while generating the PDF.
     */
    @Override
    public byte[] generatePdf(String content, String sourceFileName, String originalKey, String targetKey) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDPageContentStream contentStream = new PDPageContentStream(document, page);

            // Set title with sanitized text
            contentStream.beginText();
            contentStream.setFont(PDType1Font.HELVETICA_BOLD, 14);
            contentStream.newLineAtOffset(50, 750);
            String baseName = sourceFileName.substring(0, sourceFileName.lastIndexOf('.'));
            String titleText = sanitizeText(baseName + " (" + originalKey + " → " + targetKey + ")");
            contentStream.showText(titleText);
            contentStream.endText();

            // Set content
            contentStream.beginText();
            contentStream.setFont(PDType1Font.COURIER, 11);
            contentStream.setLeading(14); // Line spacing
            contentStream.newLineAtOffset(50, 720);

            // Process content line by line
            String[] lines = content.split("\n");
            float yPosition = 720;

            for (String line : lines) {
                // Sanitize each line
                String sanitizedLine = sanitizeText(line);

                // Check if we need a new page
                if (yPosition < 50) {
                    contentStream.endText();
                    contentStream.close();

                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);

                    contentStream = new PDPageContentStream(document, page);

                    contentStream.beginText();
                    contentStream.setFont(PDType1Font.COURIER, 11);
                    contentStream.setLeading(14);
                    yPosition = 750;
                    contentStream.newLineAtOffset(50, yPosition);
                }

                // Handle long lines
                if (sanitizedLine.length() > 100) {
                    for (int i = 0; i < sanitizedLine.length(); i += 100) {
                        String subLine = sanitizedLine.substring(i, Math.min(i + 100, sanitizedLine.length()));
                        contentStream.showText(subLine);
                        contentStream.newLine();
                        yPosition -= 14;
                    }
                } else {
                    contentStream.showText(sanitizedLine);
                    contentStream.newLine();
                    yPosition -= 14;
                }
            }

            contentStream.endText();
            contentStream.close();

            document.save(baos);
        }

        return baos.toByteArray();
    }

    /**
     * Sanitizes text to ensure compatibility with PDF standard fonts
     */
    private String sanitizeText(String text) {
        if (text == null) return "";

        // Replace special characters that might cause issues
        return text.replaceAll("[^ -~]", " ") // Replace non-ASCII printable chars with spaces
                .replace("→", "->")                  // Replace arrow with ASCII equivalent
                .replace("\t", "    ");              // Replace tabs with spaces
    }
}
