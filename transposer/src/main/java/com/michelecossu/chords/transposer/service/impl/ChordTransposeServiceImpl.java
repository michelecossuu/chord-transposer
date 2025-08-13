package com.michelecossu.chords.transposer.service.impl;

import com.michelecossu.chords.transposer.service.ChordTransposeService;
import com.michelecossu.chords.transposer.web.exception.ChordTransposeException;
import com.michelecossu.chords.transposer.web.exception.TargetKeyException;
import com.michelecossu.chords.transposer.web.request.RelativeTransposeRequest;
import com.michelecossu.chords.transposer.web.request.TransposeRequest;
import com.michelecossu.chords.transposer.web.response.TransposeResponse;
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
import java.nio.charset.StandardCharsets;
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

    @Override
    public TransposeResponse generatePdfWithTransposedChords(TransposeRequest request) {
        try {
            String originalContent = readFile(request.sourceFileName());

            String originalKey = detectKey(originalContent);

            int semitones;
            if (request.targetKey() != null && !request.targetKey().isEmpty()) {
                semitones = calculateSemitones(originalKey, request.targetKey());
            } else {
                throw new TargetKeyException("Target key is null or empty");
            }

            String transposedContent = transposeContent(originalContent, semitones);

            String transposedFileName = generateFile(request, transposedContent);

            return new TransposeResponse(
                    request.sourceFileName(),
                    transposedFileName,
                    originalKey,
                    request.targetKey(),
                    true
            );
        } catch (IOException e) {
            throw new ChordTransposeException("Error generating PDF: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new ChordTransposeException("Invalid key provided: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new ChordTransposeException("Unexpected error generating PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Read a file and return its content as a string.
     * Supports PDF, DOCX, and plain text.
     *
     * @param fileName The name of the file to read.
     * @return The content of the file as a string.
     * @throws IOException If the file does not exist or cannot be read.
     */
    private String readFile(String fileName) throws IOException {
        Path filePath = Paths.get(filesDirectory, fileName);
        if (!Files.exists(filePath)) {
            throw new IOException("File non found: " + fileName);
        }

        String extension = getFileExtension(fileName).toLowerCase();

        try {
            return switch (extension) {
                case "pdf" -> readPDFFile(filePath);
                case "docx" -> readDOCXFile(filePath);
                case "txt" -> Files.readString(filePath, StandardCharsets.UTF_8);
                default -> {
                    if (!isAllowedExtension(extension)) {
                        throw new SecurityException("Unsupported file type: " + extension);
                    }
                    yield Files.readString(filePath, StandardCharsets.UTF_8);
                }
            };
        } catch (Exception e) {
            throw new IOException("Error reading file " + fileName + ": " + e.getMessage(), e);
        }
    }

    /**
     * Check if the file extension is allowed for processing.
     *
     * @param extension The file extension to check.
     * @return true if the extension is allowed, false otherwise.
     */
    private boolean isAllowedExtension(String extension) {
        return List.of("pdf", "docx", "txt").contains(extension.toLowerCase());
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
                    text = normalizeSpacingForChords(text);
                    content.append(text).append("\n");
                }
            }

            // Extract text from tables if any
            document.getTables().forEach(table ->
                    table.getRows().forEach(row -> {
                        row.getTableCells().forEach(cell -> {
                            String cellText = cell.getText();
                            if (cellText != null && !cellText.trim().isEmpty()) {
                                cellText = normalizeSpacingForChords(cellText);
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
     * Converts standard spaces to half-width spaces to maintain exact chord positioning
     *
     * @param text The text to process
     * @return Text with half-width spaces
     */
    private String normalizeSpacingForChords(String text) {
        if (text == null) return "";

        return text
                .replaceAll("[\\u00A0\\u2009\\u200A\\u202F\\u205F\\u2005]", " ")
                .replace("\t", "    ");
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
    private String transposeContent(String content, int semitones) {
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
    private String transposeChord(String chord, int semitones) {
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
    private int calculateSemitones(String fromKey, String toKey) {
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
    private String detectKey(String content) {
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
     * Generate a file with the transposed content and save it to the local filesystem.
     *
     * @param request The request containing the original chords
     * @return A byte array representing the generated file
     */
    private String generateFile(TransposeRequest request, String transposedContent) throws IOException {
        String outputFileName = generateFileName(request.sourceFileName(), request.targetKey());

        // Generate PDF with transposed content
        byte[] pdfBytes = generatePdf(transposedContent);

        // Save PDF to local filesystem
        Path outputDirectory = Paths.get(filesDirectory);
        Files.createDirectories(outputDirectory); // Create directories if they don't exist
        Path outputPath = outputDirectory.resolve(outputFileName);
        Files.write(outputPath, pdfBytes);

        return outputFileName;
    }

    /**
     * Generate a file name for the transposed file based on the source file name and target key.
     * The generated file name will be in the format: "sourceFileName_targetKey.pdf".
     *
     * @param sourceFileName The name of the source file.
     * @param targetKey The target key for the transposition.
     * @return A generated
     */
    private String generateFileName(String sourceFileName, String targetKey) {
        String baseName = sourceFileName.substring(0, sourceFileName.lastIndexOf('.'));
        return baseName + "_" + targetKey + ".pdf";
    }

    /**
     * Generate a PDF file with the transposed content.
     *
     * @param content The transposed content to be included in the PDF.
     * @return A byte array representing the generated PDF file.
     * @throws IOException If an error occurs while generating the PDF.
     */
    private byte[] generatePdf(String content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDPageContentStream contentStream = new PDPageContentStream(document, page);

            // Process content line by line
            String[] lines = content.split("\n");
            float yPosition = 750;

            // Extract the first line as title
            String titleText = lines.length > 0 ? sanitizeText(lines[0]) : "";

            // Set title
            contentStream.beginText();
            contentStream.setFont(PDType1Font.HELVETICA_BOLD, 14);
            contentStream.newLineAtOffset(50, yPosition);
            contentStream.showText(titleText);
            contentStream.endText();

            yPosition -= 30; // Add space after title

            // Font settings
            PDType1Font regularFont = PDType1Font.COURIER;
            PDType1Font boldFont = PDType1Font.COURIER_BOLD;
            float fontSize = 11;
            float standardSpaceWidth = regularFont.getSpaceWidth() * fontSize / 1000;
            float halfSpaceWidth = standardSpaceWidth / 2; // Half-width space for chord alignment

            for (int i = 1; i < lines.length; i++) {
                String line = sanitizeText(lines[i]);

                // Check if we need a new page
                if (yPosition < 50) {
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    contentStream.close();
                    contentStream = new PDPageContentStream(document, page);
                    yPosition = 750;
                }

                // Process line character by character with precise positioning
                float xPosition = 50; // Starting x position

                // Find all chord matches in the line
                Matcher matcher = CHORD_PATTERN.matcher(line);
                int lastEnd = 0;

                while (matcher.find()) {
                    // Process text before chord
                    if (matcher.start() > lastEnd) {
                        String textBefore = line.substring(lastEnd, matcher.start());
                        xPosition = renderText(contentStream, textBefore, regularFont, fontSize, xPosition, yPosition, halfSpaceWidth);
                    }

                    // Process chord with bold font
                    String chord = matcher.group();
                    xPosition = renderText(contentStream, chord, boldFont, fontSize, xPosition, yPosition, halfSpaceWidth);

                    lastEnd = matcher.end();
                }

                // Process remaining text after last chord
                if (lastEnd < line.length()) {
                    String textAfter = line.substring(lastEnd);
                    renderText(contentStream, textAfter, regularFont, fontSize, xPosition, yPosition, halfSpaceWidth);
                }

                yPosition -= 14; // Move to next line
            }

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

    @Override
    public TransposeResponse transposeChordsBySemitones(RelativeTransposeRequest request) {
        try {
            String originalContent = readFile(request.sourceFileName());

            // Transpose the content by the specified number of semitones
            String transposedContent = transposeContentBySemitones(originalContent, request.semitones());

            // Generate the output file
            String transposedFileName = generateFileNameByAddingSemitonesAdded(
                    request.sourceFileName(),
                    request.semitones()
            );

            // Generate and save the PDF
            byte[] pdfBytes = generatePdf(transposedContent);
            Path outputDirectory = Paths.get(filesDirectory);
            Files.createDirectories(outputDirectory);
            Path outputPath = outputDirectory.resolve(transposedFileName);
            Files.write(outputPath, pdfBytes);

            return new TransposeResponse(
                    request.sourceFileName(),
                    transposedFileName,
                    null,
                    request.semitones().toString(),
                    true
            );
        } catch (IOException e) {
            throw new ChordTransposeException("Error generating PDF: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new ChordTransposeException("Unexpected error: " + e.getMessage(), e);
        }
    }

    private String transposeContentBySemitones(String content, Integer semitones) {
        StringBuilder result = new StringBuilder();
        Matcher matcher = CHORD_PATTERN.matcher(content);

        while (matcher.find()) {
            String chord = matcher.group();
            String transposedChord = transposeChordBySemitone(chord, semitones);
            matcher.appendReplacement(result, transposedChord);
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private String transposeChordBySemitone(String chord, Integer semitones) {
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
     * Renders text with specified font, handling spaces with half-width
     *
     * @return the new x position after rendering
     */
    private float renderText(PDPageContentStream contentStream, String text, PDType1Font font,
                             float fontSize, float xPosition, float yPosition, float halfSpaceWidth)
            throws IOException {

        if (text.isEmpty()) return xPosition;

        // Process character by character for precise spacing
        float currentX = xPosition;
        StringBuilder textBuffer = new StringBuilder();

        for (int j = 0; j < text.length(); j++) {
            char c = text.charAt(j);

            if (c == ' ') {
                // Output any accumulated text
                if (!textBuffer.isEmpty()) {
                    contentStream.beginText();
                    contentStream.setFont(font, fontSize);
                    contentStream.newLineAtOffset(currentX, yPosition);
                    contentStream.showText(textBuffer.toString());
                    contentStream.endText();

                    // Update position based on text width
                    currentX += font.getStringWidth(textBuffer.toString()) * fontSize / 1000;
                    textBuffer.setLength(0);
                }

                // Add half-width space
                currentX += halfSpaceWidth;
            } else {
                textBuffer.append(c);
            }
        }

        // Output any remaining text
        if (!textBuffer.isEmpty()) {
            contentStream.beginText();
            contentStream.setFont(font, fontSize);
            contentStream.newLineAtOffset(currentX, yPosition);
            contentStream.showText(textBuffer.toString());
            contentStream.endText();

            currentX += font.getStringWidth(textBuffer.toString()) * fontSize / 1000;
        }

        return currentX;
    }

    /**
     * Generate a file name for the transposed file based on the source file name, target key and semitones.
     */
    private String generateFileNameByAddingSemitonesAdded(String sourceFileName, int semitones) {
        String baseName = sourceFileName.substring(0, sourceFileName.lastIndexOf('.'));
        String semitoneIndicator = semitones >= 0 ? "+" + semitones : String.valueOf(semitones);
        return baseName + "_" + semitoneIndicator + ".pdf";
    }
}
