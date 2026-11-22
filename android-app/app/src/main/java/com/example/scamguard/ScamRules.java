//package com.example.scamguard;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Locale;
//import java.util.regex.Pattern;
//
//public class ScamRules {
//    private static final Pattern URL = Pattern.compile(
//            "(https?://|www\\.|bit\\.ly|tinyurl)\\S*|\\b[\\w-]+\\s?\\.(xyz|top|click|link|buzz|icu)\\b");
//    private static final Pattern URGENT = Pattern.compile(
//            "\\b(urgent|urgently|immediately|act now|last chance|within 24 hours|blocked today|expires today)\\b");
//    private static final Pattern ACCOUNT = Pattern.compile(
//            "kyc|account (will be|has been|is) (blocked|suspended|closed|deactivated)"
//                    + "|(verify|update|confirm) your (account|pan|aadhaar|kyc|card)"
//                    + "|lose access|sim (will be )?(blocked|deactivated)");
//    private static final Pattern CODE_REQUEST = Pattern.compile(
//            "\\b(share|send|give|tell|reply|provide|enter|submit)\\b.{0,25}\\b(otp|pin|cvv|password)\\b");
//    private static final Pattern PRIZE = Pattern.compile(
//            "\\byou('ve| have)? won\\b|\\b(winner|lottery|lucky draw|prize)\\b");
//    private static final Pattern CALL = Pattern.compile(
//            "\\b(call|dial|whatsapp)\\b.{0,25}\\+?\\d[\\d\\s-]{8,}");
//    // Real OTP messages say "do not share your OTP". Remove that sentence before checking.
//    private static final Pattern WARNING = Pattern.compile(
//            "(do not|don't|dont|never)\\s+(share|disclose|give|tell)[^.]*");
//
//    /** Returns warning signs. Never log the text itself. */
//    public static List<String> signals(String text) {
//        List<String> s = new ArrayList<>();
//        if (text == null) return s;
//        String t = text.toLowerCase(Locale.ROOT);
//        String noWarn = WARNING.matcher(t).replaceAll(" ");
//
//        if (URGENT.matcher(t).find()) s.add("urgent wording");
//        if (URL.matcher(t).find()) s.add("link present");
//        if (ACCOUNT.matcher(t).find()) s.add("account threat");
//        if (CODE_REQUEST.matcher(noWarn).find()) s.add("asks for secret code");
//        if (PRIZE.matcher(t).find()) s.add("prize claim");
//        if (CALL.matcher(t).find()) s.add("asks you to call a number");
//        return s;
//    }
//
//    /** Scam if the model says so, or if two or more different warning signs are present. */
//    public static boolean isScam(float spamScore, float threshold, List<String> signals) {
//        return spamScore >= threshold || signals.size() >= 2;
//    }
//
//    /** Confidence shown to the user: the higher of the model score and the rule score. */
//    public static float confidence(float spamScore, List<String> signals) {
//        if (signals.isEmpty()) return spamScore;
//        float ruleScore = Math.min(0.95f, 0.45f + 0.15f * signals.size());
//        return Math.max(spamScore, ruleScore);
//    }
//    /** Names the kind of scam from the warning signs. */
//    public static String typeOf(List<String> s) {
//        if (s.contains("asks for secret code")) return "OTP / secret code theft";
//        if (s.contains("account threat")) return "Fake bank / KYC warning";
//        if (s.contains("prize claim")) return "Prize or lottery scam";
//        if (s.contains("asks you to call a number")) return "Phone call scam";
//        if (s.contains("link present")) return "Phishing link";
//        if (s.contains("urgent wording")) return "Pressure / urgency scam";
//        return "Suspicious message (AI model)";
//    }
//
//    /** Advice that matches the warning signs. */
//    public static String actions(List<String> s) {
//        StringBuilder sb = new StringBuilder();
//        int n = 1;
//        sb.append(n++).append(". Do not reply to this message.\n");
//        if (s.contains("link present"))
//            sb.append(n++).append(". Do not open the link. It can steal your logins or install a virus.\n");
//        if (s.contains("asks for secret code") || s.contains("account threat"))
//            sb.append(n++).append(". Never share an OTP, PIN, CVV or password. Your bank never asks for them by message.\n");
//        if (s.contains("asks you to call a number"))
//            sb.append(n++).append(". Do not call the number in the message. Use the number on your bank card or official website.\n");
//        if (s.contains("prize claim"))
//            sb.append(n++).append(". Real prizes never ask you to pay or share details first.\n");
//        sb.append(n++).append(". Block the sender and report the message.\n");
//        sb.append(n).append(". If you already clicked or shared something, call your bank and change your passwords.");
//        return sb.toString();
//    }
//}

