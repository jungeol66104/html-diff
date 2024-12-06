package com.timeline.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class NewHtmlDiff {
    public static void main(String[] args) {
        String html1 = "<p>Hello, world!</p><p>This is a test.</p><p>deleted</p>";
        String html2 = "<p>inserted</p><p>Hello, timeline!</p><p>This is a test of the diff tool.</p>";

        List<String> tokens1 = tokenize(html1);
        List<String> tokens2 = tokenize(html2);
        System.out.println("Tokenized HTML 1: " + tokens1);
        System.out.println("Tokenized HTML 2: " + tokens2);

        List<Edit> edits = computeMyers(tokens1, tokens2);
        System.out.println("Edits:");
        for (Edit edit : edits) {
            System.out.println("[" + edit.operation + "] " + edit.text);
        }

        String diff = generateDiff(edits);
        System.out.println(diff);

        diff = filterDiff(diff);
        System.out.println(diff);
    }

    public static List<String> tokenize(String html) {
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        boolean insidePTag = false;

        for (int i = 0; i < html.length(); i++) {
            char c = html.charAt(i);

            if (c == '<') {
                int endIndex = html.indexOf('>', i);
                if (endIndex != -1) {
                    String tag = html.substring(i, endIndex + 1);
                    tokens.add(tag);
                    i = endIndex;
                    if (tag.startsWith("<p>")) insidePTag = true;
                    else if (tag.startsWith("</p>")) insidePTag = false;
                }
            } else if (Character.isWhitespace(c)) {
                if (insidePTag && !currentToken.isEmpty()) {
                    tokens.add(currentToken.toString());
                    tokens.add(" ");
                    currentToken.setLength(0);
                } else if (currentToken.isEmpty()) {
                    tokens.add(" ");
                }
            } else {
                if (insidePTag) currentToken.append(c);
            }

            if (!currentToken.isEmpty() && (i == html.length() - 1 || html.charAt(i + 1) == '<')) {
                tokens.add(currentToken.toString());
                currentToken.setLength(0);
            }
        }
        return tokens;
    }

    private static List<Edit> computeMyers(List<String> tokens1, List<String> tokens2) {
        int n = tokens1.size();
        int m = tokens2.size();
        int max = n + m;
        int[] v = new int[2 * max + 1];
        List<int[]> trace = new ArrayList<>();

        for (int d = 0; d <= max; d++) {
            int[] currentV = v.clone();
            trace.add(currentV);

            for (int k = -d; k <= d; k += 2) {
                int x;
                if (k == -d || (k != d && v[max + k - 1] < v[max + k + 1])) x = v[max + k + 1]; // insert
                else x = v[max + k - 1] + 1; // delete
                int y = x - k;

                while (x >= 0 && y >= 0 && x < n && y < m && tokens1.get(x).equals(tokens2.get(y))) {
                    x++;
                    y++;
                }

                // force path
//                int prevXAbove = v[max + k + 1];
//                int prevYAbove = prevXAbove - k - 1;
//                if (prevYAbove >= 0 && prevXAbove >= 0 && prevXAbove < n && prevYAbove < m && tokens2.get(prevYAbove).equals("</p>")) {
//                    if (!tokens1.get(prevXAbove).equals("</p>") && !tokens1.get(prevXAbove).equals("<p>")) {
//                        continue;
//                    }
//                }
//                int prevXBelow = v[max + k - 1];
//                int prevYBelow = prevXBelow - k + 1;
//                if (prevYBelow >= 0 && prevXBelow >= 0 && prevXBelow < n && prevYBelow < m  && tokens1.get(prevXBelow).equals("</p>")) {
//                    if (!tokens2.get(prevYBelow).equals("</p>") && !tokens2.get(prevYBelow).equals("<p>")) {
//                        continue;
//                    }
//                }

                v[max + k] = x;

                if (x >= n && y >= m) {
                    return backtrack(trace, tokens1, tokens2, max, d);
                }
            }
        }
        return null;
    }

    private static List<Edit> backtrack(List<int[]> trace, List<String> tokens1, List<String> tokens2, int max, int d) {
        for (int[] v : trace) {
            System.out.println(Arrays.toString(v));
        }

        List<Edit> edits = new ArrayList<>();
        int x = tokens1.size();
        int y = tokens2.size();

        for (int i = d; i >= 0; i--) {
            int[] currentV = trace.get(i);
            int k = x - y;
            int prevK = (k == -i || (k != i && currentV[max + k - 1] < currentV[max + k + 1])) ? k + 1 : k - 1;

            int prevX = currentV[max + prevK];
            int prevY = prevX - prevK;

            while (x > prevX && y > prevY) {
                edits.add(new Edit(Operation.EQUAL, tokens1.get(--x)));
                y--;
            }

            if (i > 0) {
                if (x > prevX) {
                    edits.add(new Edit(Operation.DELETE, tokens1.get(--x)));
                } else if (y > prevY) {
                    edits.add(new Edit(Operation.INSERT, tokens2.get(--y)));
                }
            }
        }

        Collections.reverse(edits);
        return edits;
    }

    private static String generateDiff(List<Edit> edits) {
        // generate editBundles
        List<List<Edit>> editBundles = new ArrayList<>();
        List<Edit> editBundle = new ArrayList<>();
        boolean insideTag = false;
        for (Edit edit : edits) {
            if (edit.text.equals("<p>")) {
                editBundle.add(edit);
                insideTag = true;
            } else if (edit.text.equals("</p>")) {
                editBundle.add(edit);
                editBundles.add(editBundle);
                editBundle = new ArrayList<>();
                insideTag = false;
            } else if (edit.text.startsWith("<img")) {
                editBundle.add(edit);
                editBundles.add(editBundle);
                editBundle = new ArrayList<>();
            } else {
                if (insideTag) {
                    editBundle.add(edit);
                }
            }
        }

        // generate paragraphs
        List<String> paragraphs = new ArrayList<>();
        for (List<Edit> bundle : editBundles) {
            StringBuilder paragraph = new StringBuilder();
            for (Edit edit : bundle) {
                if (edit.operation == Operation.INSERT) {
                    paragraph.append("<ins>").append(edit.text).append("</ins>");
                } else if (edit.operation == Operation.DELETE) {
                    paragraph.append("<del>").append(edit.text).append("</del>");
                } else {
                    paragraph.append(edit.text);
                }
            }
            paragraphs.add(paragraph.toString());
        }

        // filter and generate result
        StringBuilder result = new StringBuilder();
        for (String paragraph : paragraphs) {
            paragraph = paragraph
                    .replaceAll("</(ins|del)><\\1>", "")
                    .replaceAll("</p></(ins|del)>", "</$1></p>")
                    .replaceAll("<(ins|del)><p>", "<p><$1>");
            result.append(paragraph);
        }
        return result.toString();
    }

    public static String filterDiff(String diff) {
        diff = diff
                .replaceAll("<(ins|del)></\\1></p><p><\\1></\\1>", "</p><p><$1><span></span></$1></p><p>")
                .replaceAll("<(ins|del)></(ins|del)>", "");
        return diff;
    }

    // type
    private enum Operation {
        INSERT, DELETE, EQUAL
    }

    private static class Edit {
        Operation operation;
        String text;

        Edit(Operation operation, String text) {
            this.operation = operation;
            this.text = text;
        }
    }
}

