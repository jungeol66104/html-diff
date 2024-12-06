package com.timeline.api;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Collectors;

public class CustomHtmlDiff {
    private String html1;
    private String html2;
    private String[] tokens1;
    private String[] tokens2;
    private List<Operation> operations;
    private StringBuilder diffHtml;

    // options
    private int granularity;
    private final int granularityThreshold = 4;
    private final double OrphanMatchThreshold = 0.0;
    private final double repeatingWordsAccuracy = 1d;
    private final boolean ignoreWhitespaceDifferences = false;

    // main constructor
    public CustomHtmlDiff(String html1, String html2) {
        this.html1 = html1;
        this.html2 = html2;
    }

    // main function
    public String build() {
        if (html1.equals(html2)) return html2;

        tokenize();
        calculateGranularity();
        createOperations();
        performOperations();

        return diffHtml.toString();
    }

    private void tokenize() {
        Tokenizer tokenizer1 = new Tokenizer();
        tokens1 = tokenizer1.tokenize(html1);
        html1 = null;

        Tokenizer tokenizer2 = new Tokenizer();
        tokens2 = tokenizer2.tokenize(html2);
        html2 = null;
    }

    private class Tokenizer {
        Mode mode = Mode.CHARACTER;
        List<Character> currentToken = new ArrayList<>();
        List<String> tokens = new ArrayList<>();

        private String[] tokenize(String html) {
            for (var i = 0; i < html.length(); i++)
            {
                var character = html.charAt(i);
                processCharacter(character);
            }
            appendCurrentWordToWords();
            return tokens.toArray(new String[0]);
        }

        public void processCharacter(char character) {
            switch (mode) {
                case CHARACTER:
                    processTextCharacter(character);
                    break;
                case TAG:
                    processHtmlTagContinuation(character);
                    break;
                case WHITESPACE:
                    processWhiteSpaceContinuation(character);
                    break;
                case ENTITY:
                    processEntityContinuation(character);
                    break;
            }
        }

        private void processTextCharacter(char character) {
            if (isStartOfTag(character)) {
                appendCurrentWordToWords();
                currentToken.add('<');
                mode = Mode.TAG;
            } else if (isStartOfEntity(character)) {
                appendCurrentWordToWords();
                currentToken.add(character);
                mode = Mode.ENTITY;
            } else if (Character.isWhitespace(character)) {
                appendCurrentWordToWords();
                currentToken.add(character);
                mode = Mode.WHITESPACE;
            } else if (isWord(character) && (currentToken.isEmpty() || isWord(currentToken.get(currentToken.size() - 1)))) {
                currentToken.add(character);
            } else {
                appendCurrentWordToWords();
                currentToken.add(character);
            }
        }

        private void processEntityContinuation(char character) {
            if (isStartOfTag(character)) {
                appendCurrentWordToWords();
                currentToken.add(character);
                mode = Mode.TAG;
            } else if (Character.isWhitespace(character)) {
                appendCurrentWordToWords();
                currentToken.add(character);
                mode = Mode.WHITESPACE;
            } else if (isEndOfEntity(character)) {
                boolean switchToNextMode = true;
                if (!currentToken.isEmpty()) {
                    currentToken.add(character);
                    tokens.add(listToString(currentToken));

                    if (tokens.size() > 2 && isWhiteSpace(tokens.get(tokens.size() - 2)) && isWhiteSpace(tokens.get(tokens.size() - 1))) {
                        String w1 = tokens.get(tokens.size() - 2);
                        String w2 = tokens.get(tokens.size() - 1);
                        tokens.subList(tokens.size() - 2, tokens.size()).clear();
                        currentToken.clear();
                        currentToken.addAll(stringToList(w1));
                        currentToken.addAll(stringToList(w2));
                        mode = Mode.WHITESPACE;
                        switchToNextMode = false;
                    }
                }

                if (switchToNextMode) {
                    currentToken.clear();
                    mode = Mode.CHARACTER;
                }
            } else if (isWord(character)) {
                currentToken.add(character);
            } else {
                appendCurrentWordToWords();
                currentToken.add(character);
                mode = Mode.CHARACTER;
            }
        }