package com.example.scamguard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class ScamRules {
    private static final Pattern URL = Pattern.compile(
            "(https?://|www\\.|bit\\.ly|tinyurl)\\S*|\\b[\\w-]+\\s?\\.(xyz|top|click|link|buzz|icu)\\b");
    private static final Pattern URGENT = Pattern.compile(
            "\\b(urgent|urgently|immediately|act now|last chance|within 24 hours|blocked today|expires today)\\b");
    private static final Pattern ACCOUNT = Pattern.compile(
            "kyc|account (will be|has been|is) (blocked|suspended|closed|deactivated)"
                    + "|(verify|update|confirm) your (account|pan|aadhaar|kyc|card)"
                    + "|lose access|sim (will be )?(blocked|deactivated)");
    private static final Pattern CODE_REQUEST = Pattern.compile(
            "\\b(share|send|give|tell|reply|provide|enter|submit)\\b.{0,25}\\b(otp|pin|cvv|password)\\b");
    private static final Pattern PRIZE = Pattern.compile(
            "\\byou('ve| have)? won\\b|\\b(winner|lottery|lucky draw|prize)\\b");
    private static final Pattern CALL = Pattern.compile(
            "\\b(call|dial|whatsapp)\\b.{0,25}\\+?\\d[\\d\\s-]{8,}");
    // Real OTP messages say "do not share your OTP". Remove that sentence before checking.
    private static final Pattern WARNING = Pattern.compile(
            "(do not|don't|dont|never)\\s+(share|disclose|give|tell)[^.]*");

    /** Returns warning signs. Never log the text itself. */
    public static List<String> signals(String text) {
        List<String> s = new ArrayList<>();
        if (text == null) return s;
        String t = text.toLowerCase(Locale.ROOT);
        String noWarn = WARNING.matcher(t).replaceAll(" ");

        if (URGENT.matcher(t).find()) s.add("urgent wording");
        if (URL.matcher(t).find()) s.add("link present");
        if (ACCOUNT.matcher(t).find()) s.add("account threat");
        if (CODE_REQUEST.matcher(noWarn).find()) s.add("asks for secret code");
        if (PRIZE.matcher(t).find()) s.add("prize claim");
        if (CALL.matcher(t).find()) s.add("asks you to call a number");
        return s;
    }

    /** Scam if the model says so, or if two or more different warning signs are present. */
    public static boolean isScam(float spamScore, float threshold, List<String> signals) {
        return spamScore >= threshold || signals.size() >= 2;
    }

    /** Confidence shown to the user: the higher of the model score and the rule score. */
    public static float confidence(float spamScore, List<String> signals) {
        if (signals.isEmpty()) return spamScore;
        float ruleScore = Math.min(0.95f, 0.45f + 0.15f * signals.size());
        return Math.max(spamScore, ruleScore);
    }

    /** Names the kind of scam from the warning signs. */
    public static String typeOf(List<String> s) {
        if (s.contains("asks for secret code")) return "OTP / secret code theft";
        if (s.contains("account threat")) return "Fake bank / KYC warning";
        if (s.contains("prize claim")) return "Prize or lottery scam";
        if (s.contains("asks you to call a number")) return "Phone call scam";
        if (s.contains("link present")) return "Phishing link";
        if (s.contains("urgent wording")) return "Pressure / urgency scam";
        return "Suspicious message (AI model)";
    }

    /** Fixed advice that matches the warning signs. Used as a backup when the AI text is missing. */
    public static String actions(List<String> s) {
        StringBuilder sb = new StringBuilder();
        int n = 1;
        sb.append(n++).append(". Do not reply to this message.\n");
        if (s.contains("link present"))
            sb.append(n++).append(". Do not open the link. It can steal your logins or install a virus.\n");
        if (s.contains("asks for secret code") || s.contains("account threat"))
            sb.append(n++).append(". Never share an OTP, PIN, CVV or password. Your bank never asks for them by message.\n");
        if (s.contains("asks you to call a number"))
            sb.append(n++).append(". Do not call the number in the message. Use the number on your bank card or official website.\n");
        if (s.contains("prize claim"))
            sb.append(n++).append(". Real prizes never ask you to pay or share details first.\n");
        sb.append(n++).append(". Block the sender and report the message.\n");
        sb.append(n).append(". If you already clicked or shared something, call your bank and change your passwords.");
        return sb.toString();
    }

    /** Fixed explanation used when the AI text is missing or refused. */
    public static String fallbackWhy(List<String> s) {
        return "This message looks like a \"" + typeOf(s) + "\". "
                + "Scammers use fake urgency, links or prizes to trick you into sharing money, codes or logins. "
                + "Do not reply or open any link, and block the sender.";
    }
}