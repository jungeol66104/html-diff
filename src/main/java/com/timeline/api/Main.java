package com.timeline.api;

public class Main {
    public static void main(String[] args) {
        String html1 = "<img src=\"https://via.placeholder.com/150\" alt=\"Placeholder Image 1\"><p>Hello, world!</p><p>This is a test.</p><p>deleted</p>";
        String html2 = "<img src=\"https://via.placeholder.com/150\" alt=\"Placeholder Image 2\"><p>inserted</p><p>Hello, timeline!</p><p>This is a test of the diff tool.</p>";

        CustomHtmlDiff customHtmlDIff = new CustomHtmlDiff(html1, html2);
        String diffHtml = customHtmlDIff.build();
        System.out.println("diffHtml: " + diffHtml);
    }
}