        private void processWhiteSpaceContinuation(char character) {
            if (isStartOfTag(character)) {
                appendCurrentWordToWords();
                currentToken.add(character);
                mode = Mode.TAG;
            } else if (isStartOfEntity(character)) {
                appendCurrentWordToWords();
                currentToken.add(character);
                mode = Mode.ENTITY;
            } else if (Character.isWhitespace(character)) {
                currentToken.add(character);
            } else {
                appendCurrentWordToWords();
                currentToken.add(character);
                mode = Mode.CHARACTER;
            }
        }

        private void processHtmlTagContinuation(char character) {
            if (isEndOfTag(character)) {
                currentToken.add(character);
                appendCurrentWordToWords();
                mode = isWhiteSpace(character) ? Mode.WHITESPACE : Mode.CHARACTER;
            } else {
                currentToken.add(character);
            }
        }

        private void appendCurrentWordToWords() {
            if (isCurrentWordHasChars()) {
                StringBuilder word = new StringBuilder(currentToken.size());
                currentToken.forEach(word::append);
                tokens.add(word.toString());
                currentToken.clear();
            }
        }

        private boolean isCurrentWordHasChars() {
            return currentToken != null && !currentToken.isEmpty();
        }
    }

    private void calculateGranularity() {
        granularity = Math.min(granularityThreshold, Math.min(tokens1.length, tokens2.length));
    }

    private void createOperations() {
        OperationCreator operationCreator = new OperationCreator(tokens1, tokens2);
        operations = operationCreator.create();
    }

    private class OperationCreator {
        private String[] tokens1;
        private String[] tokens2;
        private List<Operation> operations = new ArrayList<>();
        private List<Match> matches = new ArrayList<>();

        int positionInOld = 0;
        int positionInNew = 0;

        public OperationCreator(String[] tokens1, String[] tokens2) {
            this.tokens1 = tokens1;
            this.tokens2 = tokens2;
        }

        public List<Operation> create() {
            findMatches(0, tokens1.length, 0, tokens2.length);
            matches.add(new Match(tokens1.length, tokens2.length, 0));
            removeOrphans();
            createOperations();

            return operations;
        }

        private void findMatches(int startInOld, int endInOld, int startInNew, int endInNew) {
            Match match = findMatch(startInOld, endInOld, startInNew, endInNew);

            if (match != null) {
                if (startInOld < match.getStartInOld() && startInNew < match.getStartInNew()) {
                    findMatches(startInOld, match.getStartInOld(), startInNew, match.getStartInNew());
                }

                matches.add(match);

                if (match.getEndInOld() < endInOld && match.getEndInNew() < endInNew) {
                    findMatches(match.getEndInOld(), endInOld, match.getEndInNew(), endInNew);
                }
            }
        }

        private Match findMatch(int startInOld, int endInOld, int startInNew, int endInNew) {
            for (int i = granularity; i > 0; i--) {
                MatchOptions options = new MatchOptions(i, repeatingWordsAccuracy, ignoreWhitespaceDifferences);
                MatchFinder finder = new MatchFinder(tokens1, tokens2, startInOld, endInOld, startInNew, endInNew, options);
                Match match = finder.findMatch();
                if (match != null) {
                    return match;
                }
            }
            return null;
        }

        public void removeOrphans() {
            Match prev = null;
            Match curr = null;
            Iterator<Match> iterator = matches.iterator();
            List<Match> filteredMatches = new ArrayList<>();

            while (iterator.hasNext()) {
                Match next = iterator.next();

                if (curr == null) {
                    prev = new Match(0, 0, 0);
                    curr = next;
                    continue;
                }

                if ((prev.getEndInOld() == curr.startInOld && prev.getEndInNew() == curr.startInNew) ||
                        (curr.getEndInOld() == next.startInOld && curr.getEndInNew() == next.startInNew)) {
                    filteredMatches.add(curr);
                    prev = curr;
                    curr = next;
                    continue;
                }

                int oldDistanceInChars = calculateDistance(prev.getEndInOld(), next.startInOld, tokens1);
                int newDistanceInChars = calculateDistance(prev.getEndInNew(), next.startInNew, tokens2);
                int currMatchLengthInChars = calculateMatchLength(curr.startInNew, curr.getEndInNew(), tokens2);

                if (currMatchLengthInChars > Math.max(oldDistanceInChars, newDistanceInChars) * OrphanMatchThreshold) {
                    filteredMatches.add(curr);
                }

                prev = curr;
                curr = next;
            }

            filteredMatches.add(curr);
            matches = filteredMatches;
        }

