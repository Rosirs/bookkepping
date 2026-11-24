package cn.ryan.bookkeeper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

final class SpendingInsights {
    static final Set<String> DEFAULT_FOOD=Collections.unmodifiableSet(new HashSet<>(Arrays.asList("餐饮","食品","食品烟酒","买菜","生鲜","零食","饮品","水果","外卖","早餐","午餐","晚餐","烟酒")));
    final String engel,surplus;
    final long averageExpense;
    SpendingInsights(long income,long expense,Map<String,Long> categories,Set<String> food,int expenseCount) {
        long foodExpense=0;for(String name:food)foodExpense=Math.addExact(foodExpense,categories.getOrDefault(name,0L));
        engel=percent(foodExpense,expense);surplus=percent(income-expense,income);
        averageExpense=expenseCount>0?expense/expenseCount:0;
    }
    private static String percent(long amount,long total) {
        return total<=0?"暂无":BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(total),1,RoundingMode.HALF_UP).toPlainString()+"%";
    }
}
