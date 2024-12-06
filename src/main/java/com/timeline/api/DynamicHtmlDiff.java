package com.timeline.api;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DynamicHtmlDiff {
    public static void main(String[] args) {
        String html1 = "<p>Hello, world!</p><p>This is a test.</p><p>deleted</p>";
        String html2 = "<p>inserted</p><p>Hello, timeline!</p><p>This is a test of the diff tool.</p>";

        List<String> tokens1 = tokenize(html1);
        List<String> tokens2 = tokenize(html2);

        System.out.println("Tokenized HTML 1: " + tokens1);
        System.out.println("Tokenized HTML 2: " + tokens2);

        List<String> lcs = computeLCS(tokens1, tokens2);
        System.out.println("Computed LCS: " + lcs);

        List<String> diffResult = generateDiff(tokens1, tokens2, lcs);
        System.out.println("Unified Diff Result:");
        for (String token : diffResult) {
            System.out.print(token);
        }

        List<String> filteredDiffResult = filterDiff(diffResult);
        System.out.println("\nFiltered Diff Result:");
        for (String token : filteredDiffResult) {
            System.out.print(token);
        }
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
                    if (tag.startsWith("<p>")) {
                        insidePTag = true;
                    } else if (tag.startsWith("</p>")) {
                        insidePTag = false;
                    }
                }
            } else if (c == '>') {
                continue;
            } else if (Character.isWhitespace(c)) {
                if (insidePTag && currentToken.length() > 0) {
                    tokens.add(currentToken.toString());
                    tokens.add(" ");
                    currentToken.setLength(0);
                }
            } else {
                if (insidePTag) {
                    currentToken.append(c);
                }
            }

            if (currentToken.length() > 0 && (i == html.length() - 1 || html.charAt(i + 1) == '<')) {
                tokens.add(currentToken.toString());
                currentToken.setLength(0);
            }
        }

        return tokens;
    }

    public static List<String> computeLCS(List<String> X, List<String> Y) {
        int m = X.size();
        int n = Y.size();
        int[][] L = new int[m + 1][n + 1];

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (X.get(i - 1).equals(Y.get(j - 1))) {
                    L[i][j] = L[i - 1][j - 1] + 1;
                } else {
                    L[i][j] = Math.max(L[i - 1][j], L[i][j - 1]);
                }
            }
        }

        List<String> lcs = new ArrayList<>();
        int i = m, j = n;
        while (i > 0 && j > 0) {
            if (X.get(i - 1).equals(Y.get(j - 1))) {
                lcs.add(0, X.get(i - 1));
                i--;
                j--;
            } else if (L[i - 1][j] > L[i][j - 1]) {
                i--;
            } else {
                j--;
            }
        }
        return lcs;
    }

    public static List<String> generateDiff(List<String> tokens1, List<String> tokens2, List<String> lcs) {
        List<String> diff = new ArrayList<>();
        int i = 0, j = 0;

        // tokens1: [<p>, Hello,, world!, </p>, <p>, This, is, a, test., </p>, <p>, deleted, </p>] (size: 13)
        // tokens2: [<p>, inserted, </p>, <p>, Hello,, OpenAI!, </p>, <p>, This, is, a, test, of, the, diff, tool., </p>] (size: 17)
        // lcs: [<p>, Hello,, </p>, <p>, This, is, a, </p>]
        for (String token : lcs) {
            while (i < tokens1.size() && !tokens1.get(i).equals(token)) {
                diff.add("<del>" + tokens1.get(i) + "</del>");
                i++;
            }
            while (j < tokens2.size() && !tokens2.get(j).equals(token)) {
                diff.add("<ins>" + tokens2.get(j) + "</ins>");
                j++;
            }
            diff.add(token);
            i++;
            j++;
        }
        while (i < tokens1.size()) {
            diff.add("<del>" + tokens1.get(i) + "</del>");
            i++;
        }
        while (j < tokens2.size()) {
            diff.add("<ins>" + tokens2.get(j) + "</ins>");
            j++;
        }
        return diff;
    }

    public static List<String> filterDiff(List<String> diff) {
        List<String> filteredDiff = new ArrayList<>();

        for (String token : diff) {
            String patternString = "(?:<ins>|<del>)(.*?)(?:</ins>|</del>)";
            Pattern pattern = Pattern.compile(patternString);
            Matcher matcher = pattern.matcher(token);

            if (matcher.find()) {
                filteredDiff.add(matcher.group(1));
            } else {
                filteredDiff.add(token);
            }
        }

        return filteredDiff;
    }
}
