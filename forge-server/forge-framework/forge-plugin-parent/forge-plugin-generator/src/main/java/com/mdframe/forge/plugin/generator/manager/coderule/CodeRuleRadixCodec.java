package com.mdframe.forge.plugin.generator.manager.coderule;

import com.mdframe.forge.starter.core.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 编码流水号固定宽度进制转换器。
 */
@Component
public class CodeRuleRadixCodec {

    public static final String ALL_AMBIGUOUS_CHARACTERS = "I,O,Z";

    private static final BigInteger LONG_MAX_VALUE = BigInteger.valueOf(Long.MAX_VALUE);
    private static final List<String> AMBIGUOUS_CHARACTER_ORDER = List.of("I", "O", "Z");
    private static final Set<String> AMBIGUOUS_CHARACTER_SET = Set.copyOf(AMBIGUOUS_CHARACTER_ORDER);

    private static final Map<String, String> ALPHABETS = Map.of(
            "DECIMAL", "0123456789",
            "HEX", "0123456789ABCDEF",
            "ALPHA_UPPER", "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
            "ALPHA_LOWER", "abcdefghijklmnopqrstuvwxyz",
            "ALPHANUMERIC", "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    );

    public String encode(long value, String radixType, int length, boolean excludeAmbiguous) {
        return encode(value, radixType, length,
                excludeAmbiguous ? ALL_AMBIGUOUS_CHARACTERS : "");
    }

    public String encode(long value, String radixType, int length, String excludedCharacters) {
        if (value < 0) {
            throw new BusinessException("流水号不能小于0");
        }
        if (length < 1 || length > 32) {
            throw new BusinessException("流水号长度必须在1到32之间");
        }
        String alphabet = alphabet(radixType, excludedCharacters);
        BigInteger capacity = capacity(radixType, length, excludedCharacters);
        if (BigInteger.valueOf(value).compareTo(capacity) >= 0) {
            throw new BusinessException("流水号已超过当前进制和长度的容量，请增加流水号长度");
        }
        StringBuilder encoded = new StringBuilder();
        long remaining = value;
        do {
            int index = (int) (remaining % alphabet.length());
            encoded.append(alphabet.charAt(index));
            remaining /= alphabet.length();
        } while (remaining > 0);
        encoded.reverse();
        return StringUtils.leftPad(encoded.toString(), length, alphabet.charAt(0));
    }

    public int requiredLength(long value, String radixType, boolean excludeAmbiguous) {
        return requiredLength(value, radixType,
                excludeAmbiguous ? ALL_AMBIGUOUS_CHARACTERS : "");
    }

    public int requiredLength(long value, String radixType, String excludedCharacters) {
        if (value < 0) {
            throw new BusinessException("流水号不能小于0");
        }
        BigInteger remaining = BigInteger.valueOf(value);
        BigInteger radix = BigInteger.valueOf(alphabet(radixType, excludedCharacters).length());
        int length = 1;
        while (remaining.compareTo(radix) >= 0) {
            remaining = remaining.divide(radix);
            length++;
        }
        return length;
    }

    public long maxValue(String radixType, int length, boolean excludeAmbiguous) {
        return maxValue(radixType, length,
                excludeAmbiguous ? ALL_AMBIGUOUS_CHARACTERS : "");
    }

    public long maxValue(String radixType, int length, String excludedCharacters) {
        BigInteger maximum = capacity(radixType, length, excludedCharacters).subtract(BigInteger.ONE);
        return maximum.min(LONG_MAX_VALUE).longValue();
    }

    public int recommendedAllocationStep(String radixType,
                                         int length,
                                         boolean excludeAmbiguous) {
        return recommendedAllocationStep(radixType, length,
                excludeAmbiguous ? ALL_AMBIGUOUS_CHARACTERS : "");
    }

    public int recommendedAllocationStep(String radixType,
                                         int length,
                                         String excludedCharacters) {
        BigInteger suggested = capacity(radixType, length, excludedCharacters)
                .divide(BigInteger.valueOf(1_000L));
        return suggested.max(BigInteger.ONE)
                .min(BigInteger.valueOf(1_000L))
                .intValue();
    }

    public String alphabet(String radixType, boolean excludeAmbiguous) {
        return alphabet(radixType, excludeAmbiguous ? ALL_AMBIGUOUS_CHARACTERS : "");
    }

    public String alphabet(String radixType, String excludedCharacters) {
        String normalized = StringUtils.defaultIfBlank(radixType, "DECIMAL").toUpperCase(Locale.ROOT);
        String alphabet = ALPHABETS.get(normalized);
        if (alphabet == null) {
            throw new BusinessException("不支持的流水号进制: " + radixType);
        }
        String canonical = normalizeExcludedCharacters(excludedCharacters, false);
        if ("DECIMAL".equals(normalized) || "HEX".equals(normalized)) {
            return alphabet;
        }
        for (String character : canonical.split(",")) {
            if (StringUtils.isBlank(character)) {
                continue;
            }
            alphabet = alphabet.replace(character, "")
                    .replace(character.toLowerCase(Locale.ROOT), "");
        }
        return alphabet;
    }

    /**
     * 将用户选择规范为固定顺序的 I/O/Z 集合；旧总开关只在新字段为空时兜底为全选。
     */
    public static String normalizeExcludedCharacters(String excludedCharacters, boolean legacyExcludeAll) {
        if (StringUtils.isBlank(excludedCharacters)) {
            return legacyExcludeAll ? ALL_AMBIGUOUS_CHARACTERS : "";
        }
        String compact = excludedCharacters.toUpperCase(Locale.ROOT)
                .replace(",", "")
                .replaceAll("\\s+", "");
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (int index = 0; index < compact.length(); index++) {
            String character = String.valueOf(compact.charAt(index));
            if (!AMBIGUOUS_CHARACTER_SET.contains(character)) {
                throw new BusinessException("易混淆字符仅支持 I、O、Z");
            }
            selected.add(character);
        }
        return AMBIGUOUS_CHARACTER_ORDER.stream()
                .filter(selected::contains)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private BigInteger capacity(String radixType, int length, String excludedCharacters) {
        if (length < 1 || length > 32) {
            throw new BusinessException("流水号长度必须在1到32之间");
        }
        return BigInteger.valueOf(alphabet(radixType, excludedCharacters).length()).pow(length);
    }
}