        private int calculateDistance(int start, int end, String[] words) {
            int distance = 0;
            for (int i = start; i < end; i++) {
                distance += words[i].length();
            }
            return distance;
        }

        private int calculateMatchLength(int start, int end, String[] words) {
            int length = 0;
            for (int i = start; i < end; i++) {
                length += words[i].length();
            }
            return length;
        }

        private void createOperations() {
            for (Match match : matches) {
                boolean matchStartsAtCurrentPositionInOld = (positionInOld == match.getStartInOld());
                boolean matchStartsAtCurrentPositionInNew = (positionInNew == match.getStartInNew());
                Action action;

                if (!matchStartsAtCurrentPositionInOld && !matchStartsAtCurrentPositionInNew) action = Action.REPLACE;
                else if (matchStartsAtCurrentPositionInOld && !matchStartsAtCurrentPositionInNew) action = Action.INSERT;
                else if (!matchStartsAtCurrentPositionInOld) action = Action.DELETE;
                else action = Action.NONE;

                if (action != Action.NONE) {
                    operations.add(new Operation(action, positionInOld, match.getStartInOld(), positionInNew, match.getStartInNew()));
                }
                if (match.getSize() != 0) {
                    operations.add(new Operation(Action.EQUAL, match.getStartInOld(), match.getEndInOld(), match.getStartInNew(), match.getEndInNew()));
                }

                positionInOld = match.getEndInOld();
                positionInNew = match.getEndInNew();
            }
        }
    }

    private class MatchFinder {
        private final String[] tokens1;
        private final String[] tokens2;
        private final int startInOld;
        private final int endInOld;
        private final int startInNew;
        private final int endInNew;
        private Map<String, List<Integer>> tokenIndices;
        private final MatchOptions options;

        public MatchFinder(String[] tokens1, String[] tokens2, int startInOld, int endInOld, int startInNew, int endInNew, MatchOptions options) {
            this.tokens1 = tokens1;
            this.tokens2 = tokens2;
            this.startInOld = startInOld;
            this.endInOld = endInOld;
            this.startInNew = startInNew;
            this.endInNew = endInNew;
            this.options = options;
        }

        public Match findMatch() {
            indexNewWords();
            removeRepeatingWords();

            if (tokenIndices.isEmpty()) return null;

            int bestMatchInOld = startInOld;
            int bestMatchInNew = startInNew;
            int bestMatchSize = 0;

            Map<Integer, Integer> matchLengthAt = new HashMap<>();
            Queue<String> block = new LinkedList<>();

            for (int indexInOld = startInOld; indexInOld < endInOld; indexInOld++) {
                String word = normalizeForIndex(tokens1[indexInOld]);
                String index = putNewWord(block, word, options.getBlockSize());

                if (index == null)
                    continue;

                Map<Integer, Integer> newMatchLengthAt = new HashMap<>();

                if (!tokenIndices.containsKey(index)) {
                    matchLengthAt = newMatchLengthAt;
                    continue;
                }

                for (int indexInNew : tokenIndices.get(index)) {
                    int newMatchLength = (matchLengthAt.containsKey(indexInNew - 1) ? matchLengthAt.get(indexInNew - 1): 0) + 1;
                    newMatchLengthAt.put(indexInNew, newMatchLength);

                    if (newMatchLength > bestMatchSize) {
                        bestMatchInOld = indexInOld - newMatchLength + 1 - options.getBlockSize() + 1;
                        bestMatchInNew = indexInNew - newMatchLength + 1 - options.getBlockSize() + 1;
                        bestMatchSize = newMatchLength;
                    }
                }

                matchLengthAt = newMatchLengthAt;
            }

            return (bestMatchSize != 0) ? new Match(bestMatchInOld, bestMatchInNew, bestMatchSize + options.getBlockSize() - 1) : null;
        }

        private void indexNewWords() {
            tokenIndices = new HashMap<>();
            Queue<String> block = new LinkedList<>();
            for (int i = startInNew; i < endInNew; i++) {
                String word = normalizeForIndex(tokens2[i]);
                String key = putNewWord(block, word, options.getBlockSize());

                if (key == null) continue;

                List<Integer> indices = tokenIndices.getOrDefault(key, new ArrayList<>());
                indices.add(i);
                tokenIndices.put(key, indices);
            }
        }

