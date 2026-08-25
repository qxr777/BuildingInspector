package edu.whut.cs.bi.biz.utils;

/**
 * 字符串自然排序工具：连续数字按数值比较，避免 R-10 排在 R-2 前面。
 */
public final class NaturalStringComparator {

    private NaturalStringComparator() {
    }

    /**
     * 按自然升序比较字符串；null 和空字符串排在非空字符串之后。
     */
    public static int compare(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);

        boolean leftEmpty = normalizedLeft.isEmpty();
        boolean rightEmpty = normalizedRight.isEmpty();
        if (leftEmpty || rightEmpty) {
            if (leftEmpty && rightEmpty) {
                return 0;
            }
            return leftEmpty ? 1 : -1;
        }

        int leftIndex = 0;
        int rightIndex = 0;
        while (leftIndex < normalizedLeft.length() && rightIndex < normalizedRight.length()) {
            char leftChar = normalizedLeft.charAt(leftIndex);
            char rightChar = normalizedRight.charAt(rightIndex);

            if (Character.isDigit(leftChar) && Character.isDigit(rightChar)) {
                int leftEnd = findDigitEnd(normalizedLeft, leftIndex);
                int rightEnd = findDigitEnd(normalizedRight, rightIndex);
                int numberComparison = compareNumberParts(
                        normalizedLeft, leftIndex, leftEnd,
                        normalizedRight, rightIndex, rightEnd);
                if (numberComparison != 0) {
                    return numberComparison;
                }
                leftIndex = leftEnd;
                rightIndex = rightEnd;
                continue;
            }

            int charComparison = Character.compare(
                    Character.toUpperCase(leftChar), Character.toUpperCase(rightChar));
            if (charComparison != 0) {
                return charComparison;
            }
            leftIndex++;
            rightIndex++;
        }

        return Integer.compare(normalizedLeft.length(), normalizedRight.length());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static int findDigitEnd(String value, int start) {
        int end = start;
        while (end < value.length() && Character.isDigit(value.charAt(end))) {
            end++;
        }
        return end;
    }

    private static int compareNumberParts(String left, int leftStart, int leftEnd,
                                          String right, int rightStart, int rightEnd) {
        int leftSignificantStart = skipLeadingZeros(left, leftStart, leftEnd);
        int rightSignificantStart = skipLeadingZeros(right, rightStart, rightEnd);
        int leftSignificantLength = leftEnd - leftSignificantStart;
        int rightSignificantLength = rightEnd - rightSignificantStart;

        int lengthComparison = Integer.compare(leftSignificantLength, rightSignificantLength);
        if (lengthComparison != 0) {
            return lengthComparison;
        }

        for (int offset = 0; offset < leftSignificantLength; offset++) {
            int digitComparison = Character.compare(
                    left.charAt(leftSignificantStart + offset),
                    right.charAt(rightSignificantStart + offset));
            if (digitComparison != 0) {
                return digitComparison;
            }
        }

        return Integer.compare(leftEnd - leftStart, rightEnd - rightStart);
    }

    private static int skipLeadingZeros(String value, int start, int end) {
        int index = start;
        while (index < end - 1 && value.charAt(index) == '0') {
            index++;
        }
        return index;
    }
}
