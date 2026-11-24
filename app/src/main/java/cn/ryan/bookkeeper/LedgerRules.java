package cn.ryan.bookkeeper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Shared input boundary for UI, backups and future integrations. Money is integer fen. */
public final class LedgerRules {
    public static final List<String> TYPES = java.util.Collections.unmodifiableList(java.util.Arrays.asList("expense", "income", "invest", "redeem"));
    public static long parseAmount(String text) {
        if (!text.matches("[0-9]{1,10}(\\.[0-9]{1,2})?")) throw new IllegalArgumentException("金额最多两位小数");
        long fen = new BigDecimal(text).movePointRight(2).longValueExact();
        if (fen <= 0 || fen > 999999999999L) throw new IllegalArgumentException("请输入有效的正数金额");
        return fen;
    }
    public static void validate(String type, long fen, String date, String category, String note) {
        if (!TYPES.contains(type)) throw new IllegalArgumentException("未知账目类型");
        if (fen <= 0 || fen > 999999999999L) throw new IllegalArgumentException("金额超出范围");
        if (!LocalDate.parse(date).toString().equals(date)) throw new IllegalArgumentException("日期格式应为 YYYY-MM-DD");
        if (category.trim().isEmpty() || category.length() > 40) throw new IllegalArgumentException("分类应为 1–40 个字符");
        if (note.length() > 500) throw new IllegalArgumentException("备注最多 500 个字符");
    }
    public static String month(String value) {
        try { return YearMonth.parse(value).toString(); }
        catch (java.time.DateTimeException e) { throw new IllegalArgumentException("月份格式应为 YYYY-MM",e); }
    }
    public static String money(long fen) { return BigDecimal.valueOf(fen, 2).toPlainString(); }
    private LedgerRules() {}
}