        private static String putNewWord(Queue<String> block, String word, int blockSize) {
            block.offer(word);
            if (block.size() > blockSize) block.poll();
            if (block.size() != blockSize) return null;

            StringBuilder result = new StringBuilder(blockSize);
            for (String s : block) {
                result.append(s);
            }
            return result.toString();
        }

        private String normalizeForIndex(String token) {
            token = stripAnyAttributes(token);
            if (options.isIgnoreWhitespaceDifferences() && isWhiteSpace(token)) return " ";

            return token;
        }

        public void removeRepeatingWords() {
            double threshold = tokens2.length * repeatingWordsAccuracy;

            String[] repeatingWords = tokenIndices.entrySet().stream()
                    .filter(entry -> entry.getValue().size() > threshold)
                    .map(Map.Entry::getKey)
                    .toArray(String[]::new);

            for (String word : repeatingWords) {tokenIndices.remove(word);}
        }
    }

    private void performOperations() {
        OperationPerformer operationPerformer = new OperationPerformer(operations);
        diffHtml = operationPerformer.perform();
    }

    private class OperationPerformer {
        private List<Operation> operations;
        private StringBuilder diffHtml = new StringBuilder();

        private static final String InsTag = "ins";
        private static final String DelTag = "del";
        private static final Map<String, Integer> SpecialCaseClosingTags =  Map.ofEntries(Map.entry("</strong>", 0), Map.entry("</em>", 0), Map.entry("</b>", 0), Map.entry("</i>", 0), Map.entry("</big>", 0), Map.entry("</small>", 0), Map.entry("</u>", 0), Map.entry("</sub>", 0), Map.entry("</sup>", 0), Map.entry("</strike>", 0), Map.entry("</s>", 0), Map.entry("</span>", 0));
        private static final Pattern SpecialCaseOpeningTagRegex = Pattern.compile("<((strong)|(b)|(i)|(em)|(big)|(small)|(u)|(sub)|(sup)|(strike)|(s)|(span))[>\\s]+", Pattern.CASE_INSENSITIVE);
        private final Deque<String> SpecialTagDiffStack = new ArrayDeque<>();

        public OperationPerformer(List<Operation> operations) {
            this.operations = operations;
        }

        private StringBuilder perform() {
            for (Operation operation : operations) {
                switch (operation.action)
                {
                    case EQUAL:
                        processEqualOperation(operation);
                        break;
                    case DELETE:
                        processDeleteOperation(operation, "diffdel");
                        break;
                    case INSERT:
                        processInsertOperation(operation, "diffins");
                        break;
                    case NONE:
                        break;
                    case REPLACE:
                        processReplaceOperation(operation);
                        break;
                }
            }

            return diffHtml;
        }

        private void processReplaceOperation(Operation operation) {
            processDeleteOperation(operation, "diffmod");
            processInsertOperation(operation, "diffmod");
        }

        private void processInsertOperation(Operation operation, String cssClass) {
            List<String> text = IntStream.range(0, tokens2.length)
                    .filter(pos -> pos >= operation.getStartInNew() && pos < operation.getEndInNew())
                    .mapToObj(pos -> tokens2[pos])
                    .collect(Collectors.toList());
            insertTag(InsTag, cssClass, text);
        }

        private void processDeleteOperation(Operation operation, String cssClass) {
            List<String> text = IntStream.range(0, tokens1.length)
                    .filter(pos -> pos >= operation.getStartInOld() && pos < operation.getEndInOld())
                    .mapToObj(pos -> tokens1[pos])
                    .collect(Collectors.toList());
            insertTag(DelTag, cssClass, text);
        }

        private void processEqualOperation(Operation operation) {
            String result = IntStream.range(0, tokens2.length)
                    .filter(pos -> pos >= operation.getStartInNew() && pos < operation.getEndInNew())
                    .mapToObj(pos -> tokens2[pos])
                    .collect(Collectors.joining());
            diffHtml.append(result);
        }

