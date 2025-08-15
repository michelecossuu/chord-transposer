package com.michelecossu.chords.transposer.service.impl;

import com.michelecossu.chords.transposer.service.ChordTransposeService;
import com.michelecossu.chords.transposer.web.exception.*;
import com.michelecossu.chords.transposer.web.request.RelativeTransposeRequest;
import com.michelecossu.chords.transposer.web.request.TransposeRequest;
import com.michelecossu.chords.transposer.web.response.RelativeTransposeResponse;
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

    private static final String[] ITALIAN_NOTES = {"DO", "DO#", "RE", "RE#", "MI", "FA", "FA#", "SOL", "SOL#", "LA", "LA#", "SI"};
    private static final String[] ITALIAN_FLAT_NOTES = {"DO", "REb", "RE", "MIb", "MI", "FA", "SOLb", "SOL", "LAb", "LA", "SIb", "SI"};

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

        // Notazione italiana/latina
        NOTE_TO_SEMITONE.put("DO", 0);
        NOTE_TO_SEMITONE.put("DO#", 1);  NOTE_TO_SEMITONE.put("REb", 1);
        NOTE_TO_SEMITONE.put("RE", 2);
        NOTE_TO_SEMITONE.put("RE#", 3);  NOTE_TO_SEMITONE.put("MIb", 3);
        NOTE_TO_SEMITONE.put("MI", 4);
        NOTE_TO_SEMITONE.put("FA", 5);
        NOTE_TO_SEMITONE.put("FA#", 6);  NOTE_TO_SEMITONE.put("SOLb", 6);
        NOTE_TO_SEMITONE.put("SOL", 7);
        NOTE_TO_SEMITONE.put("SOL#", 8); NOTE_TO_SEMITONE.put("LAb", 8);
        NOTE_TO_SEMITONE.put("LA", 9);
        NOTE_TO_SEMITONE.put("LA#", 10); NOTE_TO_SEMITONE.put("SIb", 10);
        NOTE_TO_SEMITONE.put("SI", 11);
    }

    // pattern to ricognize chords (like Cmaj, Dm7, Gsus4, etc)
    private static final Pattern CHORD_PATTERN = Pattern.compile(
            "\\b((?:[A-G][#b]?)|(?:DO|RE|MI|FA|SOL|LA|SI)(?:[#b]?))(" +
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
                    "ø\\d*|" +             // ø (half-diminished)
                    "-" +                  // Trattino per accordi minori italiani (LA-, RE-, MI-)
                    ")?(?:/([A-G][#b]?|DO|RE|MI|FA|SOL|LA|SI)(?:[#b]?))?\\b" // Bass note
    );

    /**
     * Generates a PDF file with transposed chords based on the provided request.
     * This method reads the original file, detects the key, transposes the chords,
     * and generates a new PDF file with the transposed chords.
     *
     * @param request the request containing the original chords and the target key
     * @return a TransposeResponse containing the details of the transposed file
     * @throws ChordTransposeException if an error occurs during the transposition or file generation
     */
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
     * Transpose chords by a specified number of semitones relative to the original chords.
     * This method reads the original file, transposes the chords by the specified number of semitones,
     * and generates a new PDF file with the transposed chords.
     *
     * @param request the request containing the original chords and the number of semitones to transpose
     * @return a RelativeTransposeResponse containing the details of the transposed file
     * @throws ChordTransposeException if an error occurs during the transposition or file generation
     */
    @Override
    public RelativeTransposeResponse transposeChordsBySemitones(RelativeTransposeRequest request) {
        try {
            String originalContent = readFile(request.sourceFileName());

            // Transpose the content by the specified number of semitones
            String transposedContent = transposeContent(originalContent, request.semitones());

            // Generate the output file
            String transposedFileName = generateFileNameWithSemitoneOffset(
                    request.sourceFileName(),
                    request.semitones()
            );

            // Generate and save the PDF
            byte[] pdfBytes = generatePdf(transposedContent);
            Path outputDirectory = Paths.get(filesDirectory);
            Files.createDirectories(outputDirectory);
            Path outputPath = outputDirectory.resolve(transposedFileName);
            Files.write(outputPath, pdfBytes);

            return new RelativeTransposeResponse(
                    request.sourceFileName(),
                    transposedFileName,
                    request.semitones(),
                    true
            );
        } catch (IOException e) {
            throw new ChordTransposeException("Error generating PDF: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new ChordTransposeException("Unexpected error: " + e.getMessage(), e);
        }
    }

    /**
     * Read the content of a file based on its extension.
     * This method supports PDF, DOCX, and TXT files.
     *
     * @param fileName The name of the file to read.
     * @return The content of the file as a string.
     * @throws FileNotFoundException if the file does not exist.
     * @throws ReadFileException if an error occurs while reading the file.
     * @throws SecurityException if the file type is not supported.
     */
    private String readFile(String fileName) throws IOException {
        Path filePath = Paths.get(filesDirectory, fileName);
        if (!Files.exists(filePath)) {
            throw new FileNotFoundException("File non found: " + fileName);
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
        } catch (IOException e) {
            throw new ReadFileException("Error reading file " + fileName + ": " + e.getMessage(), e);
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
            return cleanExtractedPdfText(text);
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
                    text = normalizeWhitespaceForChords(text);
                    content.append(text).append("\n");
                }
            }

            // Extract text from tables if any
            document.getTables().forEach(table ->
                    table.getRows().forEach(row -> {
                        row.getTableCells().forEach(cell -> {
                            String cellText = cell.getText();
                            if (cellText != null && !cellText.trim().isEmpty()) {
                                cellText = normalizeWhitespaceForChords(cellText);
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
     * Normalize whitespace in chords to ensure consistent formatting.
     * This method replaces non-breaking spaces and other whitespace characters
     * with regular spaces, and replaces tabs with four spaces.
     *
     * @param text The text to normalize.
     * @return The normalized text.
     */
    private String normalizeWhitespaceForChords(String text) {
        if (text == null) return "";

        return text
                .replaceAll("[\\u00A0\\u2009\\u200A\\u202F\\u205F\\u2005]", " ")
                .replace("\t", "    ");
    }

    /**
     * Clean up the extracted text from a PDF file.
     * This method removes excessive whitespace, page headers/footers,
     * and common PDF artifacts to produce cleaner text.
     *
     * @param text The extracted text from the PDF.
     * @return The cleaned text.
     */
    private String cleanExtractedPdfText(String text) {
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
     * This method extracts the substring after the last dot in the file name.
     *
     * @param fileName The name of the file.
     * @return The file extension as a string, or an empty string if no extension is found.
     */
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex > 0 ? fileName.substring(lastDotIndex + 1) : "";
    }

    /**
     * Transpose the content of the song by the specified number of semitones.
     * This method finds all chords in the content, transposes them,
     * and returns the modified content with transposed chords.
     *
     * @param content The content of the song as a string.
     * @param semitones The number of semitones to transpose (positive for up, negative for down).
     * @return The transposed content with chords modified according to the specified semitones.
     */
    private String transposeContent(String content, int semitones) {
        boolean useItalianNotation = shouldUseItalianNotation(content);

        StringBuilder result = new StringBuilder();
        Matcher matcher = CHORD_PATTERN.matcher(content);

        while (matcher.find()) {
            String chord = matcher.group();
            String transposedChord = transposeChord(chord, semitones, useItalianNotation);
            matcher.appendReplacement(result, transposedChord);
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Transpose a single chord by the specified number of semitones.
     * This method handles chords in the format: RootNote[Extensions][OptionalBass].
     *
     * @param chord The chord to transpose (e.g., "C", "Dm7", "Gsus4/E").
     * @param semitones The number of semitones to transpose (positive for up, negative for down).
     * @return The transposed chord as a string, or the original chord if it cannot be transposed.
     */
    private String transposeChord(String chord, int semitones, boolean useItalianNotation) {
        Matcher matcher = CHORD_PATTERN.matcher(chord);

        if (!matcher.matches()) {
            return chord;
        }

        String rootNote = matcher.group(1);
        String suffix = matcher.group(2) != null ? matcher.group(2) : "";
        String bassNote = matcher.group(3);

        // Check if the chord is a minor chord with Italian notation (e.g., "SI-")
        boolean isItalianMinor = suffix.equals("-");

        // Transpose the root note
        String newRootNote = transposeNote(rootNote, semitones, useItalianNotation);
        if (newRootNote == null) return chord;

        // Transpose the bass note if present
        String newBassNote = null;
        if (bassNote != null) {
            newBassNote = transposeNote(bassNote, semitones, useItalianNotation);
            if (newBassNote == null) return chord;
        }

        // Rebuild the transposed chord
        StringBuilder result = new StringBuilder(newRootNote);

        // Add the suffix, preserving Italian minor notation if needed
        if (isItalianMinor) {
            result.append("-");
        } else if (!suffix.isEmpty()) {
            result.append(suffix);
        }

        // Add the bass note if present
        if (newBassNote != null) {
            result.append("/").append(newBassNote);
        }

        return result.toString();
    }

    /**
     * Transpose a single note by the specified number of semitones.
     * This method handles both sharps and flats based on the transposition direction.
     *
     * @param note The note to transpose (e.g., "C", "D#", "Bb").
     * @param semitones The number of semitones to transpose (positive for up, negative for down).
     * @return The transposed note as a string, or null if the note is not recognized.
     */
    private String transposeNote(String note, int semitones, boolean useItalianNotation) {
        Integer currentSemitone = NOTE_TO_SEMITONE.get(note);
        if (currentSemitone == null) {
            return null; // Note not recognized
        }

        int newSemitone = (currentSemitone + semitones + 12) % 12;

        if (useItalianNotation) {
            return shouldPreferFlats(semitones) ? ITALIAN_FLAT_NOTES[newSemitone] : ITALIAN_NOTES[newSemitone];
        } else {
            return shouldPreferFlats(semitones) ? FLAT_NOTES[newSemitone] : NOTES[newSemitone];
        }
    }

    /**
     * Determine if flats should be used based on the number of semitones.
     * This is a simplified logic that can be improved based on specific key signatures.
     *
     * @param semitones The number of semitones to transpose.
     * @return true if flats should be used, false if sharps should be used.
     */
    private boolean shouldPreferFlats(int semitones) {
        // Simplify the logic for determining whether to use flats or sharps
        return semitones < 0 || semitones % 12 == 1 || semitones % 12 == 3 ||
                semitones % 12 == 6 || semitones % 12 == 8 || semitones % 12 == 10;
    }

    /**
     * Calculate the number of semitones between two keys.
     * This method assumes that the keys are valid and in the format "C", "Dm", "G#", etc.
     *
     * @param fromKey The original key (e.g., "C", "G", "Dm").
     * @param toKey The target key (e.g., "D", "A", "Em").
     * @return The number of semitones to transpose from the original key to the target key.
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
     * Detect the key of the song based on the frequency of chords in the content.
     * This method analyzes the content to find the most common root note,
     * which is assumed to be the key of the song.
     *
     * @param content The content of the song as a string.
     * @return The detected key as a string (e.g., "C", "G", "Dm").
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
     * The file will be named based on the source file name and the target key.
     *
     * @param request The request containing the source file name and target key.
     * @return The name of the generated file.
     * @throws PDFGenerationException if an error occurs during directory creation or file writing.
     */
    private String generateFile(TransposeRequest request, String transposedContent)
    {
        try {
            String outputFileName = generateFileName(request.sourceFileName(), request.targetKey());

            // Generate PDF with transposed content
            byte[] pdfBytes = generatePdf(transposedContent);

            // Save PDF to local filesystem
            Path outputDirectory = Paths.get(filesDirectory);
            Files.createDirectories(outputDirectory); // Create directories if they don't exist
            Path outputPath = outputDirectory.resolve(outputFileName);
            Files.write(outputPath, pdfBytes);

            return outputFileName;
        } catch (IOException e) {
            throw new PDFGenerationException("Error during directory creation or file writing: " + e.getMessage(), e);
        }
    }

    /**
     * Generate a file name for the transposed file based on the source file name and target key.
     * The generated file name will be in the format: "sourceFileName_targetKey.pdf".
     *
     * @param sourceFileName The name of the source file.
     * @param targetKey The target key for the transposition.
     * @return A generated file name with the target key.
     */
    private String generateFileName(String sourceFileName, String targetKey) {
        String baseName = sourceFileName.substring(0, sourceFileName.lastIndexOf('.'));
        return baseName + "_" + targetKey + ".pdf";
    }

    /**
     * Generate a PDF file with the transposed content.
     * This method creates a PDF document, adds the transposed content line by line,
     * and returns the generated PDF as a byte array.
     *
     * @param content The transposed content to include in the PDF.
     * @return A byte array representing the generated PDF.
     * @throws PDFGenerationException If an error occurs during PDF generation.
     */
    private byte[] generatePdf(String content) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDPageContentStream contentStream = new PDPageContentStream(document, page);

            // Process content line by line
            String[] lines = content.split("\n");
            float yPosition = 750;

            // Extract the first line as title
            String titleText = lines.length > 0 ? sanitizeTextForPdf(lines[0]) : "";

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
                String line = sanitizeTextForPdf(lines[i]);

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
                        xPosition = renderTextWithCustomSpacing(contentStream, textBefore, regularFont, fontSize, xPosition, yPosition, halfSpaceWidth);
                    }

                    // Process chord with bold font
                    String chord = matcher.group();
                    xPosition = renderTextWithCustomSpacing(contentStream, chord, boldFont, fontSize, xPosition, yPosition, halfSpaceWidth);

                    lastEnd = matcher.end();
                }

                // Process remaining text after last chord
                if (lastEnd < line.length()) {
                    String textAfter = line.substring(lastEnd);
                    renderTextWithCustomSpacing(contentStream, textAfter, regularFont, fontSize, xPosition, yPosition, halfSpaceWidth);
                }

                yPosition -= 14; // Move to next line
            }

            contentStream.close();
            document.save(baos);
        } catch (IOException e) {
            throw new PDFGenerationException("Error generating PDF: " + e.getMessage(), e);
        }

        return baos.toByteArray();
    }

    /**
     * Sanitize text for PDF rendering by replacing problematic characters.
     * This method ensures that the text is safe for PDF generation by removing or replacing
     * characters that might cause issues during rendering.
     *
     * @param text The text to sanitize.
     * @return The sanitized text.
     */
    private String sanitizeTextForPdf(String text) {
        if (text == null) return "";

        // Replace special characters that might cause issues
        return text.replaceAll("[^ -~]", " ") // Replace non-ASCII printable chars with spaces
                .replace("→", "->")                  // Replace arrow with ASCII equivalent
                .replace("\t", "    ");              // Replace tabs with spaces
    }

    /**
     * Render text with custom spacing to ensure chords are aligned correctly.
     * This method processes the text character by character to maintain precise spacing.
     *
     * @param contentStream The content stream to write to.
     * @param text The text to render.
     * @param font The font to use for rendering.
     * @param fontSize The size of the font.
     * @param xPosition The initial x position for rendering.
     * @param yPosition The y position for rendering.
     * @param halfSpaceWidth The width of a half-space for chord alignment.
     * @return The updated x position after rendering the text.
     * @throws IOException If an error occurs while writing to the content stream.
     */
    private float renderTextWithCustomSpacing(PDPageContentStream contentStream, String text, PDType1Font font,
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
     * Generate a file name for the transposed file based on the source file name and semitone offset.
     * The generated file name will be in the format: "sourceFileName_semitoneOffset.pdf".
     *
     * @param sourceFileName The name of the source file.
     * @param semitones The number of semitones to transpose.
     * @return A generated file name with semitone offset.
     */
    private String generateFileNameWithSemitoneOffset(String sourceFileName, int semitones) {
        String baseName = sourceFileName.substring(0, sourceFileName.lastIndexOf('.'));
        String semitoneIndicator = semitones >= 0 ? "+" + semitones : String.valueOf(semitones);
        return baseName + "_" + semitoneIndicator + ".pdf";
    }

    private boolean shouldUseItalianNotation(String content) {
        // Conta le occorrenze di notazione italiana vs anglosassone
        Pattern italianPattern = Pattern.compile("\\b(?:DO|RE|MI|FA|SOL?|LA|SI)\\b");
        Pattern anglosaxonPattern = Pattern.compile("\\b[A-G][#b]?(?!-)[a-z]*\\b"); // Escludi note italiane che finiscono con -

        Matcher italianMatcher = italianPattern.matcher(content);
        Matcher anglosaxonMatcher = anglosaxonPattern.matcher(content);

        int italianCount = 0;
        int anglosaxonCount = 0;

        while (italianMatcher.find()) italianCount++;
        while (anglosaxonMatcher.find()) {
            String match = anglosaxonMatcher.group();
            // Verifica che non sia una parola italiana che contiene lettere A-G
            if (isValidAnglosaxonChord(match)) {
                anglosaxonCount++;
            }
        }

        return italianCount > anglosaxonCount;
    }

    private boolean isValidAnglosaxonChord(String text) {
        // Lista di parole italiane comuni che potrebbero contenere A-G ma non sono accordi
        String[] italianWords = {"amar", "servir", "terra", "deserta", "ombra", "ali", "bene"};
        for (String word : italianWords) {
            if (text.toLowerCase().contains(word.toLowerCase())) {
                return false;
            }
        }
        return true;
    }
}
