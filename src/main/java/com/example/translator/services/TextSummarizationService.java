package com.example.translator.services;

import android.content.Context;
import android.util.Log;
import java.util.*;
import java.util.stream.Collectors;

public class TextSummarizationService {

    private static final String TAG = "TextSummarizationService";
    private static final long SUMMARIZATION_TIMEOUT = 30000L; // 30 seconds
    private static final int MAX_TEXT_LENGTH = 10000;
    private static final int MIN_TEXT_LENGTH = 100;

    private TranslationService translationService;

    public enum SummaryType {
        BRIEF,          // 1-2 sentences
        DETAILED,       // 3-5 sentences
        BULLET_POINTS,  // Key points as bullets
        KEY_PHRASES     // Important terms
    }

    public static class SummaryResult {
        public static class Success extends SummaryResult {
            public final String summary;
            public final SummaryType type;

            public Success(String summary, SummaryType type) {
                this.summary = summary;
                this.type = type;
            }
        }

        public static class Error extends SummaryResult {
            public final String message;

            public Error(String message) {
                this.message = message;
            }
        }
    }

    public interface SummarizationCallback {
        void onSuccess(SummaryResult.Success result);
        void onFailure(SummaryResult.Error error);
    }

    public TextSummarizationService(Context context) {
        this.translationService = new TranslationService(context);
    }

    public void summarizeText(String text, SummaryType summaryType, String targetLanguage,
                              SummarizationCallback callback) {
        if (!isValidInput(text)) {
            callback.onFailure(new SummaryResult.Error("Text is too short or too long for summarization"));
            return;
        }

        try {
            String summary;
            switch (summaryType) {
                case BRIEF:
                    summary = createBriefSummary(text);
                    break;
                case DETAILED:
                    summary = createDetailedSummary(text);
                    break;
                case BULLET_POINTS:
                    summary = createBulletPointSummary(text);
                    break;
                case KEY_PHRASES:
                    summary = extractKeyPhrases(text);
                    break;
                default:
                    summary = createBriefSummary(text);
            }

            // Translate summary if needed
            if (!"en".equals(targetLanguage)) {
                translationService.translateText(summary, "en", targetLanguage,
                        new TranslationService.TranslationCallback() {
                            @Override
                            public void onSuccess(String translatedText) {
                                callback.onSuccess(new SummaryResult.Success(translatedText, summaryType));
                            }

                            @Override
                            public void onFailure(Exception exception) {
                                // Use original summary if translation fails
                                callback.onSuccess(new SummaryResult.Success(summary, summaryType));
                            }
                        });
            } else {
                callback.onSuccess(new SummaryResult.Success(summary, summaryType));
            }

        } catch (Exception e) {
            Log.e(TAG, "Summarization failed", e);
            callback.onFailure(new SummaryResult.Error("Failed to summarize text: " + e.getMessage()));
        }
    }

    private boolean isValidInput(String text) {
        if (text == null) return false;
        String cleanText = text.trim();
        return cleanText.length() >= MIN_TEXT_LENGTH && cleanText.length() <= MAX_TEXT_LENGTH;
    }

    private String createBriefSummary(String text) {
        List<String> sentences = splitIntoSentences(text);
        List<String> importantSentences = extractImportantSentences(sentences, 2);
        return String.join(" ", importantSentences);
    }

    private String createDetailedSummary(String text) {
        List<String> sentences = splitIntoSentences(text);
        List<String> importantSentences = extractImportantSentences(sentences, 5);
        return String.join(" ", importantSentences);
    }

    private String createBulletPointSummary(String text) {
        List<String> sentences = splitIntoSentences(text);
        List<String> keyPoints = extractImportantSentences(sentences, 4);

        StringBuilder result = new StringBuilder();
        for (String sentence : keyPoints) {
            result.append("• ").append(sentence.trim()).append("\n");
        }

        return result.toString().trim();
    }

    private String extractKeyPhrases(String text) {
        String[] words = text.toLowerCase()
                .replaceAll("[^a-zA-Z\\s]", "")
                .split("\\s+");

        Map<String, Integer> wordFrequency = new HashMap<>();
        for (String word : words) {
            if (word.length() > 3) {
                wordFrequency.put(word, wordFrequency.getOrDefault(word, 0) + 1);
            }
        }

        List<String> topWords = wordFrequency.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(8)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        return "Key terms: " + String.join(", ", topWords);
    }

    private List<String> splitIntoSentences(String text) {
        String[] sentences = text.split("[.!?]+");
        List<String> result = new ArrayList<>();

        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (!trimmed.isEmpty() && trimmed.length() > 10) {
                result.add(trimmed);
            }
        }

        return result;
    }

    private List<String> extractImportantSentences(List<String> sentences, int maxSentences) {
        if (sentences.size() <= maxSentences) {
            return sentences;
        }

        List<ScoredSentence> scoredSentences = new ArrayList<>();

        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);

            // Simple scoring based on sentence length and position
            double positionScore;
            if (i == 0) {
                positionScore = 3.0; // First sentence is important
            } else if (i == sentences.size() - 1) {
                positionScore = 2.0; // Last sentence
            } else if (i < sentences.size() * 0.3) {
                positionScore = 1.5; // Early sentences
            } else {
                positionScore = 1.0;
            }

            double lengthScore;
            if (sentence.length() < 50) {
                lengthScore = 0.5; // Too short
            } else if (sentence.length() > 200) {
                lengthScore = 0.7; // Too long
            } else {
                lengthScore = 1.0; // Good length
            }

            double keywordScore = countKeywords(sentence);
            double totalScore = positionScore * lengthScore * (1 + keywordScore * 0.1);

            scoredSentences.add(new ScoredSentence(sentence, totalScore, i));
        }

        // Sort by score and take top sentences
        List<ScoredSentence> topSentences = scoredSentences.stream()
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .limit(maxSentences)
                .collect(Collectors.toList());

        // Sort back by original order to maintain flow
        return topSentences.stream()
                .sorted(Comparator.comparingInt(s -> s.originalIndex))
                .map(s -> s.sentence)
                .collect(Collectors.toList());
    }

    private int countKeywords(String sentence) {
        String[] keywords = {
                "important", "significant", "key", "main", "primary", "essential",
                "critical", "major", "fundamental", "crucial", "vital", "notable",
                "first", "second", "third", "finally", "conclusion", "result",
                "because", "therefore", "however", "although", "moreover"
        };

        String lowerSentence = sentence.toLowerCase();
        int count = 0;
        for (String keyword : keywords) {
            if (lowerSentence.contains(keyword)) {
                count++;
            }
        }
        return count;
    }

    public void close() {
        if (translationService != null) {
            translationService.closeTranslators();
        }
    }

    private static class ScoredSentence {
        final String sentence;
        final double score;
        final int originalIndex;

        ScoredSentence(String sentence, double score, int originalIndex) {
            this.sentence = sentence;
            this.score = score;
            this.originalIndex = originalIndex;
        }
    }
}