        private void insertTag(String tag, String cssClass, List<String> words) {
            while (true) {
                if (words.isEmpty()) { break; }

                String[] nonTags = extractConsecutiveWords(words, x -> !isTag(x));
                String specialCaseTagInjection = "";
                boolean specialCaseTagInjectionIsBefore = false;

                if (nonTags.length != 0) {
                    String text = wrapText(String.join("", nonTags), tag, cssClass);
                    diffHtml.append(text);
                } else {
                    if (SpecialCaseOpeningTagRegex.matcher(words.get(0)).matches()) {
                        SpecialTagDiffStack.push(words.get(0));
                        specialCaseTagInjection = "<ins class='mod'>";
                        if (tag.equals(DelTag)) {
                            words.remove(0);

                            while (!words.isEmpty() && SpecialCaseOpeningTagRegex.matcher(words.get(0)).matches()) {
                                words.remove(0);
                            }
                        }
                    } else if (SpecialCaseClosingTags.containsKey(words.get(0))) {
                        String openingTag = SpecialTagDiffStack.isEmpty() ? null : SpecialTagDiffStack.pop();
                        boolean hasOpeningTag = openingTag != null;
                        boolean openingAndClosingTagsMatch = getTagName(openingTag).equals(getTagName(words.get(words.size() - 1)));
                        if (hasOpeningTag && openingAndClosingTagsMatch) {
                            specialCaseTagInjection = "</ins>";
                            specialCaseTagInjectionIsBefore = true;
                        }

                        if (tag.equals(DelTag)) {
                            words.remove(0);
                            while (!words.isEmpty() && SpecialCaseClosingTags.containsKey(words.get(0))) {words.remove(0);}
                        }
                    }
                }

                if (words.isEmpty() && specialCaseTagInjection.isEmpty()) {
                    break;
                }

                if (specialCaseTagInjectionIsBefore) {
                    diffHtml.append(specialCaseTagInjection).append(String.join("", extractConsecutiveWords(words, CustomHtmlDiff::isTag)));
                } else {
                    diffHtml.append(String.join("", extractConsecutiveWords(words, CustomHtmlDiff::isTag))).append(specialCaseTagInjection);
                }
            }
        }

        private String[] extractConsecutiveWords(List<String> words, java.util.function.Predicate<String> condition) {
            Integer indexOfFirstTag = null;

            for (int i = 0; i < words.size(); i++) {
                String word = words.get(i);

                if (i == 0 && word.equals(" ")) {
                    words.set(i, "&nbsp;");
                }

                if (!condition.test(word)) {
                    indexOfFirstTag = i;
                    break;
                }
            }

            if (indexOfFirstTag != null) {
                String[] items = IntStream.range(0, indexOfFirstTag)
                        .mapToObj(words::get)
                        .toArray(String[]::new);
                if (indexOfFirstTag > 0) {
                    words.subList(0, indexOfFirstTag).clear();
                }
                return items;
            } else {
                String[] items = words.toArray(new String[0]);
                words.clear();
                return items;
            }
        }
    }

    // utils
    private static final Pattern openingTagRegex = Pattern.compile("^\\s*<[^>]+>\\s*$");
    private static final Pattern closingTagTexRegex = Pattern.compile("^\\s*</[^>]+>\\s*$");
    private static final Pattern tagWordRegex = Pattern.compile("<[^\\s>]+");
    private static final Pattern whitespaceRegex = Pattern.compile("^(\\s|&nbsp;)+$");
    private static final Pattern wordRegex = Pattern.compile("[\\w#@]+");
    private static final Pattern tagRegex = Pattern.compile("</?(?<name>[^\\s/>]+)[^>]*>");
    private static final String[] specialCaseWordTags = { "<img" };

    public static boolean isTag(String item) {
        if (item != null && Arrays.stream(specialCaseWordTags).anyMatch(re -> item.startsWith(re))) {
            return false;
        }
        return isOpeningTag(item) || isClosingTag(item);
    }
    private static boolean isOpeningTag(String item) {
        return item != null && openingTagRegex.matcher(item).matches();
    }
    private static boolean isClosingTag(String item) {
        return item != null && closingTagTexRegex.matcher(item).matches();
    }
    public static boolean isStartOfTag(char val) {
        return val == '<';
    }
    public static boolean isEndOfTag(char val) {
        return val == '>';
    }
    public static boolean isStartOfEntity(char val) {
        return val == '&';
    }
    public static boolean isEndOfEntity(char val) {
        return val == ';';
    }
    public static boolean isWhiteSpace(String value) {
        return whitespaceRegex.matcher(value).matches();
    }
    public static boolean isWhiteSpace(char value) {
        return Character.isWhitespace(value);
    }
    public static boolean isWord(char text) {
        return wordRegex.matcher(String.valueOf(text)).matches();
    }

    public static String stripTagAttributes(String word) {
        Matcher matcher = tagWordRegex.matcher(word);
        if (matcher.find()) {
            String tag = matcher.group();
            word = tag + (word.endsWith("/>") ? "/>" : ">");
        }
        return word;
    }
    public static String stripAnyAttributes(String word) {
        if (isTag(word)) {
            return stripTagAttributes(word);
        }
        return word;
    }

    public static String wrapText(String text, String tagName, String cssClass) {
        return String.format("<%s class='%s'>%s</%s>", tagName, cssClass, text, tagName);
    }
    public static String getTagName(String word) {
        if (word == null) {
            return "";
        }
        Matcher matcher = tagRegex.matcher(word);
        return matcher.find() ? matcher.group("name").toLowerCase() : "";
    }

    private String listToString(List<Character> list) {
        StringBuilder sb = new StringBuilder(list.size());
        for (Character ch : list) {
            sb.append(ch);
        }
        return sb.toString();
    }
    private List<Character> stringToList(String str) {
        List<Character> list = new ArrayList<>();
        for (char ch : str.toCharArray()) {
            list.add(ch);
        }
        return list;
    }

    // types
    public static class Operation {
        private Action action;
        private int startInOld;
        private int endInOld;
        private int startInNew;
        private int endInNew;

        public Operation(Action action, int startInOld, int endInOld, int startInNew, int endInNew) {
            this.action = action;
            this.startInOld = startInOld;
            this.endInOld = endInOld;
            this.startInNew = startInNew;
            this.endInNew = endInNew;
        }

        public Action getAction() {
            return action;
        }
        public void setAction(Action action) {
            this.action = action;
        }
        public int getStartInOld() {
            return startInOld;
        }
        public void setStartInOld(int startInOld) {
            this.startInOld = startInOld;
        }
        public int getEndInOld() {
            return endInOld;
        }
        public void setEndInOld(int endInOld) {
            this.endInOld = endInOld;
        }
        public int getStartInNew() {
            return startInNew;
        }
        public void setStartInNew(int startInNew) {
            this.startInNew = startInNew;
        }
        public int getEndInNew() {
            return endInNew;
        }
        public void setEndInNew(int endInNew) {
            this.endInNew = endInNew;
        }
    }

    public static class Match {
        private final int startInOld;
        private final int startInNew;
        private final int size;

        public Match(int startInOld, int startInNew, int size) {
            this.startInOld = startInOld;
            this.startInNew = startInNew;
            this.size = size;
        }

        public int getStartInOld() {return startInOld;}
        public int getStartInNew() {return startInNew;}
        public int getSize() {return size;}
        public int getEndInOld() {return startInOld + size;}
        public int getEndInNew() {return startInNew + size;}
    }

    public static class MatchOptions {
        private int blockSize;
        private double repeatingWordsAccuracy;
        private boolean ignoreWhitespaceDifferences;

        public MatchOptions(int blockSize, double repeatingWordsAccuracy, boolean ignoreWhitespaceDifferences) {
            this.blockSize = blockSize;
            this.repeatingWordsAccuracy = repeatingWordsAccuracy;
            this.ignoreWhitespaceDifferences = ignoreWhitespaceDifferences;
        }

        public int getBlockSize() {
            return blockSize;
        }
        public void setBlockSize(int blockSize) {
            this.blockSize = blockSize;
        }
        public double getRepeatingWordsAccuracy() {return repeatingWordsAccuracy;}
        public void setRepeatingWordsAccuracy(double repeatingWordsAccuracy) {this.repeatingWordsAccuracy = repeatingWordsAccuracy;}
        public boolean isIgnoreWhitespaceDifferences() {
            return ignoreWhitespaceDifferences;
        }
        public void setIgnoreWhitespaceDifferences(boolean ignoreWhitespaceDifferences) {this.ignoreWhitespaceDifferences = ignoreWhitespaceDifferences;}
    }

    public enum Action {
        INSERT, DELETE, EQUAL, NONE, REPLACE
    }

    public enum Mode {
        CHARACTER, TAG, WHITESPACE, ENTITY,
    }

}
